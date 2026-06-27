package dev.komrd.core.network.tls

import android.util.Log
import dev.komrd.core.database.dao.ServerTrustDao
import dev.komrd.core.database.entity.ServerTrustEntity
import dev.komrd.core.database.mapper.ServerTrust
import dev.komrd.core.database.mapper.toDomain
import dev.komrd.core.database.mapper.toEntity
import kotlinx.coroutines.flow.first
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

class DatabaseServerTrustStore(
    private val dao: ServerTrustDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : ServerTrustStore {
    private val pinsByServer = ConcurrentHashMap<String, Set<String>>()
    private val customCasByServer = ConcurrentHashMap<String, List<X509Certificate>>()

    override fun pinnedCertificateFingerprints(serverId: String): Set<String> = pinsByServer[serverId].orEmpty()

    override fun customCaCertificates(serverId: String): List<X509Certificate> = customCasByServer[serverId].orEmpty()

    override suspend fun load(serverId: String) {
        val entity = dao.findById(serverId) ?: return
        val trust = decodeOrLog(entity) ?: return
        pinsByServer[serverId] = trust.pinnedFingerprints
        customCasByServer[serverId] = trust.customCaCertificates
    }

    override suspend fun loadAll() {
        dao.observe().first().forEach { entity ->
            val trust = decodeOrLog(entity) ?: return@forEach
            pinsByServer[entity.serverId] = trust.pinnedFingerprints
            customCasByServer[entity.serverId] = trust.customCaCertificates
        }
    }

    private fun decodeOrLog(entity: ServerTrustEntity): ServerTrust? =
        runCatching { entity.toDomain() }.getOrElse { error ->
            Log.e(TAG, "Skipping invalid server trust data for ${entity.serverId}.", error)
            null
        }

    override suspend fun replacePins(
        serverId: String,
        fingerprints: Set<String>,
    ) {
        val currentCas = customCasByServer[serverId].orEmpty()
        upsert(serverId, ServerTrust(fingerprints, currentCas))
        pinsByServer[serverId] = fingerprints
    }

    override suspend fun replaceCustomCas(
        serverId: String,
        certificates: List<X509Certificate>,
    ) {
        val currentPins = pinsByServer[serverId].orEmpty()
        upsert(serverId, ServerTrust(currentPins, certificates))
        customCasByServer[serverId] = certificates
    }

    override suspend fun clear(serverId: String) {
        dao.deleteByServerId(serverId)
        pinsByServer.remove(serverId)
        customCasByServer.remove(serverId)
    }

    private suspend fun upsert(
        serverId: String,
        trust: ServerTrust,
    ) {
        dao.upsert(trust.toEntity(serverId, clock()))
    }

    private companion object {
        const val TAG = "ServerTrustStore"
    }
}
