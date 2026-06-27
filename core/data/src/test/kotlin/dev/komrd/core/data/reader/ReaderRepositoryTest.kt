package dev.komrd.core.data.reader

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
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

class ReaderRepositoryTest {
    @Test
    fun loadBook_success_mapsSeriesReadingDirectionAsLayer2() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_JSON)
                server.enqueueJson(PAGES_JSON)
                server.enqueueJson(SERIES_JSON)

                val result = repository().loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                val detail = (result as KomgaResult.Success).value
                assertEquals("book-1", detail.id)
                assertEquals("s1", detail.serverId)
                assertEquals("Book One", detail.name)
                assertEquals(3, detail.pagesCount)
                assertEquals(listOf(1, 2, 3), detail.pages.map { it.number })
                // ②=Series readingDirection（VERTICAL）。Book.metadata.readingDirectionは使わない。
                assertEquals(ReadingDirection.VERTICAL, detail.readingDirection)
            }
        }

    @Test
    fun loadBook_noSeriesId_fallsBackToNullDirection() =
        runTest {
            withServer { server ->
                server.enqueueJson("""{"id": "book-1", "name": "Book One"}""")
                server.enqueueJson("""[{"number": 1}]""")

                val result = repository().loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                assertNull((result as KomgaResult.Success).value.readingDirection)
            }
        }

    @Test
    fun loadBook_seriesFailure_stillSucceedsWithNullDirection() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_JSON)
                server.enqueueJson(PAGES_JSON)
                server.enqueue(MockResponse.Builder().code(500).build())

                val result = repository().loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                assertNull((result as KomgaResult.Success).value.readingDirection)
            }
        }

    @Test
    fun loadBook_pagesFailure_propagatesFailure() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_JSON)
                server.enqueue(MockResponse.Builder().code(500).build())

                val result = repository().loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Http)
            }
        }

    @Test
    fun loadBook_bookUnauthorized_propagatesUnauthorized() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                server.enqueueJson(PAGES_JSON)

                val result = repository().loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
            }
        }

    @Test
    fun loadBook_nullDimensions_resolvedViaResolver() =
        runTest {
            withServer { server ->
                server.enqueueJson(BOOK_JSON)
                // page 2 だけ寸法欠落(null)。
                server.enqueueJson(
                    """
                    [
                      {"number": 1, "width": 800, "height": 1200},
                      {"number": 2},
                      {"number": 3, "width": 1600, "height": 1200}
                    ]
                    """.trimIndent(),
                )
                server.enqueueJson(SERIES_JSON)

                val result =
                    repository(FakeDimensionResolver())
                        .loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                val pages = (result as KomgaResult.Success).value.pages
                assertEquals(3, pages.size)
                // page 1, 3 はmetadataのまま。page 2 はresolverが埋めた実寸。
                assertEquals(800, pages[0].width)
                assertEquals(1200, pages[0].height)
                assertEquals(1000, pages[1].width)
                assertEquals(1500, pages[1].height)
                assertEquals(1600, pages[2].width)
                assertEquals(1200, pages[2].height)
            }
        }

    @Test
    fun loadBook_epub_returnsEmptyPagesWithoutFetchingPages() =
        runTest {
            withServer { server ->
                // EPUB: getBookのみenqueue( seriesId無し→getSeriesも呼ばない)。
                // getBookPagesが呼ばれなければ追加enqueue不要。
                server.enqueueJson(EPUB_BOOK_JSON)

                val result = repository().loadBook(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                val detail = (result as KomgaResult.Success).value
                assertEquals(0, detail.pagesCount)
                assertEquals(BookMediaProfile.EPUB, detail.mediaProfile)
                // getBookのみ発火(1リクエスト)。getBookPagesは呼ばれない。
                assertEquals(1, server.requestCount)
                assertEquals("/api/v1/books/book-1", server.takeRequest().url.encodedPath)
            }
        }

    private fun repository(resolver: PageDimensionResolver = NoOpPageDimensionResolver): ReaderRepository =
        ReaderRepositoryImpl(
            clientFactory =
                KomgaClientFactory(
                    sessionStore = InMemorySessionStore(),
                    trustStore = InMemoryServerTrustStore(),
                ),
            trustStore = InMemoryServerTrustStore(),
            dimensionResolver = resolver,
        )

    /** [MockWebServer] と同じベースURLを持つ [Server]。 */
    private fun MockWebServer.asServer(): Server =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private suspend fun withServer(block: suspend (MockWebServer) -> Unit) {
        val ws = MockWebServer()
        try {
            ws.start()
            block(ws)
        } finally {
            ws.close()
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

    /** null寸法ページを固定の実寸(1000x1500)で埋めるテスト用resolver。 */
    private class FakeDimensionResolver : PageDimensionResolver {
        override suspend fun resolve(
            client: dev.komrd.core.network.KomgaClient,
            bookId: String,
            page: dev.komrd.core.model.BookPage,
        ): dev.komrd.core.model.BookPage = page.copy(width = 1000, height = 1500)
    }

    private companion object {
        val BOOK_JSON =
            """
            {
              "id": "book-1", "seriesId": "series-1", "name": "Book One",
              "metadata": {"title": "Book One", "readingDirection": "RIGHT_TO_LEFT"},
              "media": {"pagesCount": 3}
            }
            """.trimIndent()

        val PAGES_JSON =
            """
            [
              {"number": 1, "width": 800, "height": 1200},
              {"number": 2, "width": 800, "height": 1200},
              {"number": 3, "width": 1600, "height": 1200}
            ]
            """.trimIndent()

        val SERIES_JSON =
            """
            {
              "id": "series-1", "name": "Series One",
              "metadata": {"title": "Series One", "readingDirection": "VERTICAL"}
            }
            """.trimIndent()

        // EPUB: mediaProfile=EPUB。seriesId無し→getSeries呼ばない。
        val EPUB_BOOK_JSON =
            """
            {
              "id": "book-1", "name": "EPUB Book",
              "metadata": {"title": "EPUB Book"},
              "media": {"pagesCount": 10, "mediaProfile": "EPUB", "mediaType": "application/epub+zip"}
            }
            """.trimIndent()
    }
}
