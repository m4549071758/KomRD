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

class KomgaClientBookTest {
    @Test
    fun getBook_parsesMediaAndReadingDirection_andHitsBookPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_JSON)
                val client = client(server)

                val result = client.getBook("book-1")

                assertTrue(result is KomgaResult.Success)
                val book = (result as KomgaResult.Success).value
                assertEquals("book-1", book.id)
                assertEquals("Book One", book.name)
                assertEquals(3, book.media.pagesCount)
                assertEquals("DIVINA", book.media.mediaProfile)
                assertEquals("image/jpeg", book.media.mediaType)
                assertEquals("RIGHT_TO_LEFT", book.metadata.readingDirection)
                assertEquals("/api/v1/books/book-1", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun getBookPages_parsesNumberAndDimensions_andHitsPagesPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(PAGES_JSON)
                val client = client(server)

                val result = client.getBookPages("book-1")

                assertTrue(result is KomgaResult.Success)
                val pages = (result as KomgaResult.Success).value
                assertEquals(listOf(1, 2, 3), pages.map { it.number })
                assertEquals(800, pages.first().width)
                assertEquals(1200, pages.first().height)
                assertEquals(102400L, pages.first().sizeBytes)
                assertEquals("/api/v1/books/book-1/pages", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun getBook_withoutOptionalFields_defaultsToNulls() =
        runTest {
            withServer { server ->
                server.enqueueJson("""{"id": "book-1", "name": "Book One"}""")
                val client = client(server)

                val result = client.getBook("book-1")

                assertTrue(result is KomgaResult.Success)
                val book = (result as KomgaResult.Success).value
                assertNull(book.media.pagesCount)
                assertNull(book.metadata.readingDirection)
            }
        }

    @Test
    fun getBook_unauthorized_returnsUnauthorizedError() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val client = client(server)

                val result = client.getBook("book-1")

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
            }
        }

    @Test
    fun getSeries_parsesReadingDirection_andHitsSeriesPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(SERIES_JSON)
                val client = client(server)

                val result = client.getSeries("series-1")

                assertTrue(result is KomgaResult.Success)
                val series = (result as KomgaResult.Success).value
                assertEquals("series-1", series.id)
                assertEquals("Series One", series.name)
                assertEquals("VERTICAL", series.metadata.readingDirection)
                assertEquals("/api/v1/series/series-1", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun getSeries_unauthorized_returnsUnauthorizedError() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val client = client(server)

                val result = client.getSeries("series-1")

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
            }
        }

    @Test
    fun getBookPage_withConvert_jpeg_query_sendsConvertAndZeroBasedQueries() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .headers(Headers.headersOf("Content-Type", "image/jpeg"))
                        .body(PAGE_BYTES)
                        .build(),
                )
                val client = client(server)

                val result = client.getBookPage("book-1", 1, convert = "jpeg")

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                assertEquals("/api/v1/books/book-1/pages/1", request.url.encodedPath)
                assertEquals("jpeg", request.url.queryParameter("convert"))
                // zero_based は明示的に渡さなければクエリに出ない(nullだから)
                assertNull(request.url.queryParameter("zero_based"))
            }
        }

    @Test
    fun getBookPage_ifModifiedSince_header_sent() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .headers(Headers.headersOf("Content-Type", "image/jpeg"))
                        .body(PAGE_BYTES)
                        .build(),
                )
                val client = client(server)
                val ifModifiedSince = "Wed, 21 Oct 2026 07:28:00 GMT"

                val result = client.getBookPage("book-1", 1, ifModifiedSince = ifModifiedSince)

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                assertEquals(ifModifiedSince, request.headers["If-Modified-Since"])
            }
        }

    @Test
    fun getBookPage_defaultArgs_omitsConvertAndIfModifiedSince() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .headers(Headers.headersOf("Content-Type", "image/jpeg"))
                        .body(PAGE_BYTES)
                        .build(),
                )
                val client = client(server)

                client.getBookPage("book-1", 1)

                val request = server.takeRequest()
                assertNull(request.url.queryParameter("convert"))
                assertNull(request.headers["If-Modified-Since"])
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
        val BOOK_JSON =
            """
            {
              "id": "book-1",
              "seriesId": "series-1",
              "name": "Book One",
              "metadata": {"title": "Book One", "number": "1", "readingDirection": "RIGHT_TO_LEFT"},
              "media": {"pagesCount": 3, "mediaProfile": "DIVINA", "mediaType": "image/jpeg"}
            }
            """.trimIndent()

        val PAGES_JSON =
            """
            [
              {"number": 1, "fileName": "p1.jpg", "width": 800, "height": 1200, "mediaType": "image/jpeg", "sizeBytes": 102400},
              {"number": 2, "fileName": "p2.jpg", "width": 800, "height": 1200, "mediaType": "image/jpeg", "sizeBytes": 98304},
              {"number": 3, "fileName": "p3.jpg", "width": 1600, "height": 1200, "mediaType": "image/jpeg", "sizeBytes": 204800}
            ]
            """.trimIndent()

        val SERIES_JSON =
            """
            {
              "id": "series-1",
              "libraryId": "lib-1",
              "name": "Series One",
              "metadata": {"title": "Series One", "status": "ONGOING", "readingDirection": "VERTICAL"}
            }
            """.trimIndent()

        const val PAGE_BYTES = "fake-jpeg-bytes"
    }
}
