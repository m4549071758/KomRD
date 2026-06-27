package dev.komrd.core.network.tls

import dev.komrd.core.database.dao.ServerTrustDao
import dev.komrd.core.database.entity.ServerTrustEntity
import dev.komrd.core.database.mapper.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.X509Certificate

class DatabaseServerTrustStoreTest {
    @Test
    fun replacePins_then_read_returnsPins() =
        runTest {
            val store = DatabaseServerTrustStore(FakeServerTrustDao(), clock = { 100L })
            store.replacePins("s1", setOf("AA:BB", "CC:DD"))
            assertEquals(setOf("AA:BB", "CC:DD"), store.pinnedCertificateFingerprints("s1"))
        }

    @Test
    fun replacePins_preservesExistingCustomCas() =
        runTest {
            val dao = FakeServerTrustDao()
            val store = DatabaseServerTrustStore(dao, clock = { 100L })
            store.replaceCustomCas("s1", listOf(selfSignedCert()))
            store.replacePins("s1", setOf("AA:BB"))
            val persisted = dao.findById("s1")
            assertEquals(setOf("AA:BB"), persisted?.toDomain()?.pinnedFingerprints)
            assertEquals(1, persisted?.toDomain()?.customCaCertificates?.size)
            assertEquals(1, store.customCaCertificates("s1").size)
        }

    @Test
    fun replaceCustomCas_preservesExistingPins() =
        runTest {
            val dao = FakeServerTrustDao()
            val store = DatabaseServerTrustStore(dao, clock = { 100L })
            store.replacePins("s1", setOf("AA:BB"))
            store.replaceCustomCas("s1", listOf(selfSignedCert()))
            val persisted = dao.findById("s1")
            assertEquals(setOf("AA:BB"), persisted?.toDomain()?.pinnedFingerprints)
            assertEquals(1, persisted?.toDomain()?.customCaCertificates?.size)
        }

    @Test
    fun load_populatesCacheFromDao() =
        runTest {
            val dao = FakeServerTrustDao()
            dao.upsert(
                ServerTrustEntity(
                    serverId = "s1",
                    pinnedFingerprintsJson = """["AA:BB"]""",
                    customCaCertsPem = "",
                    updatedAt = 1L,
                ),
            )
            val store = DatabaseServerTrustStore(dao, clock = { 100L })
            store.load("s1")
            assertEquals(setOf("AA:BB"), store.pinnedCertificateFingerprints("s1"))
            assertTrue(store.customCaCertificates("s1").isEmpty())
        }

    @Test
    fun loadAll_populatesCacheForAllServers() =
        runTest {
            val dao = FakeServerTrustDao()
            dao.upsert(
                ServerTrustEntity(
                    serverId = "s1",
                    pinnedFingerprintsJson = """["AA:BB"]""",
                    customCaCertsPem = "",
                    updatedAt = 1L,
                ),
            )
            dao.upsert(
                ServerTrustEntity(
                    serverId = "s2",
                    pinnedFingerprintsJson = """["CC:DD"]""",
                    customCaCertsPem = "",
                    updatedAt = 2L,
                ),
            )
            val store = DatabaseServerTrustStore(dao, clock = { 100L })
            store.loadAll()
            assertEquals(setOf("AA:BB"), store.pinnedCertificateFingerprints("s1"))
            assertEquals(setOf("CC:DD"), store.pinnedCertificateFingerprints("s2"))
        }

    @Test
    fun clear_removesFromCacheAndDao() =
        runTest {
            val dao = FakeServerTrustDao()
            val store = DatabaseServerTrustStore(dao, clock = { 100L })
            store.replacePins("s1", setOf("AA:BB"))
            store.clear("s1")
            assertTrue(store.pinnedCertificateFingerprints("s1").isEmpty())
            assertNull(dao.findById("s1"))
        }

    @Test
    fun load_missingServer_leavesCacheEmpty() =
        runTest {
            val store = DatabaseServerTrustStore(FakeServerTrustDao(), clock = { 100L })
            store.load("nope")
            assertTrue(store.pinnedCertificateFingerprints("nope").isEmpty())
        }

    private fun selfSignedCert(): X509Certificate = loadTestKeystore().getCertificate("test") as X509Certificate

    private fun loadTestKeystore(): java.security.KeyStore {
        val keystore = java.security.KeyStore.getInstance("PKCS12")
        DatabaseServerTrustStoreTest::class.java.classLoader!!
            .getResourceAsStream("test-keystore.p12")
            .use { input -> keystore.load(input, KEYSTORE_PASSWORD.toCharArray()) }
        return keystore
    }

    private companion object {
        const val KEYSTORE_PASSWORD = "password"
    }
}

private class FakeServerTrustDao : ServerTrustDao {
    private val rows = mutableMapOf<String, ServerTrustEntity>()
    private val flow = MutableStateFlow<List<ServerTrustEntity>>(emptyList())

    override suspend fun findById(serverId: String): ServerTrustEntity? = rows[serverId]

    override fun observe(): Flow<List<ServerTrustEntity>> = flow

    override suspend fun upsert(entity: ServerTrustEntity) {
        rows[entity.serverId] = entity
        flow.update { rows.values.sortedBy { it.updatedAt }.toList() }
    }

    override suspend fun deleteByServerId(serverId: String) {
        rows.remove(serverId)
        flow.update { rows.values.sortedBy { it.updatedAt }.toList() }
    }
}
