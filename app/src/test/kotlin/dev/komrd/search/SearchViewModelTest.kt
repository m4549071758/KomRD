package dev.komrd.search

import androidx.paging.PagingData
import app.cash.turbine.test
import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.library.ImageLoaderProvider
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.data.search.SearchRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Book
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Library
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import dev.komrd.testing.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.security.cert.X509Certificate

class SearchViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun uiState_reflectsServers_andFallsBackToFirstWhenNoActive() =
        runTest {
            val viewModel = buildViewModel(servers = listOf(server("s1"), server("s2")))

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals(listOf("s1", "s2"), state.servers.map { it.id })
                assertEquals("s1", state.activeServer?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun onSelectServer_updatesActiveServer() =
        runTest {
            val activeStore = FakeActiveServerStore()
            val viewModel = buildViewModel(servers = listOf(server("s1"), server("s2")), active = activeStore)

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()

                viewModel.onSelectServer("s2")

                val updated = awaitItem()
                assertEquals("s2", updated.activeServerId)
                assertEquals("s2", updated.activeServer?.id)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun emptyServers_activeServerIsNull() =
        runTest {
            val viewModel = buildViewModel(servers = emptyList())

            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertTrue(state.servers.isEmpty())
                assertNull(state.activeServer)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun onQueryChanged_updatesQueryInState() =
        runTest {
            val viewModel = buildViewModel(servers = listOf(server("s1")))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                viewModel.onQueryChanged("naruto")
                assertEquals("naruto", awaitItem().query)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun onToggleGlobalAllServers_updatesFlag() =
        runTest {
            val viewModel = buildViewModel(servers = listOf(server("s1")))
            viewModel.uiState.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                viewModel.onToggleGlobalAllServers(true)
                assertTrue(awaitItem().globalAllServers)
                cancelAndIgnoreRemainingEvents()
            }
        }

    private fun buildViewModel(
        servers: List<Server>,
        active: FakeActiveServerStore = FakeActiveServerStore(),
    ): SearchViewModel =
        SearchViewModel(
            serverRepository = FakeServerRepository(servers),
            activeServerStore = active,
            libraryRepository = FakeLibraryRepository(),
            searchRepository = FakeSearchRepository(),
            imageLoaders = FakeImageLoaderProvider,
        )

    private fun server(id: String) =
        Server(
            id = id,
            name = "Server $id",
            baseUrl = "https://$id.example",
            auth = AuthMethod.ApiKey("key"),
        )
}

private class FakeServerRepository(
    servers: List<Server>,
) : ServerRepository {
    private val serversFlow = MutableStateFlow(servers)
    override val servers: Flow<List<Server>> = serversFlow

    override suspend fun byId(id: String): Server? = serversFlow.value.firstOrNull { it.id == id }

    override suspend fun add(server: Server) = Unit

    override suspend fun update(server: Server) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult> =
        KomgaResult.Failure(KomgaError.Unknown("not used in search tests"))

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

private class FakeActiveServerStore : ActiveServerStore {
    private val idFlow = MutableStateFlow<String?>(null)
    override val activeServerId: Flow<String?> = idFlow

    override suspend fun setActive(id: String) {
        idFlow.value = id
    }

    override suspend fun clear() {
        idFlow.value = null
    }
}

private class FakeLibraryRepository : LibraryRepository {
    override suspend fun libraries(server: Server): KomgaResult<List<Library>> = KomgaResult.Success(emptyList())

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

private class FakeSearchRepository :
    SearchRepository(
        clientFactory = KomgaClientFactory(trustStore = InMemoryServerTrustStore()),
        trustStore = InMemoryServerTrustStore(),
    )

private object FakeImageLoaderProvider : ImageLoaderProvider {
    override fun forServer(server: Server): coil3.ImageLoader = error("unused in ViewModel logic test")
}
