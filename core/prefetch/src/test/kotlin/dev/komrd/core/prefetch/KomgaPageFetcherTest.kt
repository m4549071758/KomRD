package dev.komrd.core.prefetch

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KomgaPageFetcherTest {
    private lateinit var ws: MockWebServer
    private lateinit var fetcher: KomgaPageFetcher

    @Before
    fun setUp() {
        ws = MockWebServer()
        ws.start()
        val trustStore = InMemoryServerTrustStore()
        fetcher =
            KomgaPageFetcher(
                clientFactory = KomgaClientFactory(sessionStore = InMemorySessionStore(), trustStore = trustStore),
                trustStore = trustStore,
            )
    }

    @After
    fun tearDown() {
        ws.close()
    }

    @Test
    fun fetch_success_returnsBytes() =
        runTest {
            ws.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(PAGE_BYTES)
                    .build(),
            )

            val result = fetcher.fetch(server(), "b1", 3)

            assertTrue(result is KomgaResult.Success)
            assertArrayEquals(PAGE_BYTES.toByteArray(), (result as KomgaResult.Success).value)
            val recorded = ws.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/books/b1/pages/3", recorded.target)
        }

    @Test
    fun fetch_serverError_returnsHttp() =
        runTest {
            ws.enqueue(MockResponse.Builder().code(500).build())

            val result = fetcher.fetch(server(), "b1", 3)

            assertTrue(result is KomgaResult.Failure)
            val error = (result as KomgaResult.Failure).error
            assertTrue(error is KomgaError.Http)
            assertEquals(500, (error as KomgaError.Http).statusCode)
        }

    @Test
    fun fetch_unauthorized_returnsUnauthorized() =
        runTest {
            ws.enqueue(MockResponse.Builder().code(401).build())

            val result = fetcher.fetch(server(), "b1", 3)

            assertTrue(result is KomgaResult.Failure)
            assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
        }

    @Test
    fun fetch_pdf_sendsConvertJpegQuery() =
        runTest {
            ws.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(PAGE_BYTES)
                    .build(),
            )

            val result = fetcher.fetch(server(), "b1", 3, BookMediaProfile.PDF)

            assertTrue(result is KomgaResult.Success)
            val recorded = ws.takeRequest()
            assertEquals("GET", recorded.method)
            assertEquals("/api/v1/books/b1/pages/3?convert=jpeg", recorded.target)
        }

    @Test
    fun fetch_image_noConvertQuery() =
        runTest {
            ws.enqueue(
                MockResponse
                    .Builder()
                    .code(200)
                    .body(PAGE_BYTES)
                    .build(),
            )

            val result = fetcher.fetch(server(), "b1", 3, BookMediaProfile.IMAGE)

            assertTrue(result is KomgaResult.Success)
            val recorded = ws.takeRequest()
            // 画像系はconvert無し(既定)。
            assertEquals("/api/v1/books/b1/pages/3", recorded.target)
        }

    private fun server(): Server =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = ws.url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private companion object {
        const val PAGE_BYTES = "PNG-IMAGE-BYTES"
    }
}
