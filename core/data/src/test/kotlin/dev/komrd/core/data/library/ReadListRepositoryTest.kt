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

class ReadListRepositoryTest {
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
    fun readLists_parsesAndMapsToDomain() =
        runTest {
            server.enqueueJson(READLISTS_JSON)
            val repo = repository()

            val result = repo.readLists(srv())

            assertTrue(result is KomgaResult.Success)
            val lists = (result as KomgaResult.Success).value
            assertEquals(listOf("r1", "r2"), lists.map { it.id })
            assertEquals("My ReadList", lists.first().name)
            assertEquals(2, lists.first().bookCount)
            assertEquals("Some summary", lists.first().summary)
            assertTrue(lists.first().thumbnailUrl.endsWith("/api/v1/readlists/r1/thumbnail"))
            assertEquals("/api/v1/readlists", server.takeRequest().url.encodedPath)
        }

    private fun srv() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = server.url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun repository(): ReadListRepository {
        val trustStore = InMemoryServerTrustStore()
        return ReadListRepositoryImpl(
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
        val READLISTS_JSON =
            """
            {
              "content": [
                {"id": "r1", "name": "My ReadList", "summary": "Some summary", "ordered": true, "filtered": false, "bookIds": ["b1", "b2"]},
                {"id": "r2", "name": "Other", "summary": "", "ordered": false, "filtered": false, "bookIds": ["b3"]}
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
