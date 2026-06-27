package dev.komrd.core.data.library

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LibraryRepositoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.close()
    }

    @Test
    fun libraries_success_mapsToDomainWithServerId() =
        runTest {
            server.enqueueJson(
                """[{"id":"lib-1","name":"Manga"},{"id":"lib-2","name":"Comics"}]""",
            )

            val result = repository().libraries(testServer())

            assertTrue(result is KomgaResult.Success)
            val libraries = (result as KomgaResult.Success).value
            assertEquals(listOf("lib-1", "lib-2"), libraries.map { it.id })
            assertTrue(libraries.all { it.serverId == "s1" })
        }

    @Test
    fun libraries_serverError_returnsFailure() =
        runTest {
            server.enqueue(MockResponse.Builder().code(500).build())

            val result = repository().libraries(testServer())

            assertTrue(result is KomgaResult.Failure)
        }

    private fun repository(): LibraryRepositoryImpl {
        val trustStore = InMemoryServerTrustStore()
        return LibraryRepositoryImpl(
            clientFactory = KomgaClientFactory(trustStore = trustStore),
            trustStore = trustStore,
        )
    }

    private fun testServer() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = server.url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun MockWebServer.enqueueJson(body: String) {
        enqueue(
            MockResponse
                .Builder()
                .code(200)
                .headers(Headers.headersOf("Content-Type", "application/json"))
                .body(body)
                .build(),
        )
    }
}
