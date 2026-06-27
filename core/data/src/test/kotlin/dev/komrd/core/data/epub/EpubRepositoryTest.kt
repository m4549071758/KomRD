package dev.komrd.core.data.epub

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpubRepositoryTest {
    @Test
    fun loadManifest_parsesReadingOrderAndToc() =
        runTest {
            withServer { server ->
                server.enqueueJson(MANIFEST_JSON)

                val result = repository().loadManifest(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                val manifest = (result as KomgaResult.Success).value
                assertEquals("EPUB Book", manifest.title)
                assertEquals("ltr", manifest.readingProgression)
                assertEquals(1, manifest.readingOrder.size)
                assertEquals("OEBPS/ch1.xhtml", manifest.readingOrder.first().href)
                assertEquals("application/xhtml+xml", manifest.readingOrder.first().type)
                assertEquals("Chapter 1", manifest.readingOrder.first().title)
                assertEquals(1, manifest.toc.size)
                assertEquals("OEBPS/nav.xhtml", manifest.toc.first().href)
                assertEquals(1, manifest.resources.size)
                assertEquals("OEBPS/style.css", manifest.resources.first().href)
                assertEquals("text/css", manifest.resources.first().type)
                assertEquals("stylesheet", manifest.resources.first().rel)
            }
        }

    @Test
    fun loadResource_returnsBytes() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .headers(Headers.headersOf("Content-Type", "application/xhtml+xml"))
                        .body("<html><body/></html>")
                        .build(),
                )

                val result = repository().loadResource(server.asServer(), "book-1", "OEBPS/ch1.xhtml")

                assertTrue(result is KomgaResult.Success)
                assertArrayEquals(
                    "<html><body/></html>".toByteArray(Charsets.UTF_8),
                    (result as KomgaResult.Success).value,
                )
            }
        }

    @Test
    fun loadPositions_parsesLocators() =
        runTest {
            withServer { server ->
                server.enqueueJson(POSITIONS_JSON)

                val result = repository().loadPositions(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                val locators = (result as KomgaResult.Success).value
                assertEquals(1, locators.size)
                val locator = locators.first()
                assertEquals("OEBPS/ch1.xhtml", locator.href)
                assertEquals(1, locator.position)
                assertEquals(0.0f, locator.totalProgression)
            }
        }

    @Test
    fun loadProgression_204_returnsNull() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(204).build())

                val result = repository().loadProgression(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                assertNull((result as KomgaResult.Success).value)
            }
        }

    @Test
    fun loadProgression_parsesLocator() =
        runTest {
            withServer { server ->
                server.enqueueJson(PROGRESSION_JSON)

                val result = repository().loadProgression(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Success)
                val locator = (result as KomgaResult.Success).value
                assertEquals("OEBPS/ch1.xhtml", locator?.href)
                assertEquals(0.5f, locator?.progression)
                assertEquals(0.5f, locator?.totalProgression)
                assertEquals(5, locator?.position)
            }
        }

    @Test
    fun loadManifest_unauthorized_returnsFailure() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())

                val result = repository().loadManifest(server.asServer(), "book-1")

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
            }
        }

    private fun repository(): EpubRepository =
        EpubRepositoryImpl(
            clientFactory =
                KomgaClientFactory(
                    sessionStore = InMemorySessionStore(),
                    trustStore = InMemoryServerTrustStore(),
                ),
            trustStore = InMemoryServerTrustStore(),
        )

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

    private fun MockWebServer.enqueueJson(body: String) =
        enqueue(
            MockResponse
                .Builder()
                .code(200)
                .headers(Headers.headersOf("Content-Type", "application/json"))
                .body(body)
                .build(),
        )

    private companion object {
        val MANIFEST_JSON =
            """
            {
              "@context": "https://readium.org/webpub-manifest",
              "metadata": {"title": "EPUB Book", "identifier": "urn:isbn:123", "language": "en", "readingProgression": "ltr", "numberOfPages": 10},
              "links": [],
              "images": [],
              "readingOrder": [{"href": "OEBPS/ch1.xhtml", "type": "application/xhtml+xml", "title": "Chapter 1"}],
              "resources": [{"href": "OEBPS/style.css", "type": "text/css", "rel": "stylesheet"}],
              "toc": [{"href": "OEBPS/nav.xhtml", "type": "application/xhtml+xml", "title": "Contents"}],
              "landmarks": [],
              "pageList": []
            }
            """.trimIndent()

        val POSITIONS_JSON =
            """
            {
              "total": 10,
              "positions": [
                {"href": "OEBPS/ch1.xhtml", "type": "application/xhtml+xml", "locations": {"position": 1, "totalProgression": 0.0}}
              ]
            }
            """.trimIndent()

        val PROGRESSION_JSON =
            """
            {
              "modified": "2026-06-26T07:28:00+00:00",
              "device": {"id": "dev-1", "name": "KomRD"},
              "locator": {"href": "OEBPS/ch1.xhtml", "type": "application/xhtml+xml", "locations": {"progression": 0.5, "position": 5, "totalProgression": 0.5}}
            }
            """.trimIndent()
    }
}
