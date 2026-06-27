package dev.komrd.core.network

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Server
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.dto.PageDto
import dev.komrd.core.network.dto.SeriesDto
import dev.komrd.core.network.dto.SeriesListRequestDto
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class KomgaClientFactoryTest {
    @Test
    fun apiKeyAuth_addsApiKeyHeader() =
        runTest {
            withServer { server ->
                server.enqueueJson(seriesPageJson)
                val client =
                    factory().clientFor(
                        Server(
                            id = "s1",
                            name = "Home",
                            baseUrl = server.url("/").toString(),
                            auth = AuthMethod.ApiKey("api-key-1"),
                        ),
                    )

                val result = client.listSeries()

                assertTrue(result is KomgaResult.Success)
                val request = server.takeRequest()
                assertEquals("api-key-1", request.headers["X-API-Key"])
                assertEquals("/api/v1/series/list", request.url.encodedPath)
            }
        }

    @Test
    fun basicAuth_reusesCapturedSessionToken() =
        runTest {
            withServer { server ->
                server.enqueueJson(seriesPageJson, "X-Auth-Token" to "session-1")
                server.enqueueJson(seriesPageJson)
                val client =
                    factory().clientFor(
                        Server(
                            id = "s2",
                            name = "Home",
                            baseUrl = server.url("/").toString(),
                            auth = AuthMethod.Basic("alice", "password"),
                        ),
                    )

                assertTrue(client.listSeries() is KomgaResult.Success)
                assertTrue(client.listSeries() is KomgaResult.Success)

                val first = server.takeRequest()
                val second = server.takeRequest()
                assertTrue(first.headers["Authorization"].orEmpty().startsWith("Basic "))
                assertEquals("true", first.headers["X-Auth-Token"])
                assertNull(second.headers["Authorization"])
                assertEquals("session-1", second.headers["X-Auth-Token"])
            }
        }

    @Test
    fun basicAuth_reauthenticatesOnUnauthorized() =
        runTest {
            withServer { server ->
                val sessionStore =
                    InMemorySessionStore().apply {
                        putToken("s3", "expired-token")
                    }
                server.enqueue(MockResponse.Builder().code(401).build())
                server.enqueueJson(seriesPageJson, "X-Auth-Token" to "fresh-token")
                val client =
                    factory(sessionStore).clientFor(
                        Server(
                            id = "s3",
                            name = "Home",
                            baseUrl = server.url("/").toString(),
                            auth = AuthMethod.Basic("alice", "password"),
                        ),
                    )

                val result = client.listSeries()

                assertTrue(result is KomgaResult.Success)
                val first = server.takeRequest()
                val second = server.takeRequest()
                assertEquals("expired-token", first.headers["X-Auth-Token"])
                assertNull(first.headers["Authorization"])
                assertTrue(second.headers["Authorization"].orEmpty().startsWith("Basic "))
                assertEquals("true", second.headers["X-Auth-Token"])
                assertEquals("fresh-token", sessionStore.getToken("s3"))
            }
        }

    @Test
    fun dto_roundtripsWithUnknownFieldsIgnoredAndNullsOmitted() {
        val decoded =
            KomgaJson.decodeFromString<PageDto<SeriesDto>>(
                """
                {
                  "content": [
                    {
                      "id": "series-1",
                      "libraryId": "library-1",
                      "name": "My Series",
                      "metadata": {
                        "title": "My Series",
                        "readingDirection": "RIGHT_TO_LEFT",
                        "unknownMetadata": "ignored"
                      },
                      "unknownSeriesField": "ignored"
                    }
                  ],
                  "totalElements": 1,
                  "unknownPageField": "ignored"
                }
                """.trimIndent(),
            )

        assertEquals("series-1", decoded.content.single().id)
        assertEquals(
            "RIGHT_TO_LEFT",
            decoded.content
                .single()
                .metadata.readingDirection,
        )

        val encoded = KomgaJson.encodeToString(SeriesListRequestDto(fullTextSearch = "keyword"))
        assertEquals("""{"fullTextSearch":"keyword"}""", encoded)
    }

    @Test
    fun verifyConnection_success_returnsAuthenticated() =
        runTest {
            withServer { server ->
                server.enqueueJson(WHOAMI_JSON)
                val client =
                    factory().clientFor(
                        Server(
                            id = "s1",
                            name = "Home",
                            baseUrl = server.url("/").toString(),
                            auth = AuthMethod.ApiKey("api-key-1"),
                        ),
                    )

                val result = client.verifyConnection()

                assertTrue(result is KomgaResult.Success)
                val authenticated = (result as KomgaResult.Success).value
                assertTrue(authenticated is ConnectionResult.Authenticated)
                assertEquals("user-1", (authenticated as ConnectionResult.Authenticated).userId)
                val request = server.takeRequest()
                assertEquals("/api/v1/users/me", request.url.encodedPath)
            }
        }

    @Test
    fun verifyConnection_unauthorized_returnsUnauthorizedError() =
        runTest {
            withServer { server ->
                server.enqueue(MockResponse.Builder().code(401).build())
                val client =
                    factory().clientFor(
                        Server(
                            id = "s2",
                            name = "Home",
                            baseUrl = server.url("/").toString(),
                            auth = AuthMethod.ApiKey("bad-key"),
                        ),
                    )

                val result = client.verifyConnection()

                assertTrue(result is KomgaResult.Failure)
                assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
            }
        }

    @Test
    fun verifyConnection_tlsUntrusted_returnsUntrustedCertificateError() =
        runTest {
            val server = MockWebServer()
            server.useHttps(serverSslSocketFactory())
            try {
                server.start()
                server.enqueueJson(WHOAMI_JSON)
                val baseUrl = server.url("/").toString().replace("localhost", "127.0.0.1")
                val client =
                    factory().clientFor(
                        Server(
                            id = "s3",
                            name = "Home",
                            baseUrl = baseUrl,
                            auth = AuthMethod.ApiKey("api-key-1"),
                        ),
                    )

                val result = client.verifyConnection()

                assertTrue(result is KomgaResult.Failure)
                val error = (result as KomgaResult.Failure).error
                assertTrue("expected UntrustedCertificate but was $error", error is KomgaError.UntrustedCertificate)
                assertNotNull((error as KomgaError.UntrustedCertificate).certificate)
            } finally {
                server.close()
            }
        }

    private fun serverSslSocketFactory(): javax.net.ssl.SSLSocketFactory {
        val keyStore = KeyStore.getInstance("PKCS12")
        KomgaClientFactoryTest::class.java.classLoader!!
            .getResourceAsStream("test-keystore.p12")
            .use { input -> keyStore.load(input, KEYSTORE_PASSWORD.toCharArray()) }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore, KEYSTORE_PASSWORD.toCharArray())
        val sslContext = SSLContext.getInstance("TLS")
        sslContext.init(kmf.keyManagers, null, java.security.SecureRandom())
        return sslContext.socketFactory
    }

    private fun factory(sessionStore: InMemorySessionStore = InMemorySessionStore()) =
        KomgaClientFactory(
            sessionStore = sessionStore,
            trustStore = InMemoryServerTrustStore(),
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

    private fun MockWebServer.enqueueJson(
        body: String,
        vararg headers: Pair<String, String>,
    ) {
        enqueue(
            MockResponse
                .Builder()
                .code(200)
                .headers(
                    Headers.headersOf(
                        *(
                            listOf("Content-Type" to "application/json") + headers
                        ).flatMap { (name, value) -> listOf(name, value) }
                            .toTypedArray(),
                    ),
                ).body(body)
                .build(),
        )
    }

    private companion object {
        val seriesPageJson =
            """
            {
              "content": [
                {
                  "id": "series-1",
                  "libraryId": "library-1",
                  "name": "Series One",
                  "metadata": {"title": "Series One"}
                }
              ],
              "number": 0,
              "size": 20,
              "totalElements": 1,
              "totalPages": 1
            }
            """.trimIndent()

        const val KEYSTORE_PASSWORD = "password"

        const val WHOAMI_JSON = """{"id":"user-1","email":"u@example.com","name":"User One"}"""
    }
}
