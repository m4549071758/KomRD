package dev.komrd.feature.readerepub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.epub.EpubRepository
import dev.komrd.core.data.prefetch.NextBookResolver
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.PrefetchSettingsStore
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.EpubLocator
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.prefetch.PrefetchContext
import dev.komrd.core.prefetch.PrefetchContextStore
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import dev.komrd.core.sync.EpubProgressSyncEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.Call
import javax.inject.Inject

sealed interface EpubReaderUiState {
    data object Loading : EpubReaderUiState

    data class Error(
        val message: String,
    ) : EpubReaderUiState

    data class Ready(
        val book: BookDetail,
        val manifest: EpubManifest,
        val currentChapterIndex: Int,
        val locator: EpubLocator?,
        val prefetchState: PrefetchState = PrefetchState.Idle,
    ) : EpubReaderUiState
}

@OptIn(kotlinx.coroutines.FlowPreview::class)
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions") // UDF ViewModelの公開API+privateヘルパ集約。既存パターンに倣う。
class EpubReaderViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val epubRepository: EpubRepository,
        private val epubProgressSyncEngine: EpubProgressSyncEngine,
        private val prefetchController: PrefetchController,
        private val prefetchSettingsStore: PrefetchSettingsStore,
        private val prefetchContextStore: PrefetchContextStore,
        private val nextBookResolver: NextBookResolver,
        private val clientFactory: KomgaClientFactory,
    ) : ViewModel() {
        private val uiStateFlow = MutableStateFlow<EpubReaderUiState>(EpubReaderUiState.Loading)
        val uiState: StateFlow<EpubReaderUiState> = uiStateFlow.asStateFlow()

        private var boundServerId: String? = null
        private var boundBookId: String? = null
        private var boundReadListId: String? = null
        private var loadedServer: Server? = null
        private var loadedBook: BookDetail? = null
        private var prefetchStateJob: Job? = null

        // 進捗同期のsettleキュー。章切替/スクロールでlocatorを積み、debounce後にPUT /progression。
        // locatorはhref(章)+totalProgression(章インデックス+章内進捗)を含むためdistinctUntilChangedで
        // 章またぎのブレを回避できる。
        private val settleFlow = MutableStateFlow<EpubLocator?>(null)

        // M4: 実行文脈保存キュー。章インデックスをdebounceしてPrefetchContextStoreへ保存。
        private val contextSaveFlow = MutableStateFlow<Int?>(null)
        private var savedContext: PrefetchContext? = null

        init {
            // 進捗同期: settleFlowを章インデックス+totalProgressionでdistinctしてdebounce → PUT。
            // 章切替直後はprogression=0(章頭)を送り、スクロール着地でtotalProgressionを送る。
            viewModelScope.launch {
                settleFlow
                    .filterNotNull()
                    .debounce(SETTLE_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { locator -> syncCurrent(locator) }
            }
            // M4: 実行文脈保存。章インデックスをdebounceしてContextStoreへ反映(プロセスkill後のresume用)。
            viewModelScope.launch {
                contextSaveFlow
                    .filterNotNull()
                    .debounce(CONTEXT_SAVE_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { chapterIndex ->
                        val ctx = savedContext?.copy(currentPage = chapterIndex) ?: return@collect
                        savedContext = ctx
                        prefetchContextStore.save(ctx)
                    }
            }
        }

        /**
         * ナビ引数を一度だけ受け取り、manifest/progressionをロードする。
         * [readListId]が非nullのときReading Context=Read List(次册解決がRead List順)。
         */
        fun bind(
            serverId: String,
            bookId: String,
            readListId: String? = null,
        ) {
            if (boundServerId == serverId && boundBookId == bookId && boundReadListId == readListId) return
            boundServerId = serverId
            boundBookId = bookId
            boundReadListId = readListId
            load(serverId, bookId, readListId)
        }

        /** [Error]時の再取得。 */
        fun retry() {
            val serverId = boundServerId ?: return
            val bookId = boundBookId ?: return
            load(serverId, bookId, boundReadListId)
        }

        /** 章切替(0-based)。locatorを章頭(progression=0)へ更新しsettle/保存キューへ積む。 */
        fun setCurrentChapter(index: Int) {
            val current = uiStateFlow.value as? EpubReaderUiState.Ready
            if (current == null || index == current.currentChapterIndex) return
            val clamped = index.coerceIn(0, current.manifest.readingOrder.lastIndex)
            val chapter = current.manifest.readingOrder.getOrNull(clamped) ?: return
            val locator =
                EpubLocator(
                    href = chapter.href,
                    progression = 0f,
                    totalProgression = totalProgressionFor(clamped, current.manifest.readingOrder.size),
                    position = clamped,
                )
            uiStateFlow.value = current.copy(currentChapterIndex = clamped, locator = locator)
            settleFlow.value = locator
            contextSaveFlow.value = clamped
            prefetchController.onPageChanged(clamped)
        }

        /** 次章へ。 */
        fun nextChapter() {
            val current = uiStateFlow.value as? EpubReaderUiState.Ready ?: return
            setCurrentChapter(current.currentChapterIndex + 1)
        }

        /** [EpubReaderScreen]がサーバ別OkHttp(認証/TLS)でリソース取得するためのServer。 */
        fun serverFor(): Server? = loadedServer

        /**
         * [EpubReaderScreen]のWebView `shouldInterceptRequest`が使うサーバ別OkHttp [Call.Factory]。
         * [KomgaClientFactory.clientFor]のOkHttpClientは認証Interceptor/TLS設定を内包するため、
         * これでリソース取得すれば認証/TLSがWebView経由でも共有される(最大ハマり回避)。
         */
        fun callFactoryFor(server: Server): Call.Factory = clientFactory.clientFor(server).okHttpClient

        /** 前章へ。 */
        fun prevChapter() {
            val current = uiStateFlow.value as? EpubReaderUiState.Ready ?: return
            setCurrentChapter(current.currentChapterIndex - 1)
        }

        fun onScrollProgression(progression: Float) {
            val current = uiStateFlow.value as? EpubReaderUiState.Ready ?: return
            val chapterIndex = current.currentChapterIndex
            val chapter = current.manifest.readingOrder.getOrNull(chapterIndex) ?: return
            val total = totalProgressionFor(chapterIndex, current.manifest.readingOrder.size, progression)
            val locator =
                EpubLocator(
                    href = chapter.href,
                    progression = progression,
                    totalProgression = total,
                    position = chapterIndex,
                )
            uiStateFlow.value = current.copy(locator = locator)
            settleFlow.value = locator
        }

        private fun load(
            serverId: String,
            bookId: String,
            readListId: String?,
        ) {
            uiStateFlow.value = EpubReaderUiState.Loading
            viewModelScope.launch {
                val server = serverRepository.byId(serverId)
                if (server == null) {
                    uiStateFlow.value = EpubReaderUiState.Error("Server $serverId not found")
                    return@launch
                }
                loadedServer = server
                when (val manifestResult = epubRepository.loadManifest(server, bookId)) {
                    is KomgaResult.Failure -> {
                        uiStateFlow.value = EpubReaderUiState.Error(messageFor(manifestResult.error))
                        return@launch
                    }
                    is KomgaResult.Success -> {
                        val manifest = manifestResult.value
                        val book = bookDetailStub(server, bookId, manifest)
                        loadedBook = book
                        val resumeLocator = loadProgressionOrNull(server, bookId)
                        val initialChapter = resumeLocator?.let { chapterIndexFor(it, manifest) } ?: 0
                        val locator =
                            resumeLocator ?: locatorForChapter(initialChapter, manifest)
                        uiStateFlow.value =
                            EpubReaderUiState.Ready(
                                book = book,
                                manifest = manifest,
                                currentChapterIndex = initialChapter,
                                locator = locator,
                            )
                        startPrefetch(server, book, readListId)
                        // 復帰時一括drain(他book含むServer単位)。本bookは下のsyncCurrentで送る。
                        viewModelScope.launch { epubProgressSyncEngine.flushPending(server) }
                    }
                }
            }
        }

        private suspend fun loadProgressionOrNull(
            server: Server,
            bookId: String,
        ): EpubLocator? =
            when (val result = epubRepository.loadProgression(server, bookId)) {
                is KomgaResult.Success -> result.value
                is KomgaResult.Failure -> null
            }

        /** locator.hrefがmanifest.readingOrderにあればそのindex、無ければ0。 */
        private fun chapterIndexFor(
            locator: EpubLocator,
            manifest: EpubManifest,
        ): Int {
            val idx = manifest.readingOrder.indexOfFirst { it.href == locator.href }
            return if (idx >= 0) idx else 0
        }

        private fun locatorForChapter(
            chapterIndex: Int,
            manifest: EpubManifest,
        ): EpubLocator? =
            manifest.readingOrder.getOrNull(chapterIndex)?.let { chapter ->
                EpubLocator(
                    href = chapter.href,
                    progression = 0f,
                    totalProgression = totalProgressionFor(chapterIndex, manifest.readingOrder.size),
                    position = chapterIndex,
                )
            }

        /**
         * 章インデックスベースの出版物全体進捗(0..1)。`progression`未指定時は章頭(均等割り当て)。
         * 竉細なスクロール進捗は[EpubReaderScreen]から[onScrollProgression]経由で上書きされる。
         */
        private fun totalProgressionFor(
            chapterIndex: Int,
            chapterCount: Int,
            intraChapterProgression: Float = 0f,
        ): Float {
            val count = chapterCount.coerceAtLeast(1)
            val base = chapterIndex.toFloat() / count
            val per = 1f / count
            return (base + per * intraChapterProgression).coerceIn(0f, 1f)
        }

        private fun syncCurrent(locator: EpubLocator) {
            val server = loadedServer ?: return
            val book = loadedBook ?: return
            viewModelScope.launch {
                epubProgressSyncEngine.sync(server, book.id, locator)
            }
        }

        private suspend fun startPrefetch(
            server: Server,
            book: BookDetail,
            readListId: String?,
        ) {
            prefetchStateJob?.cancel()
            if (!prefetchSettingsStore.enabled.first()) {
                prefetchController.stop()
                prefetchContextStore.clear()
                savedContext = null
                return
            }
            val nextBooksCount = prefetchSettingsStore.nextBooks.first()
            val seriesId = book.seriesId
            val context =
                when {
                    readListId != null -> ReadingContext.ReadList(readListId)
                    seriesId != null -> ReadingContext.Series(seriesId)
                    else -> null
                }
            val nextBook =
                if (nextBooksCount > 0 && context != null) {
                    (nextBookResolver.resolve(server, book.id, context) as? KomgaResult.Success)?.value
                } else {
                    null
                }
            val ctx =
                PrefetchContext(
                    serverId = server.id,
                    bookId = book.id,
                    currentPage = 0,
                    nextBookId = nextBook?.bookId,
                    nextBookPagesCount = nextBook?.pagesCount,
                )
            savedContext = ctx
            prefetchContextStore.save(ctx)

            val alreadyRunningSameBook =
                prefetchController.boundBookId == book.id &&
                    prefetchController.state.value is PrefetchState.Running

            prefetchStateJob =
                viewModelScope.launch {
                    prefetchController.state.collect { state ->
                        val current = uiStateFlow.value as? EpubReaderUiState.Ready
                        if (current != null) {
                            uiStateFlow.value = current.copy(prefetchState = state)
                        }
                    }
                }

            if (!alreadyRunningSameBook) {
                prefetchController.start(server, book, 0, nextBook)
            }
        }

        private fun bookDetailStub(
            server: Server,
            bookId: String,
            manifest: EpubManifest,
        ): BookDetail =
            BookDetail(
                id = bookId,
                serverId = server.id,
                name = manifest.title ?: bookId,
                pages = emptyList(),
                mediaProfile = dev.komrd.core.model.BookMediaProfile.EPUB,
            )

        private fun messageFor(error: KomgaError): String =
            when (error) {
                is KomgaError.Unauthorized -> "認証に失敗しました"
                is KomgaError.UntrustedCertificate -> "サーバ証明書が信頼できません"
                is KomgaError.Network -> "ネットワークに接続できません"
                else -> "読み込みに失敗しました"
            }

        override fun onCleared() {
            super.onCleared()
            // M4継続方針: PrefetchControllerはApplication SingletonでVMライフサイクルを超えて常駐。
            // stop()は呼ばない(リーダー画面離脱後もバックグラウンドで完了まで継続)。
            prefetchStateJob?.cancel()
        }
    }

private const val SETTLE_DEBOUNCE_MS = 500L

/** M4: 実行文脈保存のdebounce時間。章切替連打の書き込み過多を防ぐ。 */
private const val CONTEXT_SAVE_DEBOUNCE_MS = 500L
