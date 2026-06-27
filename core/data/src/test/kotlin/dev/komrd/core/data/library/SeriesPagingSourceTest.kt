package dev.komrd.core.data.library

import androidx.paging.PagingSource
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.error.KomgaException
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.ReadStatusFilter
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

class SeriesPagingSourceTest {
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
            server.enqueueJson(seriesPageJson(last = false))
            val source = source()

            val result = source.load(refreshParams())

            assertTrue(result is PagingSource.LoadResult.Page)
            val page = result as PagingSource.LoadResult.Page
            val series = page.data.single()
            assertEquals("series-1", series.id)
            assertEquals("s1", series.serverId)
            assertEquals("Series One", series.name)
            assertTrue(series.thumbnailUrl.endsWith("/api/v1/series/series-1/thumbnail"))
            assertNull(page.prevKey)
            assertEquals(1, page.nextKey)
        }

    @Test
    fun load_lastPage_hasNoNextKey() =
        runTest {
            server.enqueueJson(seriesPageJson(last = true))

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

    @Test
    fun load_withAllFilter_sendsLibraryConditionOnly() =
        runTest {
            server.enqueueJson(seriesPageJson(last = true))
            source(filter = ReadStatusFilter.ALL).load(refreshParams())

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue("body should contain libraryId: $body", body.contains("libraryId"))
            assertTrue("body should contain lib-1: $body", body.contains("lib-1"))
            assertTrue("body should not contain readStatus for ALL: $body", !body.contains("readStatus"))
            assertTrue("body should not contain allOf for ALL: $body", !body.contains("allOf"))
        }

    @Test
    fun load_withUnreadFilter_sendsLibraryAndReadStatusInAllOf() =
        runTest {
            server.enqueueJson(seriesPageJson(last = true))
            source(filter = ReadStatusFilter.UNREAD).load(refreshParams())

            val body =
                server
                    .takeRequest()
                    .body
                    ?.utf8()
                    .orEmpty()
            assertTrue("body should contain allOf: $body", body.contains("allOf"))
            assertTrue("body should contain libraryId: $body", body.contains("libraryId"))
            assertTrue("body should contain readStatus: $body", body.contains("readStatus"))
            assertTrue("body should contain UNREAD value: $body", body.contains("UNREAD"))
        }

    private fun source(filter: ReadStatusFilter = ReadStatusFilter.ALL): SeriesPagingSource {
        val srv =
            Server(
                id = "s1",
                name = "Home",
                baseUrl = server.url("/").toString(),
                auth = AuthMethod.ApiKey("api-key-1"),
            )
        val trustStore = InMemoryServerTrustStore()
        val factory = KomgaClientFactory(trustStore = trustStore)
        return SeriesPagingSource(
            client = factory.clientFor(srv),
            server = srv,
            libraryId = "lib-1",
            sort = LibraryRepository.DEFAULT_SERIES_SORT,
            readStatusFilter = filter,
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

    private fun seriesPageJson(last: Boolean) =
        """
        {
          "content": [
            {"id": "series-1", "libraryId": "lib-1", "name": "Series One", "metadata": {"title": "Series One"}}
          ],
          "number": 0,
          "size": 20,
          "totalElements": 1,
          "totalPages": ${if (last) 1 else 2},
          "last": $last
        }
        """.trimIndent()
}
