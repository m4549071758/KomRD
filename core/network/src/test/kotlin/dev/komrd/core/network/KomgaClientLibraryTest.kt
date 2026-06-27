package dev.komrd.core.network

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaClientLibraryTest {
    @Test
    fun listLibraries_parsesArray_andHitsLibrariesPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(LIBRARIES_JSON)
                val client = client(server)

                val result = client.listLibraries()

                assertTrue(result is KomgaResult.Success)
                val libraries = (result as KomgaResult.Success).value
                assertEquals(listOf("lib-1", "lib-2"), libraries.map { it.id })
                assertEquals("Manga", libraries.first().name)
                assertEquals("/api/v1/libraries", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun listSeriesInLibrary_sendsLibraryCondition_andSortQuery() =
        runTest {
            withServer { server ->
                server.enqueueJson(SERIES_PAGE_JSON)
                val client = client(server)

                val result =
                    client.listSeriesInLibrary(
                        libraryId = "lib-1",
                        page = 0,
                        size = 20,
                        sort = listOf("metadata.titleSort,asc"),
                    )

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                assertEquals("/api/v1/series/list", request.url.encodedPath)
                assertEquals("metadata.titleSort,asc", request.url.queryParameter("sort"))
                val body = request.body?.utf8().orEmpty()
                assertTrue("condition should reference libraryId: $body", body.contains("libraryId"))
                assertTrue("condition should reference the id: $body", body.contains("lib-1"))
            }
        }

    @Test
    fun listBooksInSeries_sendsSeriesCondition() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_PAGE_JSON)
                val client = client(server)

                val result = client.listBooksInSeries(seriesId = "series-1", page = 0, size = 20)

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                assertEquals("/api/v1/books/list", request.url.encodedPath)
                val body = request.body?.utf8().orEmpty()
                assertTrue("condition should reference seriesId: $body", body.contains("seriesId"))
                assertTrue("condition should reference the id: $body", body.contains("series-1"))
            }
        }

    private fun client(server: MockWebServer) =
        KomgaClientFactory(
            sessionStore = InMemorySessionStore(),
            trustStore = InMemoryServerTrustStore(),
        ).clientFor(
            Server(
                id = "s1",
                name = "Home",
                baseUrl = server.url("/").toString(),
                auth = AuthMethod.ApiKey("api-key-1"),
            ),
        )

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val server = MockWebServer()
        try {
            server.start()
            block(server)
        } finally {
            server.close()
        }
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
        val LIBRARIES_JSON =
            """
            [
              {"id": "lib-1", "name": "Manga", "unknown": "ignored"},
              {"id": "lib-2", "name": "Comics"}
            ]
            """.trimIndent()

        val SERIES_PAGE_JSON =
            """
            {
              "content": [
                {"id": "series-1", "libraryId": "lib-1", "name": "Series One", "metadata": {"title": "Series One"}}
              ],
              "number": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1,
              "last": true
            }
            """.trimIndent()

        val BOOK_PAGE_JSON =
            """
            {
              "content": [
                {"id": "book-1", "seriesId": "series-1", "name": "Book One", "metadata": {"number": "1"}}
              ],
              "number": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1,
              "last": true
            }
            """.trimIndent()
    }
}
