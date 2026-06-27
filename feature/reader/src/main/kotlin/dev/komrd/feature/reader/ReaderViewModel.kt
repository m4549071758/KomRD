package dev.komrd.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.bookmark.BookmarkRepository
import dev.komrd.core.data.library.KomgaImageLoaders
import dev.komrd.core.data.prefetch.NextBookResolver
import dev.komrd.core.data.reader.ReaderRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.PrefetchSettingsStore
import dev.komrd.core.datastore.ReadingDirectionStore
import dev.komrd.core.datastore.SpreadModeStore
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.Server
import dev.komrd.core.model.SpreadMode
import dev.komrd.core.prefetch.PrefetchContext
import dev.komrd.core.prefetch.PrefetchContextStore
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import dev.komrd.core.sync.ReadProgressSyncEngine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReaderUiState {
    data object Loading : ReaderUiState

    data class Error(
        val error: KomgaError,
    ) : ReaderUiState

    data class Ready(
        val book: BookDetail,
        val readingDirection: ReadingDirection,
        val currentPage: Int,
        val server: Server,
        val prefetchState: PrefetchState = PrefetchState.Idle,
    ) : ReaderUiState
}

@OptIn(kotlinx.coroutines.FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("TooManyFunctions")
class ReaderViewModel
    @Inject
    @Suppress("LongParameterList")
    constructor(
        private val serverRepository: ServerRepository,
        private val readerRepository: ReaderRepository,
        private val readingDirectionStore: ReadingDirectionStore,
        private val spreadModeStore: SpreadModeStore,
        private val imageLoaders: KomgaImageLoaders,
        private val readProgressSyncEngine: ReadProgressSyncEngine,
        private val prefetchController: PrefetchController,
        private val prefetchSettingsStore: PrefetchSettingsStore,
        private val prefetchContextStore: PrefetchContextStore,
        private val nextBookResolver: NextBookResolver,
        private val bookmarkRepository: BookmarkRepository,
    ) : ViewModel() {
        private val uiStateFlow = MutableStateFlow<ReaderUiState>(ReaderUiState.Loading)
        val uiState: StateFlow<ReaderUiState> = uiStateFlow.asStateFlow()

        /**
         * 現在のServer+Bookのペア([boundServerId]/[boundBookId]確定時に設定)。
         * [bookmarkedPages]の購読起点。null = 未bindで空集合。
         */
        private val boundPairFlow = MutableStateFlow<Pair<String, String>?>(null)
        val spreadMode: StateFlow<SpreadMode> =
            spreadModeStore.spreadMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = SpreadMode.LANDSCAPE_ONLY,
                )
        val bookmarkedPages: StateFlow<Set<Int>> =
            boundPairFlow
                .filterNotNull()
                .flatMapLatest { (serverId, bookId) ->
                    bookmarkRepository
                        .observe(serverId, bookId)
                        .map { bookmarks -> bookmarks.map { it.pageNumber }.toSet() }
                }.stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = emptySet(),
                )

        private var boundServerId: String? = null
        private var boundBookId: String? = null
        private var boundReadListId: String? = null

        // 読書方向のlive再解決用に直近に読み込んだBookを保持(②Series値の有無判定)。
        private var loadedBook: BookDetail? = null

        private var prefetchStateJob: Job? = null

        // 進捗同期のsettleキュー。ユーザ操作でsetCurrentPageされたindexのみ入り、debounce後に同期。
        private val settleFlow = MutableStateFlow<Int?>(null)

        // M4: 実行文脈保存キュー。currentPage(0-based)をdebounceしてPrefetchContextStoreへ保存。
        private val contextSaveFlow = MutableStateFlow<Int?>(null)
        private var savedContext: PrefetchContext? = null

        init {
            // グローバル既定(③)変更を監視。現Bookが②Series値を持たない場合のみReady.readingDirectionをlive更新
            // (②がある場合はそちらが優先されるので変更不要)。pager種別が切り替わるがcurrentPageは保持で再seek。
            viewModelScope.launch {
                readingDirectionStore.readingDirection.collect { global ->
                    val current = uiStateFlow.value as? ReaderUiState.Ready
                    val book = loadedBook
                    if (current != null && book?.readingDirection == null) {
                        uiStateFlow.value = current.copy(readingDirection = global)
                    }
                }
            }
            // スクロール/スクラブ中の中間ページはdebounceで吸収し、着地ページのみ送る。
            // ロード時の初期ページ0はsettleFlowへ入れない(サーバ保存進捗を上書きしない)。
            viewModelScope.launch {
                settleFlow
                    .filterNotNull()
                    .debounce(SETTLE_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { index -> syncCurrent(index) }
            }
            // M4: 実行文脈保存。currentPageをdebounceしてContextStoreへ反映（プロセスkill後のresume用）。
            viewModelScope.launch {
                contextSaveFlow
                    .filterNotNull()
                    .debounce(CONTEXT_SAVE_DEBOUNCE_MS)
                    .distinctUntilChanged()
                    .collect { page ->
                        val ctx = savedContext?.copy(currentPage = page) ?: return@collect
                        savedContext = ctx
                        prefetchContextStore.save(ctx)
                    }
            }
        }

        fun bind(
            serverId: String,
            bookId: String,
            readListId: String? = null,
        ) {
            if (boundServerId == serverId && boundBookId == bookId && boundReadListId == readListId) return
            boundServerId = serverId
            boundBookId = bookId
            boundReadListId = readListId
            boundPairFlow.value = serverId to bookId
            load(serverId, bookId, readListId)
        }

        /** [Error]時の再取得。 */
        fun retry() {
            val serverId = boundServerId ?: return
            val bookId = boundBookId ?: return
            load(serverId, bookId, boundReadListId)
        }

        fun setCurrentPage(index: Int) {
            val current = uiStateFlow.value as? ReaderUiState.Ready ?: return
            if (index == current.currentPage) return
            val clamped = index.coerceIn(0, current.book.pagesCount - 1)
            uiStateFlow.value = current.copy(currentPage = clamped)
            settleFlow.value = clamped
            contextSaveFlow.value = clamped
            prefetchController.onPageChanged(clamped)
        }

        fun setSpreadMode(mode: SpreadMode) {
            viewModelScope.launch { spreadModeStore.set(mode) }
        }

        /**
         * 読書方向のグローバル既定(③)を更新（読書中メニューの読書設定）。
         * 現Bookが②Series値を持たない場合は本VMのinit collectorがReady.readingDirectionをlive更新する。
         * ②があるBookでは本設定は現Bookに影響しない(グローバル既定のみ変わる)。
         */
        fun setReadingDirection(direction: ReadingDirection) {
            viewModelScope.launch { readingDirectionStore.set(direction) }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaders.forServer(server)

        fun toggleBookmark() {
            val current = uiStateFlow.value as? ReaderUiState.Ready ?: return
            val index = current.currentPage
            val pageNumber =
                current.book.pages
                    .getOrNull(index)
                    ?.number ?: (index + 1)
            viewModelScope.launch {
                bookmarkRepository.toggle(current.server.id, current.book.id, pageNumber)
            }
        }

        private fun syncCurrent(index: Int) {
            val current = uiStateFlow.value as? ReaderUiState.Ready ?: return
            val book = current.book
            val page = book.pages.getOrNull(index)?.number ?: (index + 1)
            val completed = index >= book.pagesCount - 1
            viewModelScope.launch {
                readProgressSyncEngine.sync(current.server, book.id, page, completed)
            }
        }

        private fun load(
            serverId: String,
            bookId: String,
            readListId: String? = null,
        ) {
            uiStateFlow.value = ReaderUiState.Loading
            viewModelScope.launch {
                val server = serverRepository.byId(serverId)
                if (server == null) {
                    uiStateFlow.value = ReaderUiState.Error(KomgaError.Unknown("Server $serverId not found"))
                    return@launch
                }
                when (val result = readerRepository.loadBook(server, bookId)) {
                    is KomgaResult.Success -> {
                        loadedBook = result.value
                        val globalDefault = readingDirectionStore.readingDirection.first()
                        val direction = result.value.readingDirection ?: globalDefault
                        uiStateFlow.value =
                            ReaderUiState.Ready(
                                book = result.value,
                                readingDirection = direction,
                                currentPage = 0,
                                server = server,
                            )
                        startPrefetch(server, result.value, readListId)
                        viewModelScope.launch { readProgressSyncEngine.flushPending(server) }
                    }
                    is KomgaResult.Failure ->
                        uiStateFlow.value = ReaderUiState.Error(result.error)
                }
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

            // 実行文脈を永続化（start時1回。currentPageは0-based）。nextBookId/pagesCountで復元時の再解決を回避。
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

            // 同一Bookで既に稼働中（バックグラウンド継続中の再bind等）ならstartを省略し、状態観測だけ再開。
            val alreadyRunningSameBook =
                prefetchController.boundBookId == book.id &&
                    prefetchController.state.value is PrefetchState.Running

            prefetchStateJob =
                viewModelScope.launch {
                    prefetchController.state.collect { state ->
                        val current = uiStateFlow.value as? ReaderUiState.Ready
                        if (current != null) {
                            uiStateFlow.value = current.copy(prefetchState = state)
                        }
                    }
                }

            if (!alreadyRunningSameBook) {
                prefetchController.start(server, book, 0, nextBook)
            }
        }

        override fun onCleared() {
            super.onCleared()
            // M4: PrefetchControllerはApplication SingletonでVMライフサイクルを超えて常駐。
            // VM側の状態観測だけ切り、Controllerの稼働は Coordinator/Worker が背面時に引き継ぐ。
            prefetchStateJob?.cancel()
        }
    }

private const val SETTLE_DEBOUNCE_MS = 500L

/** M4: 実行文脈保存のdebounce時間。ページ送り連打の書き込み過多を防ぐ。 */
private const val CONTEXT_SAVE_DEBOUNCE_MS = 500L
