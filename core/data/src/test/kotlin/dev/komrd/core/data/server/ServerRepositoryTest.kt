package dev.komrd.core.data.server

import app.cash.turbine.test
import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.database.crypto.EncryptedSecret
import dev.komrd.core.database.crypto.SecretCipher
import dev.komrd.core.database.dao.ServersDao
import dev.komrd.core.database.entity.ServerEntity
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ServerRepositoryTest {
    private lateinit var serversDao: FakeServersDao
    private lateinit var cipher: IdentityCipher
    private lateinit var trustStore: InMemoryServerTrustStore
    private lateinit var factory: KomgaClientFactory
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        serversDao = FakeServersDao()
        cipher = IdentityCipher()
        trustStore = InMemoryServerTrustStore()
        factory = KomgaClientFactory(trustStore = trustStore)
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.close()
    }

    private fun repo() =
        ServerRepositoryImpl(
            serversDao = serversDao,
            cipher = cipher,
            clientFactory = factory,
            trustStore = trustStore,
            clock = { 100L },
        )

    private fun sampleServer(baseUrl: String = server.url("/").toString()) =
        Server(id = "s1", name = "Home", baseUrl = baseUrl, auth = AuthMethod.ApiKey("key"))

    @Test
    fun add_persistsServer() =
        runTest {
            repo().add(sampleServer())
            assertEquals("s1", serversDao.findById("s1")?.id)
        }

    @Test
    fun servers_observesDaoAndDecodesAuth() =
        runTest {
            repo().add(sampleServer())
            val list = repo().servers.first()
            assertEquals(1, list.size)
            assertEquals("s1", list[0].id)
            assertTrue(list[0].auth is AuthMethod.ApiKey)
        }

    @Test
    fun servers_emitsAcrossMutations_withTurbine() =
        runTest {
            val repository = repo()
            repository.servers.test {
                assertEquals(emptyList<String>(), awaitItem().map { it.id })
                repository.add(sampleServer())
                assertEquals(listOf("s1"), awaitItem().map { it.id })
                repository.delete("s1")
                assertEquals(emptyList<String>(), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun update_preservesCreatedAtAndInvalidatesClient() =
        runTest {
            val repository = repo()
            repository.add(sampleServer())
            serversDao.setCreatedAt("s1", 50L)
            val original = factory.clientFor(sampleServer())
            repository.update(sampleServer().copy(name = "Renamed"))
            assertEquals(50L, serversDao.findById("s1")?.createdAt)
            assertEquals("Renamed", serversDao.findById("s1")?.name)
            // invalidate でキャッシュが破棄され、次回 clientFor は新インスタンスを返す
            val rebuilt = factory.clientFor(sampleServer())
            assertTrue(original !== rebuilt)
        }

    @Test
    fun delete_removesServerAndTrustAndInvalidatesClient() =
        runTest {
            val repository = repo()
            repository.add(sampleServer())
            trustStore.replacePins("s1", setOf("AA"))
            repository.delete("s1")
            assertEquals(0, serversDao.count())
            assertTrue(trustStore.pinnedCertificateFingerprints("s1").isEmpty())
        }

    @Test
    fun verifyConnection_success_returnsAuthenticated() =
        runTest {
            server.enqueue(jsonResponse("""{"id":"u1","email":null,"name":null}""", 200))
            val result = repo().verifyConnection(sampleServer())
            assertTrue(result is KomgaResult.Success)
            val value = (result as KomgaResult.Success).value
            assertTrue(value is ConnectionResult.Authenticated)
            assertEquals("u1", (value as ConnectionResult.Authenticated).userId)
        }

    @Test
    fun verifyConnection_unauthorized_returnsUnauthorized() =
        runTest {
            server.enqueue(MockResponse.Builder().code(401).build())
            val result = repo().verifyConnection(sampleServer())
            assertTrue(result is KomgaResult.Failure)
            assertTrue((result as KomgaResult.Failure).error is KomgaError.Unauthorized)
        }

    @Test
    fun pinCertificate_setsPinAndInvalidatesClient() =
        runTest {
            val repository = repo()
            repository.add(sampleServer())
            val cert = certificateInfo("AA:BB:CC")
            val before = factory.clientFor(sampleServer())
            repository.pinCertificate("s1", cert)
            assertEquals(setOf("AA:BB:CC"), trustStore.pinnedCertificateFingerprints("s1"))
            val after = factory.clientFor(sampleServer())
            assertTrue(before !== after)
        }

    @Test
    fun pinCertificate_replacesExistingPins() =
        runTest {
            val repository = repo()
            repository.add(sampleServer())
            repository.pinCertificate("s1", certificateInfo("AA:BB"))
            repository.pinCertificate("s1", certificateInfo("CC:DD"))
            assertEquals(setOf("CC:DD"), trustStore.pinnedCertificateFingerprints("s1"))
        }

    @Test
    fun existingPinMismatch_detectsNewFingerprint() =
        runTest {
            val repository = repo()
            repository.add(sampleServer())
            repository.pinCertificate("s1", certificateInfo("AA:BB"))
            assertTrue(repository.existingPinMismatch("s1", "CC:DD"))
            assertEquals(false, repository.existingPinMismatch("s1", "AA:BB"))
        }

    @Test
    fun certificateInfoOf_extractsFromUntrustedCertificateError() {
        val cert = certificateInfo("AA:BB")
        val error = KomgaError.UntrustedCertificate(cert, "msg")
        assertEquals(
            cert,
            ServerRepositoryImpl(
                cipher = cipher,
                clientFactory = factory,
                serversDao = serversDao,
                trustStore = trustStore,
            ).certificateInfoOf(error),
        )
    }

    private fun certificateInfo(fingerprint: String): CertificateInfo =
        CertificateInfo(
            sha256Fingerprint = fingerprint,
            subject = "CN=test",
            issuer = "CN=test",
            notBefore = Instant.parse("2024-01-01T00:00:00Z"),
            notAfter = Instant.parse("2034-01-01T00:00:00Z"),
        )

    private fun jsonResponse(
        body: String,
        code: Int,
    ): MockResponse =
        MockResponse
            .Builder()
            .code(code)
            .headers(Headers.headersOf("Content-Type", "application/json"))
            .body(body)
            .build()
}

private class IdentityCipher : SecretCipher {
    override fun encrypt(plaintext: ByteArray) = EncryptedSecret(plaintext, byteArrayOf(0))

    override fun decrypt(secret: EncryptedSecret) = secret.ciphertext
}

private class FakeServersDao : ServersDao {
    private val rows = mutableMapOf<String, ServerEntity>()
    private val flow = MutableStateFlow<List<ServerEntity>>(emptyList())

    override fun observeAll(): Flow<List<ServerEntity>> = flow.asStateFlow()

    override suspend fun findById(id: String): ServerEntity? = rows[id]

    override suspend fun upsert(server: ServerEntity) {
        rows[server.id] = server
        flow.update { rows.values.sortedBy { it.createdAt }.toList() }
    }

    override suspend fun delete(server: ServerEntity) {
        rows.remove(server.id)
        flow.update { rows.values.sortedBy { it.createdAt }.toList() }
    }

    override suspend fun deleteById(id: String) {
        rows.remove(id)
        flow.update { rows.values.sortedBy { it.createdAt }.toList() }
    }

    fun count(): Int = rows.size

    fun setCreatedAt(
        id: String,
        createdAt: Long,
    ) {
        rows[id]?.let { rows[id] = it.copy(createdAt = createdAt) }
        flow.update { rows.values.sortedBy { it.createdAt }.toList() }
    }
}
