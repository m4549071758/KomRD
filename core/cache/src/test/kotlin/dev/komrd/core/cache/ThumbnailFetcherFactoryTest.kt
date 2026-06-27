package dev.komrd.core.cache

import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.BookPageThumbnail
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ThumbnailFetcherFactoryTest {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.close()
    }

    @Test
    fun hit_returnsDiskSource() =
        runTest {
            val store = FakeThumbnailStore()
            store.makeAvailable("s1", "b1", 3)

            val fetcher = ThumbnailFetcher(BookPageThumbnail("s1", "b1", 3), store, null)
            val result = fetcher.fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals("image/jpeg", result.mimeType)
        }

    @Test
    fun miss_returnsNullForPlaceholder() =
        runTest {
            val store = FakeThumbnailStore()

            val fetcher = ThumbnailFetcher(BookPageThumbnail("s1", "b1", 5), store, null)
            val result = fetcher.fetch()

            // 画像系未命中(未取得ページ)はnull→Coilがプレースホルダ(ページ番号)表示へ。
            assertNull(result)
        }

    @Test
    fun pdfMiss_fetchesThumbnailEndpointAndWritesStore() =
        runTest {
            val body = "thumb-jpeg-bytes"
            server.enqueue(MockResponse.Builder().body(body).build())
            val store = FakeThumbnailStore()
            val url = server.url("/api/v1/books/b1/pages/3/thumbnail").toString()

            val fetcher =
                ThumbnailFetcher(
                    BookPageThumbnail("s1", "b1", 3, url, BookMediaProfile.PDF),
                    store,
                    OkHttpClient(),
                )
            val result = fetcher.fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.NETWORK, result.dataSource)
            assertEquals(1, server.requestCount)
            assertEquals("/api/v1/books/b1/pages/3/thumbnail", server.takeRequest().target)
            // Storeへ格納済(2回目はDISKで返る)。
            val cached = store.get("s1", "b1", 3)
            assertTrue(cached != null)
        }

    @Test
    fun pdfHit_returnsDiskWithoutNetwork() =
        runTest {
            val store = FakeThumbnailStore()
            store.makeAvailable("s1", "b1", 3)
            val url = server.url("/api/v1/books/b1/pages/3/thumbnail").toString()

            val fetcher =
                ThumbnailFetcher(
                    BookPageThumbnail("s1", "b1", 3, url, BookMediaProfile.PDF),
                    store,
                    OkHttpClient(),
                )
            val result = fetcher.fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals(0, server.requestCount)
        }
}
