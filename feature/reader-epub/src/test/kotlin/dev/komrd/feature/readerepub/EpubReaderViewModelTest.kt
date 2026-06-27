package dev.komrd.feature.readerepub

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.epub.EpubRepository
import dev.komrd.core.data.prefetch.NextBookResolver
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.PrefetchSettingsStore
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.EpubChapter
import dev.komrd.core.model.EpubLocator
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.EpubResource
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import dev.komrd.core.prefetch.PrefetchContext
import dev.komrd.core.prefetch.PrefetchContextStore
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import dev.komrd.core.sync.EpubProgressSyncEngine
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class EpubReaderViewModelTest {
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
    fun bind_loadsManifest_andEmitsReady() =
        runTest(testDispatcher) {
            val server = server()
            val manifest = manifest()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest) }),
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is EpubReaderUiState.Ready)
            val ready = state as EpubReaderUiState.Ready
            assertEquals(2, ready.manifest.readingOrder.size)
            assertEquals(0, ready.currentChapterIndex)
            assertEquals("OEBPS/ch1.xhtml", ready.locator?.href)
        }

    @Test
    fun bind_manifestFailure_emitsError() =
        runTest(testDispatcher) {
            val server = server()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository =
                        FakeEpubRepository(
                            manifestResult = { KomgaResult.Failure(KomgaError.Http(500, "boom")) },
                        ),
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state is EpubReaderUiState.Error)
        }

    @Test
    fun bind_unknownServer_emitsError() =
        runTest(testDispatcher) {
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(null),
                    epubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest()) }),
                )

            vm.bind("missing", "book-1")
            advanceUntilIdle()

            assertTrue(vm.uiState.value is EpubReaderUiState.Error)
        }

    @Test
    fun retry_afterError_reloads() =
        runTest(testDispatcher) {
            val server = server()
            val results =
                ArrayDeque(
                    listOf<KomgaResult<EpubManifest>>(
                        KomgaResult.Failure(KomgaError.Network("net")),
                        KomgaResult.Success(manifest()),
                    ),
                )
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository = FakeEpubRepository(manifestResult = { results.removeFirst() }),
                )

            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            assertTrue(vm.uiState.value is EpubReaderUiState.Error)

            vm.retry()
            advanceUntilIdle()
            assertTrue(vm.uiState.value is EpubReaderUiState.Ready)
        }

    @Test
    fun setCurrentChapter_updatesReadyAndNotifiesPrefetch() =
        runTest(testDispatcher) {
            val server = server()
            val controller = FakePrefetchController()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest()) }),
                    prefetchController = controller,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            vm.setCurrentChapter(1)
            advanceUntilIdle()

            val ready = vm.uiState.value as EpubReaderUiState.Ready
            assertEquals(1, ready.currentChapterIndex)
            assertEquals("OEBPS/ch2.xhtml", ready.locator?.href)
            assertEquals(1, controller.pageChangedCalls.size)
            assertEquals(1, controller.pageChangedCalls.single())
        }

    @Test
    fun nextChapter_andPrevChapter_navigateWithinBounds() =
        runTest(testDispatcher) {
            val server = server()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest()) }),
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            vm.nextChapter()
            assertEquals(1, (vm.uiState.value as EpubReaderUiState.Ready).currentChapterIndex)
            // 末尾でnextChapterはクランプ(最終章)
            vm.nextChapter()
            assertEquals(1, (vm.uiState.value as EpubReaderUiState.Ready).currentChapterIndex)
            vm.prevChapter()
            assertEquals(0, (vm.uiState.value as EpubReaderUiState.Ready).currentChapterIndex)
        }

    @Test
    fun bind_drainsPendingProgressAndDoesNotSyncInitialLocator() =
        runTest(testDispatcher) {
            val server = server()
            val syncEngine = FakeEpubProgressSyncEngine()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest()) }),
                    epubProgressSyncEngine = syncEngine,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()

            assertEquals(listOf(server.id), syncEngine.flushCalls)
            assertTrue(syncEngine.syncCalls.isEmpty())
        }

    @Test
    fun setCurrentChapter_debouncesAndSyncsLatestLocator() =
        runTest(testDispatcher) {
            val server = server()
            val syncEngine = FakeEpubProgressSyncEngine()
            val vm =
                viewModel(
                    serverRepository = FakeServerRepository(server),
                    epubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest()) }),
                    epubProgressSyncEngine = syncEngine,
                )
            vm.bind(server.id, "book-1")
            advanceUntilIdle()
            syncEngine.flushCalls.clear()

            // 章切替 0→1→2(クランプで1) と連続。debounce後は着地(1)のみ1回送る。
            vm.setCurrentChapter(1)
            vm.onScrollProgression(0.5f)
            advanceUntilIdle()

            assertEquals(1, syncEngine.syncCalls.size)
            val call = syncEngine.syncCalls.single()
            assertEquals(server.id, call.server.id)
            assertEquals("book-1", call.bookId)
            // 末尾章(ch2)のスクロール進捗0.5が反映されていること
            assertTrue(call.locator.totalProgression != null)
        }

    @Suppress("LongParameterList")
    private fun viewModel(
        serverRepository: ServerRepository = FakeServerRepository(server()),
        epubRepository: EpubRepository = FakeEpubRepository(manifestResult = { KomgaResult.Success(manifest()) }),
        epubProgressSyncEngine: EpubProgressSyncEngine = FakeEpubProgressSyncEngine(),
        prefetchController: PrefetchController = FakePrefetchController(),
        prefetchSettingsStore: PrefetchSettingsStore = FakePrefetchSettingsStore(),
        prefetchContextStore: PrefetchContextStore = FakePrefetchContextStore(),
        nextBookResolver: NextBookResolver = FakeNextBookResolver(),
    ): EpubReaderViewModel =
        EpubReaderViewModel(
            serverRepository = serverRepository,
            epubRepository = epubRepository,
            epubProgressSyncEngine = epubProgressSyncEngine,
            prefetchController = prefetchController,
            prefetchSettingsStore = prefetchSettingsStore,
            prefetchContextStore = prefetchContextStore,
            nextBookResolver = nextBookResolver,
            clientFactory =
                KomgaClientFactory(
                    sessionStore = InMemorySessionStore(),
                    trustStore = InMemoryServerTrustStore(),
                ),
        )

    private fun server() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = "https://example.com/",
            auth = AuthMethod.ApiKey("api-key"),
        )

    private fun manifest() =
        EpubManifest(
            readingOrder =
                listOf(
                    EpubChapter(href = "OEBPS/ch1.xhtml", type = "application/xhtml+xml", title = "Chapter 1"),
                    EpubChapter(href = "OEBPS/ch2.xhtml", type = "application/xhtml+xml", title = "Chapter 2"),
                ),
            toc = emptyList(),
            resources = listOf(EpubResource(href = "OEBPS/styles.css", type = "text/css", rel = "stylesheet")),
            title = "Test EPUB",
            readingProgression = "ltr",
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

    override suspend fun verifyConnection(server: Server): KomgaResult<dev.komrd.core.model.ConnectionResult> =
        KomgaResult.Success(
            dev.komrd.core.model.ConnectionResult
                .Authenticated(),
        )

    override suspend fun pinCertificate(
        serverId: String,
        certificate: dev.komrd.core.common.error.CertificateInfo,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<java.security.cert.X509Certificate>,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean = false

    override fun certificateInfoOf(error: KomgaError): dev.komrd.core.common.error.CertificateInfo? = null
}

private class FakeEpubRepository(
    private val manifestResult: () -> KomgaResult<EpubManifest>,
    private val progressionResult: () -> KomgaResult<EpubLocator?> = { KomgaResult.Success<EpubLocator?>(null) },
) : EpubRepository {
    override suspend fun loadManifest(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubManifest> = manifestResult()

    override suspend fun loadResource(
        server: Server,
        bookId: String,
        href: String,
    ): KomgaResult<ByteArray> = KomgaResult.Success(ByteArray(0))

    override suspend fun loadPositions(
        server: Server,
        bookId: String,
    ): KomgaResult<List<EpubLocator>> = KomgaResult.Success(emptyList())

    override suspend fun loadProgression(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubLocator?> = progressionResult()
}

internal class FakeEpubProgressSyncEngine : EpubProgressSyncEngine {
    data class SyncCall(
        val server: Server,
        val bookId: String,
        val locator: EpubLocator,
    )

    val syncCalls = mutableListOf<SyncCall>()
    val flushCalls = mutableListOf<String>()

    override suspend fun sync(
        server: Server,
        bookId: String,
        locator: EpubLocator,
    ): KomgaResult<Unit> {
        syncCalls.add(SyncCall(server, bookId, locator))
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

internal class FakePrefetchController : PrefetchController {
    override val state: StateFlow<PrefetchState> = MutableStateFlow(PrefetchState.Idle)
    override val boundServerId: String? = null
    override val boundBookId: String? = null
    val pageChangedCalls = mutableListOf<Int>()

    override suspend fun start(
        server: Server,
        currentBook: dev.komrd.core.model.BookDetail,
        currentPageNumber: Int,
        nextBook: dev.komrd.core.model.NextBook?,
    ) = Unit

    override fun onPageChanged(currentPageNumber: Int) {
        pageChangedCalls.add(currentPageNumber)
    }

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

private class FakeNextBookResolver : NextBookResolver {
    override suspend fun resolve(
        server: Server,
        currentBookId: String,
        context: ReadingContext,
    ): KomgaResult<dev.komrd.core.model.NextBook?> = KomgaResult.Success(null)
}
