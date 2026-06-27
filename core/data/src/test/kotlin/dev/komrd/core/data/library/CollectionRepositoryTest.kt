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

class CollectionRepositoryTest {
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
    fun collections_parsesAndMapsToDomain() =
        runTest {
            server.enqueueJson(COLLECTIONS_JSON)
            val repo = repository()

            val result = repo.collections(srv())

            assertTrue(result is KomgaResult.Success)
            val collections = (result as KomgaResult.Success).value
            assertEquals(listOf("c1", "c2"), collections.map { it.id })
            assertEquals("My Col", collections.first().name)
            assertEquals(2, collections.first().seriesCount)
            assertTrue(collections.first().thumbnailUrl.endsWith("/api/v1/collections/c1/thumbnail"))
            assertEquals("/api/v1/collections", server.takeRequest().url.encodedPath)
        }

    private fun srv() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = server.url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun repository(): CollectionRepository {
        val trustStore = InMemoryServerTrustStore()
        return CollectionRepositoryImpl(
            clientFactory = KomgaClientFactory(trustStore = trustStore),
            trustStore = trustStore,
        )
    }

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

    private companion object {
        val COLLECTIONS_JSON =
            """
            {
              "content": [
                {"id": "c1", "name": "My Col", "ordered": false, "filtered": false, "seriesIds": ["s1", "s2"]},
                {"id": "c2", "name": "Other", "ordered": true, "filtered": false, "seriesIds": ["s3"]}
              ],
              "number": 0,
              "size": 500,
              "totalElements": 2,
              "totalPages": 1,
              "last": true
            }
            """.trimIndent()
    }
}
