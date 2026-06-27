package dev.komrd.core.data.library

import androidx.paging.PagingSource
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.error.KomgaException
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookPagingSourceTest {
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
    fun load_success_mapsToDomain_andComputesThumbnailUrl_andNextKey() =
        runTest {
            server.enqueueJson(bookPageJson(last = false))

            val result = source().load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            val book = page.data.single()
            assertEquals("book-1", book.id)
            assertEquals("s1", book.serverId)
            assertEquals("series-1", book.seriesId)
            assertEquals("Book One", book.name)
            assertTrue(book.thumbnailUrl.endsWith("/api/v1/books/book-1/thumbnail"))
            assertNull(page.prevKey)
            assertEquals(1, page.nextKey)
        }

    @Test
    fun load_lastPage_hasNoNextKey() =
        runTest {
            server.enqueueJson(bookPageJson(last = true))

            val result = source().load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            assertNull((result as PagingSource.LoadResult.Page).nextKey)
        }

    @Test
    fun load_unauthorized_returnsErrorWithKomgaException() =
        runTest {
            server.enqueue(MockResponse.Builder().code(401).build())

            val result = source().load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Error)
            val error = (result as PagingSource.LoadResult.Error).throwable
            assertTrue(error is KomgaException)
            assertTrue((error as KomgaException).error is KomgaError.Unauthorized)
        }

    private fun source(): BookPagingSource {
        val srv =
            Server(
                id = "s1",
                name = "Home",
                baseUrl = server.url("/").toString(),
                auth = AuthMethod.ApiKey("api-key-1"),
            )
        val trustStore = InMemoryServerTrustStore()
        val factory = KomgaClientFactory(trustStore = trustStore)
        return BookPagingSource(
            client = factory.clientFor(srv),
            server = srv,
            seriesId = "series-1",
            sort = LibraryRepository.DEFAULT_BOOK_SORT,
            trustStore = trustStore,
        )
    }

    private fun refreshParams() =
        PagingSource.LoadParams.Refresh<Int>(
            key = null,
            loadSize = 20,
            placeholdersEnabled = false,
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

    private fun bookPageJson(last: Boolean) =
        """
        {
          "content": [
            {"id": "book-1", "seriesId": "series-1", "name": "Book One", "metadata": {"number": "1"}}
          ],
          "number": 0,
          "size": 20,
          "totalElements": 1,
          "totalPages": ${if (last) 1 else 2},
          "last": $last
        }
        """.trimIndent()
}
