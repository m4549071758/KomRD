package dev.komrd.feature.reader

import app.cash.turbine.test
import dev.komrd.core.cache.PrefetchEntry
import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.cache.ThumbnailStore
import dev.komrd.core.common.error.CertificateInfo
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
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.Bookmark
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.Server
import dev.komrd.core.model.SpreadMode
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import dev.komrd.core.prefetch.PrefetchContext
import dev.komrd.core.prefetch.PrefetchContextStore
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import dev.komrd.core.sync.ReadProgressSyncEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.cert.X509Certificate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class ReaderViewModelTest {
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun bind_loadsBook_andResolvesReadingDirectionFromBook() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1"), BookPage(2, "u2")),
                    readingDirection = ReadingDirection.RIGHT_TO_LEFT,
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    readingDirectionStore = FakeReadingDirectionStore(ReadingDirection.LEFT_TO_RIGHT),
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            vm.uiState.test {
                val state = awaitItem()
                assertTrue(state is ReaderUiState.Ready)
                val ready = state as ReaderUiState.Ready
                assertEquals(ReadingDirection.RIGHT_TO_LEFT, ready.readingDirection)
                assertEquals(2, ready.book.pagesCount)
                assertEquals(0, ready.currentPage)
                assertEquals(server.id, ready.server.id)
            }
        }

    @Test
    fun bind_bookWithoutDirection_fallsBackToGlobalDefault() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1")),
                    readingDirection = null,
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    readingDirectionStore = FakeReadingDirectionStore(ReadingDirection.RIGHT_TO_LEFT),
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is ReaderUiState.Ready)
            assertEquals(ReadingDirection.RIGHT_TO_LEFT, (state as ReaderUiState.Ready).readingDirection)
        }

    @Test
    fun bind_loadFailure_emitsError() =
        runTest(testDispatcher) {
            val server = server()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Failure(KomgaError.Http(500, "boom")) },
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is ReaderUiState.Error)
            assertTrue((state as ReaderUiState.Error).error is KomgaError.Http)
        }

    @Test
    fun bind_unknownServer_emitsError() =
        runTest(testDispatcher) {
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(null),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(bookDetail("book-1")) },
                )

            vm.bind("missing", "book-1")
            advanceUntilIdle()

            assertTrue(vm.uiState.value is ReaderUiState.Error)
        }

    @Test
    fun retry_afterError_reloads() =
        runTest(testDispatcher) {
            val server = server()
            val results =
                ArrayDeque(
                    listOf<KomgaResult<BookDetail>>(
                        KomgaResult.Failure(KomgaError.Network("net")),
                        KomgaResult.Success(bookDetail("book-1")),
                    ),
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { results.removeFirst() },
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            assertTrue(vm.uiState.value is ReaderUiState.Error)

            vm.retry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is ReaderUiState.Ready)
        }

    @Test
    fun bind_startsPrefetchController_andUpdatesPrefetchState() =
        runTest(testDispatcher) {
            val server = server()
            val book = bookDetail("book-1")

            val prefetchStateFlow = MutableStateFlow<PrefetchState>(PrefetchState.Idle)

            val controller = FakePrefetchController(stateFlow = prefetchStateFlow)

            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    prefetchController = controller,
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            val state1 = vm.uiState.value as ReaderUiState.Ready
            assertEquals(dev.komrd.core.prefetch.PrefetchState.Idle, state1.prefetchState)

            prefetchStateFlow.value =
                dev.komrd.core.prefetch.PrefetchState
                    .Running(remaining = 5, total = 10)
            advanceUntilIdle()

            val state2 = vm.uiState.value as ReaderUiState.Ready
            val prefetchState = state2.prefetchState
            assertTrue(prefetchState is dev.komrd.core.prefetch.PrefetchState.Running)
            assertEquals(5, (prefetchState as dev.komrd.core.prefetch.PrefetchState.Running).remaining)
        }

    @Test
    fun setCurrentPage_updatesReadyState() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1"), BookPage(2, "u2"), BookPage(3, "u3")),
                    readingDirection = null,
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            vm.setCurrentPage(2)
            assertEquals(2, (vm.uiState.value as ReaderUiState.Ready).currentPage)

            // 範囲外はクランプ
            vm.setCurrentPage(99)
            assertEquals(2, (vm.uiState.value as ReaderUiState.Ready).currentPage)
        }

    @Test
    fun bind_drainsPendingProgressAndDoesNotSyncInitialPage() =
        runTest(testDispatcher) {
            val server = server()
            val engine = FakeReadProgressSyncEngine()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(bookDetail("book-1")) },
                    readProgressSyncEngine = engine,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            assertEquals(listOf(server.id), engine.flushCalls)
            assertTrue(engine.syncCalls.isEmpty())
        }

    @Test
    fun setCurrentPage_debouncesIntermediatePages_syncsOnlyLatestSettle() =
        runTest(testDispatcher) {
            val server = server()
            val engine = FakeReadProgressSyncEngine()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1"), BookPage(2, "u2"), BookPage(3, "u3")),
                    readingDirection = null,
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    readProgressSyncEngine = engine,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            engine.flushCalls.clear()

            // スクラブ中の中間ページ: 1→2 と連続。debounce後は着地(2=最終)のみ1回送る。
            vm.setCurrentPage(1)
            vm.setCurrentPage(2)
            advanceUntilIdle()

            assertEquals(1, engine.syncCalls.size)
            val call = engine.syncCalls.single()
            assertEquals(server.id, call[0])
            assertEquals("book-1", call[1])
            assertEquals(3, call[2]) // pages[2].number (1-based)
            assertEquals(true, call[3]) // 最終ページでcompleted=true
        }

    @Test
    fun setCurrentPage_nonLastPage_syncsCompletedFalse() =
        runTest(testDispatcher) {
            val server = server()
            val engine = FakeReadProgressSyncEngine()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1"), BookPage(2, "u2"), BookPage(3, "u3")),
                    readingDirection = null,
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    readProgressSyncEngine = engine,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            engine.flushCalls.clear()

            vm.setCurrentPage(1)
            advanceUntilIdle()

            assertEquals(1, engine.syncCalls.size)
            assertEquals(2, engine.syncCalls.single()[2])
            assertEquals(false, engine.syncCalls.single()[3])
        }

    @Test
    fun setSpreadMode_propagatesToStore() =
        runTest(testDispatcher) {
            val spreadStore = FakeSpreadModeStore(SpreadMode.LANDSCAPE_ONLY)
            val vm = viewModel(spreadModeStore = spreadStore)
            vm.bind("s1", "book-1")
            advanceUntilIdle()

            vm.setSpreadMode(SpreadMode.OFF)
            advanceUntilIdle()

            assertEquals(SpreadMode.OFF, vm.spreadMode.value)
        }

    @Test
    fun setReadingDirection_updatesReady_whenBookHasNoSeriesDirection() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1")),
                    readingDirection = null, // ③グローバル既定で解決
                )
            val directionStore = FakeReadingDirectionStore(ReadingDirection.LEFT_TO_RIGHT)
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    readingDirectionStore = directionStore,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            assertEquals(
                ReadingDirection.LEFT_TO_RIGHT,
                (vm.uiState.value as ReaderUiState.Ready).readingDirection,
            )

            // グローバル既定(③)を変更 → 現Bookは②を持たないのでlive再解決
            vm.setReadingDirection(ReadingDirection.VERTICAL)
            advanceUntilIdle()
            assertEquals(
                ReadingDirection.VERTICAL,
                (vm.uiState.value as ReaderUiState.Ready).readingDirection,
            )
        }

    @Test
    fun setReadingDirection_doesNotOverride_whenBookHasSeriesDirection() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1")),
                    readingDirection = ReadingDirection.RIGHT_TO_LEFT, // ②Series値(優先)
                )
            val directionStore = FakeReadingDirectionStore(ReadingDirection.LEFT_TO_RIGHT)
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    readingDirectionStore = directionStore,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            // グローバル既定(③)を変更しても②Series値が優先され不变
            vm.setReadingDirection(ReadingDirection.VERTICAL)
            advanceUntilIdle()
            assertEquals(
                ReadingDirection.RIGHT_TO_LEFT,
                (vm.uiState.value as ReaderUiState.Ready).readingDirection,
            )
        }

    @Test
    fun bookmarkedPages_isEmptyBeforeBind_thenReflectsRepositoryState() =
        runTest(testDispatcher) {
            val server = server()
            val book = bookDetail("book-1")
            val bookmarks = FakeBookmarkRepository()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    bookmarkRepository = bookmarks,
                )
            // bind前は空
            assertTrue(vm.bookmarkedPages.value.isEmpty())

            bookmarks.emit(server.id, book.id, listOf(Bookmark(server.id, book.id, 2, null, 10L)))
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            assertEquals(setOf(2), vm.bookmarkedPages.value)
        }

    @Test
    fun toggleBookmark_addsCurrentPage_toBookmarkedPages() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1"), BookPage(2, "u2"), BookPage(3, "u3")),
                    readingDirection = null,
                )
            val bookmarks = FakeBookmarkRepository()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    bookmarkRepository = bookmarks,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            // 現在ページ index=1 → Komga pageNumber=2
            vm.setCurrentPage(1)
            vm.toggleBookmark()
            advanceUntilIdle()

            val toggled = bookmarks.toggleCalls.single()
            assertEquals(server.id, toggled.first)
            assertEquals(book.id, toggled.second)
            assertEquals(2, toggled.third)
            // リポジトリ側の状態更新がbookmarkedPagesへ伝播
            assertEquals(setOf(2), vm.bookmarkedPages.value)
        }

    @Test
    fun toggleBookmark_removesCurrentPage_whenAlreadyBookmarked() =
        runTest(testDispatcher) {
            val server = server()
            val book =
                BookDetail(
                    id = "book-1",
                    serverId = server.id,
                    name = "Book One",
                    pages = listOf(BookPage(1, "u1")),
                    readingDirection = null,
                )
            val bookmarks = FakeBookmarkRepository()
            // 事前に現在ページ(pageNumber=1)をしおり登録済み
            bookmarks.emit(server.id, book.id, listOf(Bookmark(server.id, book.id, 1, null, 10L)))
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    readerRepository = FakeReaderRepository { KomgaResult.Success(book) },
                    bookmarkRepository = bookmarks,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            assertEquals(setOf(1), vm.bookmarkedPages.value)

            vm.toggleBookmark()
            advanceUntilIdle()

            assertEquals(1, bookmarks.toggleCalls.size)
            // toggle呼出 → リポジトリ状態から当該ページ除去
            assertTrue(vm.bookmarkedPages.value.isEmpty())
        }

    @Suppress("LongParameterList")
    private fun viewModel(
        serverRepository: ServerRepository = FakeServerRepository(server()),
        readerRepository: ReaderRepository = FakeReaderRepository { KomgaResult.Success(bookDetail("book-1")) },
        readingDirectionStore: ReadingDirectionStore = FakeReadingDirectionStore(ReadingDirection.LEFT_TO_RIGHT),
        spreadModeStore: SpreadModeStore = FakeSpreadModeStore(SpreadMode.LANDSCAPE_ONLY),
        readProgressSyncEngine: ReadProgressSyncEngine = FakeReadProgressSyncEngine(),
        prefetchController: PrefetchController = FakePrefetchController(),
        prefetchSettingsStore: PrefetchSettingsStore = FakePrefetchSettingsStore(),
        prefetchContextStore: PrefetchContextStore = FakePrefetchContextStore(),
        nextBookResolver: NextBookResolver = FakeNextBookResolver(),
        bookmarkRepository: BookmarkRepository = FakeBookmarkRepository(),
    ): ReaderViewModel =
        ReaderViewModel(
            serverRepository = serverRepository,
            readerRepository = readerRepository,
            readingDirectionStore = readingDirectionStore,
            spreadModeStore = spreadModeStore,
            imageLoaders =
                KomgaImageLoaders(
                    context = RuntimeEnvironment.getApplication(),
                    clientFactory =
                        KomgaClientFactory(
                            sessionStore = InMemorySessionStore(),
                            trustStore = InMemoryServerTrustStore(),
                        ),
                    prefetchStore = NoOpPrefetchStore,
                    thumbnailStore = NoOpThumbnailStore,
                ),
            readProgressSyncEngine = readProgressSyncEngine,
            prefetchController = prefetchController,
            prefetchSettingsStore = prefetchSettingsStore,
            prefetchContextStore = prefetchContextStore,
            nextBookResolver = nextBookResolver,
            bookmarkRepository = bookmarkRepository,
        )

    private fun server() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = "https://example.com/",
            auth = AuthMethod.ApiKey("api-key"),
        )

    private fun bookDetail(id: String) =
        BookDetail(
            id = id,
            serverId = "s1",
            name = "Book",
            pages = listOf(BookPage(1, "u1")),
            readingDirection = null,
        )
}

private class FakeServerRepository(
    private val server: Server?,
) : ServerRepository {
    override val servers: Flow<List<Server>> = MutableStateFlow(emptyList())

    override suspend fun byId(id: String): Server? = server

    override suspend fun add(server: Server) = Unit

    override suspend fun update(server: Server) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult> =
        KomgaResult.Success(ConnectionResult.Authenticated())

    override suspend fun pinCertificate(
        serverId: String,
        certificate: CertificateInfo,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<X509Certificate>,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean = false

    override fun certificateInfoOf(error: KomgaError): CertificateInfo? = null
}

private class FakeReaderRepository(
    private val load: () -> KomgaResult<BookDetail>,
) : ReaderRepository {
    override suspend fun loadBook(
        server: Server,
        bookId: String,
    ): KomgaResult<BookDetail> = load()
}

private class FakeReadingDirectionStore(
    initial: ReadingDirection,
) : ReadingDirectionStore {
    private val flow = MutableStateFlow(initial)

    override val readingDirection: Flow<ReadingDirection> = flow

    override suspend fun set(direction: ReadingDirection) {
        flow.value = direction
    }
}

private class FakeSpreadModeStore(
    initial: SpreadMode,
) : SpreadModeStore {
    private val flow = MutableStateFlow(initial)

    override val spreadMode: Flow<SpreadMode> = flow

    override suspend fun set(mode: SpreadMode) {
        flow.value = mode
    }
}

internal class FakeReadProgressSyncEngine : ReadProgressSyncEngine {
    val syncCalls = mutableListOf<List<Any?>>()
    val flushCalls = mutableListOf<String>()

    override suspend fun sync(
        server: Server,
        bookId: String,
        page: Int,
        completed: Boolean,
    ): KomgaResult<Unit> {
        syncCalls.add(listOf(server.id, bookId, page, completed))
        return KomgaResult.Success(Unit)
    }

    override suspend fun flushPending(server: Server) {
        flushCalls.add(server.id)
    }
}

private class FakePrefetchSettingsStore : PrefetchSettingsStore {
    override val enabled: Flow<Boolean> = MutableStateFlow(true)
    override val nextBooks: Flow<Int> = MutableStateFlow(1)
    override val parallelism: Flow<Int> = MutableStateFlow(2)
    override val retentionDays: Flow<Int> = MutableStateFlow(3)
    override val maxBytes: Flow<Long> = MutableStateFlow(100L)
    override val allowOnMobile: Flow<Boolean> = MutableStateFlow(true)

    override suspend fun setEnabled(enabled: Boolean) = Unit

    override suspend fun setNextBooks(count: Int) = Unit

    override suspend fun setParallelism(count: Int) = Unit

    override suspend fun setRetentionDays(days: Int) = Unit

    override suspend fun setMaxBytes(bytes: Long) = Unit

    override suspend fun setAllowOnMobile(allow: Boolean) = Unit
}

internal class FakePrefetchController(
    private val stateFlow: StateFlow<PrefetchState> = MutableStateFlow(PrefetchState.Idle),
) : PrefetchController {
    override val state: StateFlow<PrefetchState> = stateFlow
    override val boundServerId: String? = null
    override val boundBookId: String? = null

    override suspend fun start(
        server: Server,
        currentBook: BookDetail,
        currentPageNumber: Int,
        nextBook: dev.komrd.core.model.NextBook?,
    ) = Unit

    override fun onPageChanged(pageNumber: Int) = Unit

    override fun demand(
        bookId: String,
        pageNumber: Int,
    ) = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() = Unit
}

internal class FakePrefetchContextStore : PrefetchContextStore {
    val saved = mutableListOf<PrefetchContext>()
    var cleared = false
    private val flow = MutableStateFlow<PrefetchContext?>(null)

    override val prefetchContext: Flow<PrefetchContext?> = flow

    override suspend fun save(context: PrefetchContext) {
        saved.add(context)
        flow.value = context
    }

    override suspend fun clear() {
        cleared = true
        flow.value = null
    }
}

private class FakeNextBookResolver : dev.komrd.core.data.prefetch.NextBookResolver {
    override suspend fun resolve(
        server: Server,
        currentBookId: String,
        context: dev.komrd.core.model.ReadingContext,
    ): KomgaResult<dev.komrd.core.model.NextBook?> = KomgaResult.Success(null)
}

/**
 * [BookmarkRepository]のテスト用Fake。`observe`は内部StateFlowを公開し、
 * [emit]で状態を投入・[toggle]/[delete]呼出を記録する。toggleは現状集合に応じて追加/削除を模倣する。
 */
private class FakeBookmarkRepository : BookmarkRepository {
    private val flows = mutableMapOf<Pair<String, String>, MutableStateFlow<List<Bookmark>>>()
    val toggleCalls = mutableListOf<Triple<String, String, Int>>()
    val deleteCalls = mutableListOf<Triple<String, String, Int>>()

    private fun flowFor(
        serverId: String,
        bookId: String,
    ): MutableStateFlow<List<Bookmark>> = flows.getOrPut(serverId to bookId) { MutableStateFlow(emptyList()) }

    fun emit(
        serverId: String,
        bookId: String,
        bookmarks: List<Bookmark>,
    ) {
        flowFor(serverId, bookId).value = bookmarks
    }

    override fun observe(
        serverId: String,
        bookId: String,
    ): Flow<List<Bookmark>> = flowFor(serverId, bookId)

    override suspend fun toggle(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        note: String?,
    ) {
        toggleCalls.add(Triple(serverId, bookId, pageNumber))
        val flow = flowFor(serverId, bookId)
        val current = flow.value
        flow.value =
            if (current.any { it.pageNumber == pageNumber }) {
                current.filter { it.pageNumber != pageNumber }
            } else {
                current + Bookmark(serverId, bookId, pageNumber, note, 0L)
            }
    }

    override suspend fun delete(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ) {
        deleteCalls.add(Triple(serverId, bookId, pageNumber))
        val flow = flowFor(serverId, bookId)
        flow.value = flow.value.filter { it.pageNumber != pageNumber }
    }
}

/** [PrefetchStore]のテスト用NoOp([KomgaImageLoaders] ctor満たし用・VMテストで取得は行わない)。 */
private object NoOpPrefetchStore : PrefetchStore {
    override suspend fun put(
        serverId: String,
        bookId: String,
        resourcePath: String,
        resourceKind: String,
        variant: String,
        bytes: ByteArray,
        etag: String?,
    ): PrefetchEntry =
        PrefetchEntry(
            serverId = serverId,
            bookId = bookId,
            resourcePath = resourcePath,
            resourceKind = resourceKind,
            variant = variant,
            file = File("/noop"),
            sizeBytes = bytes.size.toLong(),
            fetchedAt = 0L,
            lastAccessedAt = 0L,
            etag = etag,
            pageNumber = resourcePath.toIntOrNull(),
        )

    override suspend fun get(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): PrefetchEntry? = null

    override suspend fun listByBook(
        serverId: String,
        bookId: String,
    ): List<PrefetchEntry> = emptyList()

    override suspend fun listByServer(serverId: String): List<PrefetchEntry> = emptyList()

    override suspend fun listAll(): List<PrefetchEntry> = emptyList()

    override suspend fun sumBytes(): Long = 0L

    override suspend fun delete(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): Boolean = false

    override suspend fun deleteByBook(
        serverId: String,
        bookId: String,
    ): Int = 0

    override suspend fun deleteByServer(serverId: String): Int = 0
}

/** [ThumbnailStore]のテスト用NoOp([KomgaImageLoaders] ctor満たし用)。 */
private object NoOpThumbnailStore : ThumbnailStore {
    override suspend fun get(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File? = null

    override suspend fun generate(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        source: File,
    ): File = File("/noop")

    override suspend fun putBytes(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        bytes: ByteArray,
    ): File = File("/noop")

    override suspend fun thumbnailOrNull(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File? = null
}
