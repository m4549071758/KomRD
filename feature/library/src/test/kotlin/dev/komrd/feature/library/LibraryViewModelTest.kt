package dev.komrd.feature.library

import androidx.lifecycle.SavedStateHandle
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
import dev.komrd.core.datastore.LibraryFilterStore
import dev.komrd.core.datastore.LibraryFilters
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Book
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Library
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.Series
import dev.komrd.core.model.SeriesSort
import dev.komrd.core.model.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
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
    fun uiState_initialSelectionPrefersRouteArgs_overActiveAndFirst() =
        runTest(testDispatcher) {
            val viewModel =
                LibraryViewModel(
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = FakeServerRepository(listOf(server("s1"), server("s2"))),
                            libraryRepository =
                                FakeLibraryRepository(
                                    librariesByServer =
                                        mapOf(
                                            "s1" to listOf(library("s1", "lib-a"), library("s1", "lib-b")),
                                            "s2" to listOf(library("s2", "lib-c")),
                                        ),
                                ),
                            collectionRepository = FakeCollectionRepository(emptyMap()),
                            readListRepository = FakeReadListRepository(emptyMap()),
                        ),
                    libraryRepository =
                        FakeLibraryRepository(
                            librariesByServer =
                                mapOf(
                                    "s1" to listOf(library("s1", "lib-a"), library("s1", "lib-b")),
                                    "s2" to listOf(library("s2", "lib-c")),
                                ),
                        ),
                    activeServerStore = FakeActiveServerStore(initial = "s1"),
                    libraryFilterStore = FakeLibraryFilterStore(),
                    imageLoaders = FakeImageLoaderProvider,
                    savedStateHandle = SavedStateHandle(mapOf("serverId" to "s2", "libraryId" to "lib-c")),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals("s2", state.selectedServer?.id)
                assertEquals("lib-c", state.selectedLibrary?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_fallsBackToActiveServer_whenNoRouteArgs() =
        runTest(testDispatcher) {
            val viewModel =
                LibraryViewModel(
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = FakeServerRepository(listOf(server("s1"), server("s2"))),
                            libraryRepository =
                                FakeLibraryRepository(
                                    librariesByServer =
                                        mapOf(
                                            "s1" to listOf(library("s1", "lib-a")),
                                            "s2" to listOf(library("s2", "lib-c")),
                                        ),
                                ),
                            collectionRepository = FakeCollectionRepository(emptyMap()),
                            readListRepository = FakeReadListRepository(emptyMap()),
                        ),
                    libraryRepository =
                        FakeLibraryRepository(
                            librariesByServer =
                                mapOf(
                                    "s1" to listOf(library("s1", "lib-a")),
                                    "s2" to listOf(library("s2", "lib-c")),
                                ),
                        ),
                    activeServerStore = FakeActiveServerStore(initial = "s2"),
                    libraryFilterStore = FakeLibraryFilterStore(),
                    imageLoaders = FakeImageLoaderProvider,
                    savedStateHandle = SavedStateHandle(),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals("s2", state.selectedServer?.id)
                assertEquals("lib-c", state.selectedLibrary?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_showsNoServer_whenEmpty() =
        runTest(testDispatcher) {
            val viewModel =
                LibraryViewModel(
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = FakeServerRepository(emptyList()),
                            libraryRepository = FakeLibraryRepository(emptyMap()),
                            collectionRepository = FakeCollectionRepository(emptyMap()),
                            readListRepository = FakeReadListRepository(emptyMap()),
                        ),
                    libraryRepository = FakeLibraryRepository(emptyMap()),
                    activeServerStore = FakeActiveServerStore(initial = null),
                    libraryFilterStore = FakeLibraryFilterStore(),
                    imageLoaders = FakeImageLoaderProvider,
                    savedStateHandle = SavedStateHandle(),
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
    fun uiState_recordsLibraryFetchError_inServerGroup() =
        runTest(testDispatcher) {
            val viewModel =
                LibraryViewModel(
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = FakeServerRepository(listOf(server("s1"))),
                            libraryRepository =
                                FakeLibraryRepository(
                                    librariesByServer = emptyMap(),
                                    errorByServer = mapOf("s1" to KomgaError.Network("net")),
                                ),
                            collectionRepository = FakeCollectionRepository(emptyMap()),
                            readListRepository = FakeReadListRepository(emptyMap()),
                        ),
                    libraryRepository =
                        FakeLibraryRepository(
                            librariesByServer = emptyMap(),
                            errorByServer = mapOf("s1" to KomgaError.Network("net")),
                        ),
                    activeServerStore = FakeActiveServerStore(initial = null),
                    libraryFilterStore = FakeLibraryFilterStore(),
                    imageLoaders = FakeImageLoaderProvider,
                    savedStateHandle = SavedStateHandle(),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals(1, state.serverGroups.size)
                assertTrue(state.serverGroups.first().error is KomgaError.Network)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_exposesCollectionsAndReadLists_inServerGroups() =
        runTest(testDispatcher) {
            val collections = mapOf("s1" to listOf(collection("s1", "col-1")))
            val readLists = mapOf("s1" to listOf(readList("s1", "rl-1")))
            val viewModel =
                LibraryViewModel(
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = FakeServerRepository(listOf(server("s1"))),
                            libraryRepository =
                                FakeLibraryRepository(
                                    librariesByServer = mapOf("s1" to listOf(library("s1", "lib-a"))),
                                ),
                            collectionRepository = FakeCollectionRepository(collections),
                            readListRepository = FakeReadListRepository(readLists),
                        ),
                    libraryRepository =
                        FakeLibraryRepository(
                            librariesByServer = mapOf("s1" to listOf(library("s1", "lib-a"))),
                        ),
                    activeServerStore = FakeActiveServerStore(initial = null),
                    libraryFilterStore = FakeLibraryFilterStore(),
                    imageLoaders = FakeImageLoaderProvider,
                    savedStateHandle = SavedStateHandle(),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals(1, state.serverGroups.size)
                assertEquals(
                    1,
                    state.serverGroups
                        .first()
                        .collections.size,
                )
                assertEquals(
                    "col-1",
                    state.serverGroups
                        .first()
                        .collections
                        .first()
                        .id,
                )
                assertEquals(
                    1,
                    state.serverGroups
                        .first()
                        .readLists.size,
                )
                assertEquals(
                    "rl-1",
                    state.serverGroups
                        .first()
                        .readLists
                        .first()
                        .id,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun uiState_restoresPersistedSortAndFilter_forSelectedLibrary() =
        runTest(testDispatcher) {
            val filterStore =
                FakeLibraryFilterStore(
                    filtersByLibrary =
                        mapOf("lib-a" to LibraryFilters(SeriesSort.DATE_ADDED_DESC, ReadStatusFilter.READ)),
                )
            val viewModel =
                LibraryViewModel(
                    getServerLibraryGroups =
                        GetServerLibraryGroupsUseCase(
                            serverRepository = FakeServerRepository(listOf(server("s1"))),
                            libraryRepository =
                                FakeLibraryRepository(
                                    librariesByServer = mapOf("s1" to listOf(library("s1", "lib-a"))),
                                ),
                            collectionRepository = FakeCollectionRepository(emptyMap()),
                            readListRepository = FakeReadListRepository(emptyMap()),
                        ),
                    libraryRepository =
                        FakeLibraryRepository(
                            librariesByServer = mapOf("s1" to listOf(library("s1", "lib-a"))),
                        ),
                    activeServerStore = FakeActiveServerStore(initial = null),
                    libraryFilterStore = filterStore,
                    imageLoaders = FakeImageLoaderProvider,
                    savedStateHandle = SavedStateHandle(),
                )

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals(SeriesSort.DATE_ADDED_DESC, state.currentSort)
                assertEquals(ReadStatusFilter.READ, state.readStatusFilter)
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
        serverId: String,
        libraryId: String,
    ) = Library(
        id = libraryId,
        serverId = serverId,
        name = "Library $libraryId",
    )

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
    private val librariesByServer: Map<String, List<Library>>,
    private val errorByServer: Map<String, KomgaError> = emptyMap(),
) : LibraryRepository {
    override suspend fun libraries(server: Server): KomgaResult<List<Library>> {
        errorByServer[server.id]?.let { return KomgaResult.Failure(it) }
        return KomgaResult.Success(librariesByServer[server.id].orEmpty())
    }

    override fun seriesPager(
        server: Server,
        libraryId: String,
        sort: List<String>,
        readStatusFilter: ReadStatusFilter,
    ): Flow<PagingData<Series>> = flowOf(PagingData.empty())

    override fun booksPager(
        server: Server,
        seriesId: String,
        sort: List<String>,
    ): Flow<PagingData<Book>> = flowOf(PagingData.empty())

    override fun readStatusBooksPager(
        server: Server,
        readStatus: String,
        sort: List<String>,
    ): Flow<PagingData<Book>> = flowOf(PagingData.empty())
}

private class FakeCollectionRepository(
    private val collectionsByServer: Map<String, List<Collection>>,
) : CollectionRepository {
    override suspend fun collections(server: Server): KomgaResult<List<Collection>> =
        KomgaResult.Success(collectionsByServer[server.id].orEmpty())

    override fun seriesPager(
        server: Server,
        collectionId: String,
    ): Flow<PagingData<Series>> = flowOf(PagingData.empty())
}

private class FakeReadListRepository(
    private val readListsByServer: Map<String, List<ReadListSummary>>,
) : ReadListRepository {
    override suspend fun readLists(server: Server): KomgaResult<List<ReadListSummary>> =
        KomgaResult.Success(readListsByServer[server.id].orEmpty())

    override fun booksPager(
        server: Server,
        readListId: String,
    ): Flow<PagingData<Book>> = flowOf(PagingData.empty())
}

private class FakeLibraryFilterStore(
    private val filtersByLibrary: Map<String, LibraryFilters> = emptyMap(),
) : LibraryFilterStore {
    override fun filters(libraryId: String): Flow<LibraryFilters> {
        val filters = filtersByLibrary[libraryId] ?: LibraryFilters.DEFAULT
        return flowOf(filters)
    }

    override suspend fun setSort(
        libraryId: String,
        sort: SeriesSort,
    ): Unit = error("unused")

    override suspend fun setReadStatusFilter(
        libraryId: String,
        filter: ReadStatusFilter,
    ): Unit = error("unused")
}

private object FakeImageLoaderProvider : ImageLoaderProvider {
    override fun forServer(server: Server): ImageLoader = error("unused in ViewModel logic test")
}
