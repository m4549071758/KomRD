package dev.komrd.core.network.tls

import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap

/**
 * サーバ単位のTLS信頼情報（ピン留め指紋・カスタムCA）のストア。
 *
 * 読み込み（[pinnedCertificateFingerprints]/[customCaCertificates]）はSSL handshake中に同期呼び出しされるため、
 * メモリキャッシュのみを参照する。DB等へのI/Oは[load]/[loadAll]/[save]/[clear]でバックグラウンドに行う。
 */
interface ServerTrustStore {
    fun pinnedCertificateFingerprints(serverId: String): Set<String>

    fun customCaCertificates(serverId: String): List<X509Certificate>

    /** 指定サーバの信頼情報をストア（DB等）からキャッシュへ読み込む。 */
    suspend fun load(serverId: String)

    /** 全サーバの信頼情報をキャッシュへ読み込む（起動時等）。 */
    suspend fun loadAll()

    /** ピン留め指紋を上書きする。既存のカスタムCAは保持する。 */
    suspend fun replacePins(
        serverId: String,
        fingerprints: Set<String>,
    )

    /** カスタムCA証明書を上書きする。既存のピン留め指紋は保持する。 */
    suspend fun replaceCustomCas(
        serverId: String,
        certificates: List<X509Certificate>,
    )

    /** 指定サーバの信頼情報をキャッシュとストアから消去する。 */
    suspend fun clear(serverId: String)
}

/** テスト用のメモリ実装。 */
class InMemoryServerTrustStore : ServerTrustStore {
    private val pinsByServer = ConcurrentHashMap<String, Set<String>>()
    private val customCasByServer = ConcurrentHashMap<String, List<X509Certificate>>()

    override fun pinnedCertificateFingerprints(serverId: String): Set<String> = pinsByServer[serverId].orEmpty()

    override fun customCaCertificates(serverId: String): List<X509Certificate> = customCasByServer[serverId].orEmpty()

    override suspend fun load(serverId: String) {
        // メモリ実装はキャッシュが真。何もしない。
    }

    override suspend fun loadAll() {
        // メモリ実装はキャッシュが真。何もしない。
    }

    override suspend fun replacePins(
        serverId: String,
        fingerprints: Set<String>,
    ) {
        pinsByServer[serverId] = fingerprints
    }

    override suspend fun replaceCustomCas(
        serverId: String,
        certificates: List<X509Certificate>,
    ) {
        customCasByServer[serverId] = certificates
    }

    override suspend fun clear(serverId: String) {
        pinsByServer.remove(serverId)
        customCasByServer.remove(serverId)
    }
}
