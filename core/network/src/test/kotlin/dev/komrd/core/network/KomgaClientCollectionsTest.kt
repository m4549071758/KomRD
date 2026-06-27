package dev.komrd.core.network

import dev.komrd.core.common.error.KomgaError
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaClientCollectionsTest {
    @Test
    fun listCollections_parsesContent_andHitsCollectionsPath_withDefaultPageSize() =
        runTest {
            withServer { server ->
                server.enqueueJson(COLLECTION_PAGE_JSON)
                val client = client(server)

                val result = client.listCollections()

                assertTrue(result is KomgaResult.Success)
                val collections = (result as KomgaResult.Success).value
                assertEquals(listOf("c1", "c2"), collections.map { it.id })
                assertEquals("My Col 1", collections.first().name)
                val request = server.takeRequest()
                assertEquals("/api/v1/collections", request.url.encodedPath)
                assertEquals("0", request.url.queryParameter("page"))
                assertEquals("500", request.url.queryParameter("size"))
                assertNull(request.url.queryParameter("sort"))
            }
        }

    @Test
    fun listCollections_withSort_sendsSortQuery() =
        runTest {
            withServer { server ->
                server.enqueueJson(COLLECTION_PAGE_JSON)
                val client = client(server)

                client.listCollections(page = 1, size = 100, sort = "name,asc")

                val request = server.takeRequest()
                assertEquals("1", request.url.queryParameter("page"))
                assertEquals("100", request.url.queryParameter("size"))
                assertEquals("name,asc", request.url.queryParameter("sort"))
            }
        }

    @Test
    fun getCollection_parsesFields_andHitsCollectionPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(COLLECTION_DETAIL_JSON)
                val client = client(server)

                val result = client.getCollection("c1")

                assertTrue(result is KomgaResult.Success)
                val collection = (result as KomgaResult.Success).value
                assertEquals("c1", collection.id)
                assertEquals("My Col", collection.name)
                assertEquals(false, collection.ordered)
                assertEquals(listOf("s1", "s2"), collection.seriesIds)
                assertEquals("/api/v1/collections/c1", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun listCollectionSeries_sendsPageAndSize() =
        runTest {
            withServer { server ->
                server.enqueueJson(COLLECTION_SERIES_PAGE_JSON)
                val client = client(server)

                val result = client.listCollectionSeries("c1", page = 0, size = 20)

                assertTrue(result is KomgaResult.Success)
                val page = (result as KomgaResult.Success).value
                assertEquals(1, page.content.size)
                assertEquals("series-1", page.content.first().id)
                val request = server.takeRequest()
                assertEquals("/api/v1/collections/c1/series", request.url.encodedPath)
                assertEquals("0", request.url.queryParameter("page"))
                assertEquals("20", request.url.queryParameter("size"))
            }
        }

    @Test
    fun listReadLists_parsesContent_andHitsReadListsPath_withDefaultPageSize() =
        runTest {
            withServer { server ->
                server.enqueueJson(READLIST_PAGE_JSON)
                val client = client(server)

                val result = client.listReadLists()

                assertTrue(result is KomgaResult.Success)
                val lists = (result as KomgaResult.Success).value
                assertEquals(listOf("r1", "r2"), lists.map { it.id })
                assertEquals("My ReadList", lists.first().name)
                assertEquals("", lists.first().summary)
                val request = server.takeRequest()
                assertEquals("/api/v1/readlists", request.url.encodedPath)
                assertEquals("0", request.url.queryParameter("page"))
                assertEquals("500", request.url.queryParameter("size"))
            }
        }

    @Test
    fun getReadList_parsesFields_andHitsReadListPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(READLIST_DETAIL_JSON)
                val client = client(server)

                val result = client.getReadList("r1")

                assertTrue(result is KomgaResult.Success)
                val list = (result as KomgaResult.Success).value
                assertEquals("r1", list.id)
                assertEquals("My ReadList", list.name)
                assertEquals("Some summary", list.summary)
                assertEquals(true, list.ordered)
                assertEquals(listOf("b1", "b2"), list.bookIds)
                assertEquals("/api/v1/readlists/r1", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun listReadListBooksPaged_sendsPageAndSize() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_PAGE_JSON)
                val client = client(server)

                val result = client.listReadListBooksPaged("r1", page = 2, size = 50)

                assertTrue(result is KomgaResult.Success)
                val page = (result as KomgaResult.Success).value
                assertEquals(1, page.content.size)
                assertEquals("book-1", page.content.first().id)
                val request = server.takeRequest()
                assertEquals("/api/v1/readlists/r1/books", request.url.encodedPath)
                assertEquals("2", request.url.queryParameter("page"))
                assertEquals("50", request.url.queryParameter("size"))
            }
        }

    @Test
    fun listCollections_unauthorized_returnsUnauthorizedError() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val client = client(server)

                val result = client.listCollections()

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
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
        val COLLECTION_PAGE_JSON =
            """
            {
              "content": [
                {"id": "c1", "name": "My Col 1", "ordered": false, "filtered": false, "seriesIds": []},
                {"id": "c2", "name": "My Col 2", "ordered": true, "filtered": false, "seriesIds": ["s1"]}
              ],
              "number": 0,
              "size": 500,
              "totalElements": 2,
              "totalPages": 1,
              "last": true
            }
            """.trimIndent()

        val COLLECTION_DETAIL_JSON =
            """
            {
              "id": "c1",
              "name": "My Col",
              "ordered": false,
              "filtered": false,
              "seriesIds": ["s1", "s2"]
            }
            """.trimIndent()

        val COLLECTION_SERIES_PAGE_JSON =
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

        val READLIST_PAGE_JSON =
            """
            {
              "content": [
                {"id": "r1", "name": "My ReadList", "summary": "", "ordered": false, "filtered": false, "bookIds": []},
                {"id": "r2", "name": "Other", "summary": "", "ordered": true, "filtered": false, "bookIds": ["b1"]}
              ],
              "number": 0,
              "size": 500,
              "totalElements": 2,
              "totalPages": 1,
              "last": true
            }
            """.trimIndent()

        val READLIST_DETAIL_JSON =
            """
            {
              "id": "r1",
              "name": "My ReadList",
              "summary": "Some summary",
              "ordered": true,
              "filtered": false,
              "bookIds": ["b1", "b2"]
            }
            """.trimIndent()

        val BOOK_PAGE_JSON =
            """
            {
              "content": [
                {"id": "book-1", "seriesId": "series-1", "name": "Book One", "metadata": {"number": "1"}}
              ],
              "number": 2,
              "size": 50,
              "totalElements": 1,
              "totalPages": 1,
              "last": true
            }
            """.trimIndent()
    }
}
