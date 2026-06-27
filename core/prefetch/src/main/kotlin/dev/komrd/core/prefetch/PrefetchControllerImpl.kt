package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.epub.EpubRepository
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.Server
import dev.komrd.core.model.isEpub
import dev.komrd.core.prefetch.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.pow

@Suppress("TooManyFunctions", "LongParameterList") // ワーカー制御+EPUB分岐の責務集約。分割は可読性を搕くため見送り。
class PrefetchControllerImpl(
    @ApplicationScope private val scope: CoroutineScope,
    private val pageFetcher: PageFetcher,
    private val store: PrefetchStore,
    private val networkPolicy: NetworkPolicy = NoOpNetworkPolicy,
    private val evictor: PrefetchEvictor = NoOpPrefetchEvictor,
    private val evictionConfigFlow: Flow<PrefetchEvictionConfig> =
        kotlinx.coroutines.flow.flowOf(PrefetchEvictionConfig()),
    private val parallelismFlow: Flow<Int> = kotlinx.coroutines.flow.flowOf(PrefetchController.DEFAULT_PARALLELISM),
    private val backoff: BackoffConfig = BackoffConfig(),
    @ApplicationScope private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val epubRepository: EpubRepository? = null,
    private val resourceFetcher: ResourceFetcher? = null,
) : PrefetchController {
    private val _state = MutableStateFlow<PrefetchState>(PrefetchState.Idle)
    override val state: StateFlow<PrefetchState> = _state.asStateFlow()

    override val boundServerId: String? get() = server?.id
    override val boundBookId: String? get() = currentBook?.id

    private val pausedState = MutableStateFlow(false)
    private val remaining = AtomicInteger(0)
    private val total = AtomicInteger(0)

    private val workers = mutableListOf<Job>()
    private var policyJob: Job? = null
    private var enqueueJob: Job? = null
    private var prefetchChannel: Channel<PrefetchTarget> = Channel(Channel.UNLIMITED)
    private var demandChannel: Channel<PrefetchTarget> = Channel(Channel.UNLIMITED)

    private var server: Server? = null
    private var currentBook: BookDetail? = null
    private var evictionConfig: PrefetchEvictionConfig = PrefetchEvictionConfig()
    private var nextBook: NextBook? = null
    private var currentPageNumber: Int = 0

    // 次冊(NextBook)の媒体プロファイルは未携帯のため現Book基準の近似とする(クロス媒体Seriesは稀)。
    private var mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE

    // 画像系取得時はnull。manifest取得失敗時はnullでEPUB prefetchをスキップ(表示は継続)。
    private var epubManifest: EpubManifest? = null

    override suspend fun start(
        server: Server,
        currentBook: BookDetail,
        currentPageNumber: Int,
        nextBook: NextBook?,
    ) {
        stop()
        this.server = server
        this.currentBook = currentBook
        this.nextBook = nextBook
        this.currentPageNumber = currentPageNumber
        this.mediaProfile = currentBook.mediaProfile
        this.epubManifest = null
        // 並列数・破棄予算を設定Flowから最新取得（M4: Singleton化してもセッション開始時に設定変更を反映）。
        val parallelism = parallelismFlow.first().coerceAtLeast(1)
        evictionConfig = evictionConfigFlow.first()
        prefetchChannel = Channel(Channel.UNLIMITED)
        demandChannel = Channel(Channel.UNLIMITED)
        repeat(parallelism) { workers += scope.launch { workerLoop() } }
        policyJob =
            scope.launch {
                networkPolicy.decisions.collect { decision ->
                    when (decision) {
                        NetworkDecision.Run -> resume()
                        is NetworkDecision.Pause -> pause()
                    }
                }
            }
        scope.launch { evictCurrentWindow() }
        // EPUB媒体はmanifest取得が必要(追加ネットワーク)。失敗時は非致命(prefetch無しで表示続行)。
        if (mediaProfile.isEpub && epubRepository != null) {
            val manifest = withContext(dispatcher) { epubRepository.loadManifest(server, currentBook.id) }
            if (manifest is KomgaResult.Success) this.epubManifest = manifest.value
        }
        enqueueWindow(currentPageNumber)
    }

    override fun onPageChanged(currentPageNumber: Int) {
        if (currentBook == null) return
        this.currentPageNumber = currentPageNumber
        drainPrefetch()
        enqueueJob?.cancel()
        enqueueJob = scope.launch { enqueueWindow(currentPageNumber) }
    }

    override fun demand(
        bookId: String,
        pageNumber: Int,
    ) {
        val target = demandTarget(bookId, pageNumber) ?: return
        demandChannel.trySend(target).getOrThrow()
    }

    /** 画像系はpageNumber→PAGE target。EPUBは章インデックス→HTML target(manifest要)。 */
    private fun demandTarget(
        bookId: String,
        pageNumber: Int,
    ): PrefetchTarget? =
        if (mediaProfile.isEpub) {
            val manifest = epubManifest ?: return null
            val chapter = manifest.readingOrder.getOrNull(pageNumber) ?: return null
            PrefetchTarget(bookId, chapter.href, PrefetchStore.RESOURCE_KIND_HTML, PrefetchStore.VARIANT_FULL)
        } else {
            PrefetchTarget(
                bookId,
                pageNumber.toString(),
                PrefetchStore.RESOURCE_KIND_PAGE,
                variantFor(mediaProfile),
                pageNumber,
            )
        }

    override fun pause() {
        pausedState.value = true
    }

    override fun resume() {
        pausedState.value = false
    }

    override fun stop() {
        workers.forEach { it.cancel() }
        workers.clear()
        policyJob?.cancel()
        policyJob = null
        enqueueJob?.cancel()
        enqueueJob = null
        prefetchChannel.close()
        demandChannel.close()
        remaining.set(0)
        total.set(0)
        _state.value = PrefetchState.Idle
    }

    private fun currentWindow(): ProtectedWindow? {
        val s = server
        val book = currentBook
        return if (s != null && book != null) {
            val chapterHref = epubManifest?.readingOrder?.getOrNull(currentPageNumber)?.href
            ProtectedWindow(s.id, book.id, currentPageNumber, nextBook?.bookId, currentPageNumber, chapterHref)
        } else {
            null
        }
    }

    /** [currentWindow]基準で破棄を実行(start時呼出・IO dispatcherへ切替)。 */
    private suspend fun evictCurrentWindow() {
        val window = currentWindow() ?: return
        withContext(dispatcher) { evictor.evict(window) }
    }

    /** 現Windowの未処理targetを破棄し残りカウンタを合わせる（再センタリング前に呼ぶ）。 */
    private fun drainPrefetch() {
        while (prefetchChannel.tryReceive().getOrNull() != null) {
            remaining.updateAndGet { (it - 1).coerceAtLeast(0) }
        }
        updateState()
    }

    /** Windowを計算し、キャッシュ済みを除外してprefetchChannelへenqueue。 */
    private suspend fun enqueueWindow(currentPageNumber: Int) {
        val book = currentBook
        val srv = server
        if (book == null || srv == null) return
        val targets =
            if (mediaProfile.isEpub) {
                val manifest = epubManifest ?: return
                PrefetchPlanner.planEpub(book.id, manifest, currentPageNumber, nextBookManifests = null)
            } else {
                PrefetchPlanner.plan(book, currentPageNumber, nextBook)
            }
        val uncached =
            withContext(dispatcher) {
                targets.filter { target ->
                    store.get(srv.id, target.bookId, target.resourcePath, target.variant) == null
                }
            }
        total.set(uncached.size)
        remaining.set(uncached.size)
        for (target in uncached) prefetchChannel.trySend(target).getOrThrow()
        updateState()
    }

    private fun updateState() {
        val r = remaining.get()
        _state.value = if (r <= 0) PrefetchState.Idle else PrefetchState.Running(r, total.get())
    }

    private fun onTargetDone() {
        remaining.updateAndGet { (it - 1).coerceAtLeast(0) }
        updateState()
    }

    private suspend fun workerLoop() {
        try {
            while (coroutineContext.isActive) {
                val task = nextTarget()
                fetchTarget(task)
            }
        } catch (_: CancellationException) {
            // stop()/start()でcancelされた
        } catch (_: ClosedReceiveChannelException) {
            // Channelクローズで終了
        }
    }

    private suspend fun nextTarget(): FetchTask {
        awaitNotPaused()
        return pollFetch() ?: selectFetchTask()
    }

    private fun pollFetch(): FetchTask? =
        demandChannel.tryReceive().getOrNull()?.let { FetchTask(it, false) }
            ?: prefetchChannel.tryReceive().getOrNull()?.let { FetchTask(it, true) }

    private suspend fun selectFetchTask(): FetchTask =
        select {
            demandChannel.onReceive { FetchTask(it, false) }
            prefetchChannel.onReceive { FetchTask(it, true) }
        }

    private suspend fun awaitNotPaused() {
        if (pausedState.value) pausedState.first { !it }
    }

    private suspend fun fetchTarget(task: FetchTask) {
        val server = this.server
        if (server == null) {
            if (task.counts) onTargetDone()
            return
        }
        val target = task.target
        // ファイルI/Oを伴うStore操作は[dispatcher](prod=IO)へ切替。テストではtest dispatcherで仮想時間整合。
        val cached =
            withContext(dispatcher) {
                store.get(server.id, target.bookId, target.resourcePath, target.variant)
            }
        if (cached != null) {
            if (task.counts) onTargetDone()
            return
        }
        val result = fetchWithBackoff(server, target)
        if (result is KomgaResult.Success) {
            val overBudget =
                withContext(dispatcher) {
                    store.put(
                        serverId = server.id,
                        bookId = target.bookId,
                        resourcePath = target.resourcePath,
                        resourceKind = target.resourceKind,
                        variant = target.variant,
                        bytes = result.value,
                        etag = null,
                    )
                    currentWindow()?.let { evictor.evict(it) }
                    store.sumBytes() > evictionConfig.maxBytes
                }
            // 打ち切り: evict後も予算超 = Window自体が予算超。残りprefetchを破棄(demandは維持)。
            if (overBudget) drainPrefetch()
        }
        if (task.counts) onTargetDone()
    }

    private suspend fun fetchWithBackoff(
        server: Server,
        target: PrefetchTarget,
    ): KomgaResult<ByteArray> {
        var attempt = 0
        while (true) {
            val result =
                when (target.resourceKind) {
                    PrefetchStore.RESOURCE_KIND_PAGE ->
                        pageFetcher.fetch(server, target.bookId, target.pageNumber ?: 1, mediaProfile)
                    else ->
                        resourceFetcher?.fetch(server, target.bookId, target.resourcePath)
                            ?: KomgaResult.Failure(KomgaError.Unknown("ResourceFetcher unavailable"))
                }
            if (result is KomgaResult.Success) return result
            val error = (result as KomgaResult.Failure).error
            if (!isRetryable(error) || attempt >= backoff.maxAttempts) return result
            delay(backoff.delayFor(attempt))
            attempt++
        }
    }

    private fun isRetryable(error: KomgaError): Boolean =
        when (error) {
            is KomgaError.Network -> true
            is KomgaError.Http -> error.statusCode in 500..599
            is KomgaError.Unauthorized,
            is KomgaError.UntrustedCertificate,
            is KomgaError.Serialization,
            is KomgaError.Unknown,
            -> false
        }

    private data class FetchTask(
        val target: PrefetchTarget,
        val counts: Boolean,
    )
}

/**
 * 指数バックオフ設定。delayFor(attempt) = min(base * factor^attempt, max)。
 * テストで小さいbaseを渡し[runTest]仮想時間で間隔を検証。
 */
data class BackoffConfig(
    val baseMillis: Long = 500L,
    val factor: Double = 2.0,
    val maxDelayMillis: Long = 30_000L,
    val maxAttempts: Int = 5,
) {
    fun delayFor(attempt: Int): Long = (baseMillis * factor.pow(attempt)).toLong().coerceAtMost(maxDelayMillis)
}
