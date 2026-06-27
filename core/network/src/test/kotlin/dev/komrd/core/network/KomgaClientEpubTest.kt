package dev.komrd.core.network

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.dto.R2DeviceDto
import dev.komrd.core.network.dto.R2LocationDto
import dev.komrd.core.network.dto.R2LocatorDto
import dev.komrd.core.network.dto.R2ProgressionDto
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaClientEpubTest {
    @Test
    fun getManifestEpub_parsesReadingOrder_andHitsManifestPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(MANIFEST_JSON)
                val client = client(server)

                val result = client.getManifestEpub("book-1")

                assertTrue(result is KomgaResult.Success)
                val manifest = (result as KomgaResult.Success).value
                assertEquals("https://readium.org/webpub-manifest", manifest.context)
                assertEquals("EPUB Book", manifest.metadata.title)
                assertEquals("ltr", manifest.metadata.readingProgression)
                assertEquals(1, manifest.readingOrder.size)
                assertEquals("OEBPS/ch1.xhtml", manifest.readingOrder.first().href)
                assertEquals("application/xhtml+xml", manifest.readingOrder.first().type)
                assertEquals("/api/v1/books/book-1/manifest/epub", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun getBookEpubResource_pathNotEscaped() =
        runTest {
            withServer { server ->
                server.enqueue(
                    MockResponse
                        .Builder()
                        .code(200)
                        .headers(Headers.headersOf("Content-Type", "application/xhtml+xml"))
                        .body("<html/>")
                        .build(),
                )
                val client = client(server)

                val result = client.getBookEpubResource("book-1", "OEBPS/ch1.xhtml")

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                // @Path(encoded=true)によりスラッシュが%2Fへエスケープされないことを検証
                assertEquals(
                    "/api/v1/books/book-1/resource/OEBPS/ch1.xhtml",
                    request.url.encodedPath,
                )
            }
        }

    @Test
    fun getPositions_parsesTotal_andHitsPositionsPath() =
        runTest {
            withServer { server ->
                server.enqueueJson(POSITIONS_JSON)
                val client = client(server)

                val result = client.getPositions("book-1")

                assertTrue(result is KomgaResult.Success)
                val positions = (result as KomgaResult.Success).value
                assertEquals(10, positions.total)
                assertEquals(1, positions.positions.size)
                assertEquals("OEBPS/ch1.xhtml", positions.positions.first().href)
                assertEquals(
                    1,
                    positions.positions
                        .first()
                        .locations
                        ?.position,
                )
                assertEquals("/api/v1/books/book-1/positions", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun getProgression_204_returnsNull() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(204).build())
                val client = client(server)

                val result = client.getProgression("book-1")

                assertTrue(result is KomgaResult.Success)
                assertNull((result as KomgaResult.Success).value)
                assertEquals("/api/v1/books/book-1/progression", server.takeRequest().url.encodedPath)
            }
        }

    @Test
    fun getProgression_200_parsesLocator() =
        runTest {
            withServer { server ->
                server.enqueueJson(PROGRESSION_JSON)
                val client = client(server)

                val result = client.getProgression("book-1")

                assertTrue(result is KomgaResult.Success)
                val progression = (result as KomgaResult.Success).value
                assertEquals("2026-06-26T07:28:00+00:00", progression?.modified)
                assertEquals("dev-1", progression?.device?.id)
                assertEquals(0.5f, progression?.locator?.locations?.progression)
            }
        }

    @Test
    fun putProgression_sendsBody_andHitsProgressionPath() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(204).build())
                val client = client(server)

                val result = client.putProgression("book-1", PROGRESSION_DTO)

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                assertEquals("PUT", request.method)
                assertEquals("/api/v1/books/book-1/progression", request.url.encodedPath)
                val body = request.body?.utf8()
                assertTrue(body != null && body.contains("\"OEBPS/ch1.xhtml\""))
                assertTrue(body != null && body.contains("\"dev-1\""))
            }
        }

    @Test
    fun getManifestEpub_unauthorized_returnsUnauthorizedError() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val client = client(server)

                val result = client.getManifestEpub("book-1")

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
        val MANIFEST_JSON =
            """
            {
              "@context": "https://readium.org/webpub-manifest",
              "metadata": {"title": "EPUB Book", "identifier": "urn:isbn:123", "language": "en", "readingProgression": "ltr", "numberOfPages": 10},
              "links": [],
              "images": [],
              "readingOrder": [{"href": "OEBPS/ch1.xhtml", "type": "application/xhtml+xml", "title": "Chapter 1"}],
              "resources": [],
              "toc": [],
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

        val PROGRESSION_DTO =
            R2ProgressionDto(
                modified = "2026-06-26T07:28:00+00:00",
                device = R2DeviceDto(id = "dev-1", name = "KomRD"),
                locator =
                    R2LocatorDto(
                        href = "OEBPS/ch1.xhtml",
                        type = "application/xhtml+xml",
                        locations = R2LocationDto(progression = 0.5f, position = 5, totalProgression = 0.5f),
                    ),
            )
    }
}
