package dev.komrd.core.data.prefetch

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NextBookResolverTest {
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
    fun resolve_series_nextBookExists_returnsNextByNumberSort() =
        runTest {
            server.enqueueJson(
                bookPageJson(
                    last = true,
                    books =
                        listOf(
                            bookJson("book-1", pagesCount = 3),
                            bookJson("book-2", pagesCount = 5),
                            bookJson("book-3", pagesCount = 7),
                        ),
                ),
            )

            val result = resolver().resolve(server(), "book-1", ReadingContext.Series("series-1"))

            assertTrue(result is KomgaResult.Success)
            val next = (result as KomgaResult.Success).value
            assertEquals("book-2", next?.bookId)
            assertEquals(5, next?.pagesCount)
            // Series内Book一覧はnumberSort昇順で取得(POST /api/v1/books/list)。
            assertEquals("/api/v1/books/list", server.takeRequest().url.encodedPath)
        }

    @Test
    fun resolve_series_currentIsLast_returnsNull() =
        runTest {
            server.enqueueJson(
                bookPageJson(
                    last = true,
                    books = listOf(bookJson("book-1", pagesCount = 3), bookJson("book-2", pagesCount = 5)),
                ),
            )

            val result = resolver().resolve(server(), "book-2", ReadingContext.Series("series-1"))

            assertTrue(result is KomgaResult.Success)
            assertNull((result as KomgaResult.Success).value)
        }

    @Test
    fun resolve_series_currentNotInList_returnsNull() =
        runTest {
            server.enqueueJson(
                bookPageJson(last = true, books = listOf(bookJson("book-1", pagesCount = 3))),
            )

            val result = resolver().resolve(server(), "book-9", ReadingContext.Series("series-1"))

            assertTrue(result is KomgaResult.Success)
            assertNull((result as KomgaResult.Success).value)
        }

    @Test
    fun resolve_series_pagesCountMissing_fallsBackToGetBook() =
        runTest {
            // 一覧の次Book(book-2)にpagesCount無し → getBookでフォールバック。
            server.enqueueJson(
                bookPageJson(
                    last = true,
                    books = listOf(bookJson("book-1", pagesCount = 3), bookJson("book-2", pagesCount = null)),
                ),
            )
            server.enqueueJson("""{"id":"book-2","name":"Book Two","media":{"pagesCount":4}}""")

            val result = resolver().resolve(server(), "book-1", ReadingContext.Series("series-1"))

            assertTrue(result is KomgaResult.Success)
            val next = (result as KomgaResult.Success).value
            assertEquals("book-2", next?.bookId)
            assertEquals(4, next?.pagesCount)
            val requests = listOf(server.takeRequest(), server.takeRequest())
            assertEquals("/api/v1/books/book-2", requests[1].url.encodedPath)
        }

    @Test
    fun resolve_readList_nextBookExists_returnsNextInReadListOrder() =
        runTest {
            server.enqueueJson(
                "[${bookJson("book-a", pagesCount = 2)},${bookJson("book-b", pagesCount = 6)}]",
            )

            val result = resolver().resolve(server(), "book-a", ReadingContext.ReadList("rl-1"))

            assertTrue(result is KomgaResult.Success)
            val next = (result as KomgaResult.Success).value
            assertEquals("book-b", next?.bookId)
            assertEquals(6, next?.pagesCount)
            assertEquals("/api/v1/readlists/rl-1/books", server.takeRequest().url.encodedPath)
        }

    @Test
    fun resolve_readList_currentIsLast_returnsNull() =
        runTest {
            server.enqueueJson("[${bookJson("book-a", pagesCount = 2)}]")

            val result = resolver().resolve(server(), "book-a", ReadingContext.ReadList("rl-1"))

            assertTrue(result is KomgaResult.Success)
            assertNull((result as KomgaResult.Success).value)
        }

    @Test
    fun resolve_series_unauthorized_returnsFailure() =
        runTest {
            server.enqueue(MockResponse.Builder().code(401).build())

            val result = resolver().resolve(server(), "book-1", ReadingContext.Series("series-1"))

            assertTrue(result is KomgaResult.Failure)
            assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
        }

    private fun resolver(): NextBookResolver =
        NextBookResolverImpl(
            clientFactory = KomgaClientFactory(trustStore = InMemoryServerTrustStore()),
            trustStore = InMemoryServerTrustStore(),
        )

    private fun server(): Server =
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

    private fun bookPageJson(
        last: Boolean,
        books: List<String>,
    ): String =
        """
        {
          "content": [${books.joinToString(",")}],
          "number": 0,
          "size": 100,
          "totalElements": ${books.size},
          "totalPages": 1,
          "last": $last
        }
        """.trimIndent()

    private fun bookJson(
        id: String,
        pagesCount: Int?,
    ): String =
        if (pagesCount != null) {
            """{"id":"$id","name":"$id","metadata":{"number":"1"},"media":{"pagesCount":$pagesCount}}"""
        } else {
            """{"id":"$id","name":"$id","metadata":{"number":"1"}}"""
        }
}
