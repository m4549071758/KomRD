package dev.komrd.core.data.server

import android.util.Log
import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.database.crypto.SecretCipher
import dev.komrd.core.database.dao.ServersDao
import dev.komrd.core.database.mapper.toDomain
import dev.komrd.core.database.mapper.toEntity
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.security.cert.X509Certificate

interface ServerRepository {
    val servers: Flow<List<Server>>

    suspend fun byId(id: String): Server?

    suspend fun add(server: Server)

    suspend fun update(server: Server)

    suspend fun delete(id: String)

    suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult>

    suspend fun pinCertificate(
        serverId: String,
        certificate: CertificateInfo,
    ): KomgaResult<Unit>

    suspend fun pinCustomCa(
        serverId: String,
        certificates: List<X509Certificate>,
    ): KomgaResult<Unit>

    fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean

    fun certificateInfoOf(error: KomgaError): CertificateInfo?
}

/**
 * [ServerRepository]の標準実装。
 *
 * - [add]/[update]/[delete]でDB永続化と[KomgaClientFactory.invalidate]を連動させる。
 * - [verifyConnection]/[pinCertificate]/[pinCustomCa]で接続・ピン留め後に[invalidate]し、
 *   旧OkHttpにピン未反映が残らないようにする（計画リスク4）。
 */
class ServerRepositoryImpl(
    private val serversDao: ServersDao,
    private val cipher: SecretCipher,
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
    private val invalidateImageLoaders: (serverId: String) -> Unit = {},
    private val clock: () -> Long = System::currentTimeMillis,
) : ServerRepository {
    // 復号失敗(KeyPermanentlyInvalidatedException等)でFlow全体が終了しないよう、1件の復号失敗は
    override val servers: Flow<List<Server>> =
        serversDao
            .observeAll()
            .map { entities ->
                entities.mapNotNull { entity ->
                    runCatching { entity.toDomain(cipher) }
                        .getOrElse { error ->
                            Log.w(TAG, "Failed to decrypt server ${entity.id}, skipping.", error)
                            null
                        }
                }
            }.flowOn(Dispatchers.IO)

    override suspend fun byId(id: String): Server? = serversDao.findById(id)?.toDomain(cipher)

    override suspend fun add(server: Server) {
        serversDao.upsert(server.toEntity(cipher, createdAt = clock()))
    }

    override suspend fun update(server: Server) {
        val existing =
            serversDao.findById(server.id)
                ?: throw NoSuchElementException("Server ${server.id} not found")
        serversDao.upsert(server.toEntity(cipher, createdAt = existing.createdAt))
        clientFactory.invalidate(server.id)
        invalidateImageLoaders(server.id)
    }

    override suspend fun delete(id: String) {
        serversDao.deleteById(id)
        trustStore.clear(id)
        clientFactory.invalidate(id)
        invalidateImageLoaders(id)
    }

    override suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult> {
        trustStore.load(server.id)
        return clientFactory.clientFor(server).verifyConnection()
    }

    override suspend fun pinCertificate(
        serverId: String,
        certificate: CertificateInfo,
    ): KomgaResult<Unit> {
        trustStore.load(serverId)
        // TOFU: 確認した単一のリーフ指紋へ「置換」する。merge にすると旧証明書の指紋が信頼済みに残り、
        trustStore.replacePins(serverId, setOf(certificate.sha256Fingerprint))
        clientFactory.invalidate(serverId)
        return KomgaResult.Success(Unit)
    }

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<X509Certificate>,
    ): KomgaResult<Unit> {
        trustStore.load(serverId)
        trustStore.replaceCustomCas(serverId, certificates)
        clientFactory.invalidate(serverId)
        return KomgaResult.Success(Unit)
    }

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean {
        val existing = trustStore.pinnedCertificateFingerprints(serverId)
        return existing.isNotEmpty() && newFingerprint !in existing
    }

    override fun certificateInfoOf(error: KomgaError): CertificateInfo? {
        val untrusted = error as? KomgaError.UntrustedCertificate ?: return null
        return untrusted.certificate
    }

    private companion object {
        private const val TAG = "ServerRepository"
    }
}
