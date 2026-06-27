package dev.komrd.core.cache

import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import dev.komrd.core.model.BookPageImage
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefetchFetcherFactoryTest {
    @get:Rule
    val temp = TemporaryFolder()

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

    private fun fetcher(store: PrefetchStore): PrefetchFetcher =
        PrefetchFetcher(BookPageImage("s1", "b1", 1, server.url("/p1").toString()), OkHttpClient(), store)

    private fun jpegFetcher(store: PrefetchStore): PrefetchFetcher =
        PrefetchFetcher(BookPageImage("s1", "b1", 1, server.url("/p1").toString(), "jpeg"), OkHttpClient(), store)

    @Test
    fun hit_returnsDiskSourceWithoutNetwork() =
        runTest {
            val store = FakePrefetchStore(temp.root)
            val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
            store.put("s1", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, PrefetchStore.VARIANT_FULL, png, etag = null)

            val result = fetcher(store).fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals("image/png", result.mimeType)
            // ネット呼出なし
            assertEquals(0, server.requestCount)
        }

    @Test
    fun miss_fetchesNetworkAndWritesStoreWithSameBytes() =
        runTest {
            val body = "page-bytes"
            server.enqueue(
                MockResponse
                    .Builder()
                    .body(body)
                    .build(),
            )
            val store = FakePrefetchStore(temp.root)

            val result = fetcher(store).fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.NETWORK, result.dataSource)
            assertEquals(1, server.requestCount)

            // Storeへ同一bytesが書かれ、SourceResultの実体と一致(同一ディスク実体)。
            val entry = store.get("s1", "b1", "1", PrefetchStore.VARIANT_FULL)
            assertNotNull(entry)
            val storedBytes = entry!!.file.readBytes()
            assertArrayEquals(body.toByteArray(), storedBytes)
            val resultFile = result.source.fileOrNull()?.toFile()
            assertNotNull(resultFile)
            assertArrayEquals(storedBytes, resultFile!!.readBytes())
        }

    @Test
    fun nonSuccessResponse_propagatesAsException() =
        runTest {
            server.enqueue(MockResponse.Builder().code(500).build())
            val store = FakePrefetchStore(temp.root)

            var thrown: Throwable? = null
            try {
                fetcher(store).fetch()
            } catch (t: Throwable) {
                thrown = t
            }
            assertNotNull(thrown)
            // 失敗時はStoreへ書かれない
            assertTrue(store.get("s1", "b1", "1", PrefetchStore.VARIANT_FULL) == null)
        }

    @Test
    fun jpegHit_returnsDiskSourceWithoutNetwork() =
        runTest {
            val store = FakePrefetchStore(temp.root)
            val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte())
            store.put("s1", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, PrefetchStore.VARIANT_JPEG, jpeg, etag = null)

            val result = jpegFetcher(store).fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.DISK, result.dataSource)
            assertEquals("image/jpeg", result.mimeType)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun jpegMiss_fetchesNetworkAndWritesStoreWithJpegVariant() =
        runTest {
            val body = "pdf-jpeg-bytes"
            server.enqueue(MockResponse.Builder().body(body).build())
            val store = FakePrefetchStore(temp.root)

            val result = jpegFetcher(store).fetch()

            assertTrue(result is SourceFetchResult)
            result as SourceFetchResult
            assertEquals(DataSource.NETWORK, result.dataSource)
            assertEquals(1, server.requestCount)

            val entry = store.get("s1", "b1", "1", PrefetchStore.VARIANT_JPEG)
            assertNotNull(entry)
            assertArrayEquals(body.toByteArray(), entry!!.file.readBytes())
            assertTrue(store.get("s1", "b1", "1", PrefetchStore.VARIANT_FULL) == null)
        }
}
