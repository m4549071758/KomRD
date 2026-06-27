package dev.komrd.feature.home

import androidx.paging.PagingData
import app.cash.turbine.test
import coil3.ImageLoader
import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.library.CollectionRepository
import dev.komrd.core.data.library.ImageLoaderProvider
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.data.library.ReadListRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Book
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Library
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.feature.library.GetServerLibraryGroupsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
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
    fun uiState_reflectsServers_andFallsBackToFirstWhenNoActive() =
        runTest(testDispatcher) {
            val viewModel =
                HomeViewModel(
                    serverRepository = FakeServerRepository(listOf(server("s1"), server("s2"))),
                    libraryRepository = FakeLibraryRepository(),
                    activeServerStore = FakeActiveServerStore(null),
                    imageLoaderProvider = FakeImageLoaderProvider,
                    getServerLibraryGroups = useCase(emptyList()),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals(listOf("s1", "s2"), state.servers.map { it.id })
                assertEquals("s1", state.selectedServer?.id)
                assertTrue(!state.noServer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_showsNoServer_whenEmpty() =
        runTest(testDispatcher) {
            val viewModel =
                HomeViewModel(
                    serverRepository = FakeServerRepository(emptyList()),
                    libraryRepository = FakeLibraryRepository(),
                    activeServerStore = FakeActiveServerStore(null),
                    imageLoaderProvider = FakeImageLoaderProvider,
                    getServerLibraryGroups = useCase(emptyList()),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertTrue(state.noServer)
                assertEquals(null, state.selectedServer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun onSelectServer_updatesSelectedServer_andActivates() =
        runTest(testDispatcher) {
            val activeStore = FakeActiveServerStore(null)
            val viewModel =
                HomeViewModel(
                    serverRepository = FakeServerRepository(listOf(server("s1"), server("s2"))),
                    libraryRepository = FakeLibraryRepository(),
                    activeServerStore = activeStore,
                    imageLoaderProvider = FakeImageLoaderProvider,
                    getServerLibraryGroups = useCase(emptyList()),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()

                viewModel.onSelectServer("s2")
                // onSelectServer は Main 上で即時 state 更新する
                val updated = awaitItem()
                assertEquals("s2", updated.selectedServer?.id)
                cancelAndIgnoreRemainingEvents()
            }
            // setActive は viewModelScope.launch で走るので TestScope を進める
            advanceUntilIdle()
            assertEquals("s2", activeStore.current)
        }

    @Test
    fun uiState_reflectsServerGroups_fromUseCase() =
        runTest(testDispatcher) {
            val servers = listOf(server("s1"), server("s2"))
            val serverRepo = FakeServerRepository(servers)
            val libraryRepo =
                FakeLibraryRepository(
                    librariesByServer =
                        mapOf(
                            "s1" to listOf(library("l1", "s1")),
                            "s2" to listOf(library("l2", "s2")),
                        ),
                )
            val viewModel =
                HomeViewModel(
                    serverRepository = serverRepo,
                    libraryRepository = libraryRepo,
                    activeServerStore = FakeActiveServerStore(null),
                    imageLoaderProvider = FakeImageLoaderProvider,
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = serverRepo,
                            libraryRepository = libraryRepo,
                            collectionRepository = FakeCollectionRepository(emptyMap()),
                            readListRepository = FakeReadListRepository(emptyMap()),
                        ),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                // serverGroups は refresh とは別 flow で到着するので、到達するまで待つ
                while (state.serverGroups.isEmpty()) state = awaitItem()
                assertEquals(listOf("s1", "s2"), state.serverGroups.map { it.server.id })
                assertEquals(listOf("l1"), state.serverGroups[0].libraries.map { it.id })
                assertEquals(listOf("l2"), state.serverGroups[1].libraries.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_serverGroups_containCollectionsAndReadLists() =
        runTest(testDispatcher) {
            val serverRepo = FakeServerRepository(listOf(server("s1")))
            val libraryRepo =
                FakeLibraryRepository(
                    librariesByServer = mapOf("s1" to listOf(library("l1", "s1"))),
                )
            val collectionRepo =
                FakeCollectionRepository(
                    mapOf("s1" to listOf(collection("s1", "col-1"))),
                )
            val readListRepo =
                FakeReadListRepository(
                    mapOf("s1" to listOf(readList("s1", "rl-1"))),
                )
            val viewModel =
                HomeViewModel(
                    serverRepository = serverRepo,
                    libraryRepository = libraryRepo,
                    activeServerStore = FakeActiveServerStore(null),
                    imageLoaderProvider = FakeImageLoaderProvider,
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = serverRepo,
                            libraryRepository = libraryRepo,
                            collectionRepository = collectionRepo,
                            readListRepository = readListRepo,
                        ),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.serverGroups.isEmpty()) state = awaitItem()
                assertEquals(1, state.serverGroups.size)
                assertEquals(listOf("l1"), state.serverGroups[0].libraries.map { it.id })
                assertEquals(listOf("col-1"), state.serverGroups[0].collections.map { it.id })
                assertEquals(listOf("rl-1"), state.serverGroups[0].readLists.map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun server(id: String) =
        Server(
            id = id,
            name = "Server $id",
            baseUrl = "https://$id.example",
            auth = AuthMethod.ApiKey("key"),
        )

    private fun library(
        id: String,
        serverId: String,
    ) = Library(id = id, serverId = serverId, name = "Library $id")

    private fun collection(
        serverId: String,
        collectionId: String,
    ) = Collection(
        id = collectionId,
        serverId = serverId,
        name = "Collection $collectionId",
        seriesCount = 0,
        thumbnailUrl = "",
    )

    private fun readList(
        serverId: String,
        readListId: String,
    ) = ReadListSummary(
        id = readListId,
        serverId = serverId,
        name = "ReadList $readListId",
        bookCount = 0,
        thumbnailUrl = "",
        summary = "",
    )

    /** 実装の[GetServerLibraryGroupsUseCase]をFakeリポジトリで構築する（空Library/Collection/ReadList）。 */
    private fun useCase(servers: List<Server>): GetServerLibraryGroupsUseCase =
        GetServerLibraryGroupsUseCase(
            serverRepository = FakeServerRepository(servers),
            libraryRepository = FakeLibraryRepository(),
            collectionRepository = FakeCollectionRepository(emptyMap()),
            readListRepository = FakeReadListRepository(emptyMap()),
        )
}

private class FakeServerRepository(
    servers: List<Server>,
) : ServerRepository {
    private val serversFlow = MutableStateFlow(servers)
    override val servers: Flow<List<Server>> = serversFlow

    override suspend fun byId(id: String): Server? = serversFlow.value.firstOrNull { it.id == id }

    override suspend fun add(server: Server) = error("unused")

    override suspend fun update(server: Server) = error("unused")

    override suspend fun delete(id: String) = error("unused")

    override suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult> = error("unused")

    override suspend fun pinCertificate(
        serverId: String,
        certificate: CertificateInfo,
    ): KomgaResult<Unit> = error("unused")

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<java.security.cert.X509Certificate>,
    ): KomgaResult<Unit> = error("unused")

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean = false

    override fun certificateInfoOf(error: KomgaError): CertificateInfo? = null
}

private class FakeActiveServerStore(
    initial: String?,
) : ActiveServerStore {
    private val idFlow = MutableStateFlow<String?>(initial)
    var current: String? = initial
        private set

    override val activeServerId: Flow<String?> = idFlow

    override suspend fun setActive(id: String) {
        current = id
        idFlow.value = id
    }

    override suspend fun clear() {
        current = null
        idFlow.value = null
    }
}

private class FakeLibraryRepository(
    private val librariesByServer: Map<String, List<Library>> = emptyMap(),
) : LibraryRepository {
    @Suppress("MaxLineLength")
    override suspend fun libraries(server: Server): KomgaResult<List<Library>> = KomgaResult.Success(librariesByServer[server.id].orEmpty())

    override fun seriesPager(
        server: Server,
        libraryId: String,
        sort: List<String>,
        readStatusFilter: dev.komrd.core.model.ReadStatusFilter,
    ): Flow<androidx.paging.PagingData<Series>> = flowOf(PagingData.empty())

    override fun booksPager(
        server: Server,
        seriesId: String,
        sort: List<String>,
    ): Flow<androidx.paging.PagingData<Book>> = flowOf(PagingData.empty())

    override fun readStatusBooksPager(
        server: Server,
        readStatus: String,
        sort: List<String>,
    ): Flow<androidx.paging.PagingData<Book>> = flowOf(PagingData.empty())
}

private class FakeCollectionRepository(
    private val collectionsByServer: Map<String, List<Collection>>,
) : CollectionRepository {
    override suspend fun collections(server: Server): KomgaResult<List<Collection>> =
        KomgaResult.Success(collectionsByServer[server.id].orEmpty())

    override fun seriesPager(
        server: Server,
        collectionId: String,
    ): Flow<androidx.paging.PagingData<Series>> = flowOf(PagingData.empty())
}

private class FakeReadListRepository(
    private val readListsByServer: Map<String, List<ReadListSummary>>,
) : ReadListRepository {
    override suspend fun readLists(server: Server): KomgaResult<List<ReadListSummary>> =
        KomgaResult.Success(readListsByServer[server.id].orEmpty())

    override fun booksPager(
        server: Server,
        readListId: String,
    ): Flow<androidx.paging.PagingData<Book>> = flowOf(PagingData.empty())
}

private object FakeImageLoaderProvider : ImageLoaderProvider {
    override fun forServer(server: Server): ImageLoader = error("unused in ViewModel logic test")
}
