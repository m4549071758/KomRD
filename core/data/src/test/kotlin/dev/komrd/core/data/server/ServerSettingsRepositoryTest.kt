package dev.komrd.core.data.server

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.model.ServerSettings
import dev.komrd.core.model.SettingsUpdate
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

class ServerSettingsRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var fakeServerRepo: FakeServerRepository
    private lateinit var trustStore: InMemoryServerTrustStore
    private lateinit var factory: KomgaClientFactory

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        trustStore = InMemoryServerTrustStore()
        factory = KomgaClientFactory(trustStore = trustStore)
        fakeServerRepo = FakeServerRepository()
        fakeServerRepo.seed(sampleServer())
    }

    @After
    fun teardown() {
        server.close()
    }

    private fun repo() =
        ServerSettingsRepositoryImpl(
            serverRepository = fakeServerRepo,
            clientFactory = factory,
            trustStore = trustStore,
        )

    private fun userRepo() =
        UserRepositoryImpl(
            serverRepository = fakeServerRepo,
            clientFactory = factory,
            trustStore = trustStore,
        )

    private fun sampleServer() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = server.url("/").toString(),
            auth = AuthMethod.ApiKey("key"),
        )

    @Test
    fun get_mapsDtoToDomainAndUnwrapsEffectiveValues() =
        runTest {
            server.enqueue(json(SETTINGS_JSON))
            val result = repo().get("s1")
            assertTrue(result is KomgaResult.Success)
            val settings = (result as KomgaResult.Success).value
            assertEquals(true, settings.deleteEmptyCollections)
            assertEquals(8, settings.taskPoolSize)
            assertEquals(30L, settings.rememberMeDurationDays)
            assertEquals("LARGE", settings.thumbnailSize)
            assertEquals(8080, settings.serverPort)
            assertEquals("/komga", settings.serverContextPath)
            assertEquals("/api/v1/settings", server.takeRequest().url.encodedPath)
        }

    @Test
    fun get_unknownServer_returnsFailure() =
        runTest {
            val result = repo().get("missing")
            assertTrue(result is KomgaResult.Failure)
            assertTrue((result as KomgaResult.Failure).error is KomgaError.Unknown)
        }

    @Test
    fun get_serverError_returnsFailure() =
        runTest {
            server.enqueue(MockResponse.Builder().code(500).build())
            val result = repo().get("s1")
            assertTrue(result is KomgaResult.Failure)
        }

    @Test
    fun update_emptyDiff_skipsNetworkAndSucceeds() =
        runTest {
            val result = repo().update("s1", SettingsUpdate())
            assertTrue(result is KomgaResult.Success)
            // 通信せずキューは空のまま
            assertEquals(0, server.requestCount)
        }

    @Test
    fun update_changedFieldsOnly_sendsDiffPatch() =
        runTest {
            server.enqueue(MockResponse.Builder().code(204).build())
            val update =
                SettingsUpdate(
                    deleteEmptyCollections = false,
                    taskPoolSize = 16,
                    serverPort = 8081,
                )
            val result = repo().update("s1", update)
            assertTrue(result is KomgaResult.Success)
            val request = server.takeRequest()
            assertEquals("PATCH", request.method)
            assertEquals("/api/v1/settings", request.url.encodedPath)
            val body = request.body?.utf8().orEmpty()
            assertTrue("body: $body", body.contains("deleteEmptyCollections"))
            assertTrue("body: $body", body.contains("taskPoolSize"))
            assertTrue("body: $body", body.contains("serverPort"))
            assertTrue("body: $body", body.contains("8081"))
            // 省略項目は出力されない
            assertTrue("body: $body", !body.contains("rememberMeDurationDays"))
            assertTrue("body: $body", !body.contains("koboPort"))
        }

    @Test
    fun update_nonSuccess_returnsFailure() =
        runTest {
            server.enqueue(MockResponse.Builder().code(403).build())
            val result = repo().update("s1", SettingsUpdate(taskPoolSize = 16))
            assertTrue(result is KomgaResult.Failure)
        }

    @Test
    fun update_unknownServer_returnsFailureWithoutNetwork() =
        runTest {
            val result = repo().update("missing", SettingsUpdate(taskPoolSize = 16))
            assertTrue(result is KomgaResult.Failure)
            assertEquals(0, server.requestCount)
        }

    @Test
    fun currentUser_adminRole_isAdminTrue() =
        runTest {
            server.enqueue(json(ADMIN_USER_JSON))
            val result = userRepo().currentUser("s1")
            assertTrue(result is KomgaResult.Success)
            val user = (result as KomgaResult.Success).value
            assertEquals(setOf("ADMIN", "USER"), user.roles)
            assertTrue(userRepo().isAdmin(user))
            assertEquals("/api/v2/users/me", server.takeRequest().url.encodedPath)
        }

    @Test
    fun currentUser_nonAdminRole_isAdminFalse() =
        runTest {
            server.enqueue(json(NON_ADMIN_USER_JSON))
            val result = userRepo().currentUser("s1")
            assertTrue(result is KomgaResult.Success)
            val user = (result as KomgaResult.Success).value
            assertEquals(setOf("USER"), user.roles)
            assertEquals(false, userRepo().isAdmin(user))
            assertEquals(listOf("lib-1", "lib-2"), user.sharedLibrariesIds)
            assertEquals(false, user.sharedAllLibraries)
            assertEquals(18, user.ageRestriction?.age)
            assertEquals("ALLOW_ONLY", user.ageRestriction?.restriction)
        }

    @Test
    fun currentUser_ageRestrictionOmitted_isNull() =
        runTest {
            server.enqueue(json("""{"id":"u","roles":["USER"],"sharedAllLibraries":true}"""))
            val result = userRepo().currentUser("s1")
            assertTrue(result is KomgaResult.Success)
            val user = (result as KomgaResult.Success).value
            assertNull(user.ageRestriction)
            assertTrue(user.sharedAllLibraries)
        }

    @Test
    fun currentUser_unknownServer_returnsFailure() =
        runTest {
            val result = userRepo().currentUser("missing")
            assertTrue(result is KomgaResult.Failure)
        }

    @Test
    fun diff_producesOnlyChangedFields() {
        val original =
            ServerSettings(
                deleteEmptyCollections = true,
                taskPoolSize = 8,
                serverPort = 8080,
            )
        val updated = original.copy(taskPoolSize = 16, serverPort = 8081)
        val diff = SettingsUpdate.diff(original, updated)
        assertNull(diff.deleteEmptyCollections)
        assertEquals(16, diff.taskPoolSize)
        assertEquals(8081, diff.serverPort)
        assertNull(diff.deleteEmptyReadLists)
    }

    private fun json(body: String): MockResponse =
        MockResponse
            .Builder()
            .code(200)
            .headers(Headers.headersOf("Content-Type", "application/json"))
            .body(body)
            .build()

    private companion object {
        val SETTINGS_JSON =
            """
            {
              "deleteEmptyCollections": true,
              "deleteEmptyReadLists": false,
              "taskPoolSize": 8,
              "rememberMeDurationDays": 30,
              "renewRememberMeKey": true,
              "koboPort": 8083,
              "koboProxy": false,
              "thumbnailSize": "LARGE",
              "serverPort": {"configurationSource": 8080, "effectiveValue": 8080},
              "serverContextPath": {"configurationSource": "/komga", "effectiveValue": "/komga"}
            }
            """.trimIndent()

        val ADMIN_USER_JSON =
            """
            {
              "id": "u1",
              "email": "admin@example.com",
              "roles": ["ADMIN", "USER"],
              "sharedAllLibraries": true,
              "sharedLibrariesIds": []
            }
            """.trimIndent()

        val NON_ADMIN_USER_JSON =
            """
            {
              "id": "u2",
              "email": "user@example.com",
              "roles": ["USER"],
              "sharedAllLibraries": false,
              "sharedLibrariesIds": ["lib-1", "lib-2"],
              "ageRestriction": {"age": 18, "restriction": "ALLOW_ONLY"}
            }
            """.trimIndent()
    }
}

private class FakeServerRepository : ServerRepository {
    private val rows = mutableMapOf<String, Server>()

    fun seed(server: Server) {
        rows[server.id] = server
    }

    override val servers: kotlinx.coroutines.flow.Flow<List<Server>>
        get() = kotlinx.coroutines.flow.MutableStateFlow(rows.values.toList())

    override suspend fun byId(id: String): Server? = rows[id]

    override suspend fun add(server: Server) {
        rows[server.id] = server
    }

    override suspend fun update(server: Server) {
        rows[server.id] = server
    }

    override suspend fun delete(id: String) {
        rows.remove(id)
    }

    override suspend fun verifyConnection(server: Server): KomgaResult<dev.komrd.core.model.ConnectionResult> =
        KomgaResult.Success(
            dev.komrd.core.model.ConnectionResult
                .Authenticated(),
        )

    override suspend fun pinCertificate(
        serverId: String,
        certificate: dev.komrd.core.common.error.CertificateInfo,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<java.security.cert.X509Certificate>,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean = false

    override fun certificateInfoOf(error: KomgaError): dev.komrd.core.common.error.CertificateInfo? = null
}
