package dev.komrd.core.database.mapper

import java.security.cert.X509Certificate

/**
 * サーバ単位のTLS信頼情報のドメイン表現。永続化は[ServerTrustEntity]経由で行う。
 *
 * - [pinnedFingerprints]: ピン留めされたSHA-256指紋のSet（空ならピン留め未使用）。
 * - [customCaCertificates]: 追加で信頼するCA証明書。
 */
data class ServerTrust(
    val pinnedFingerprints: Set<String>,
    val customCaCertificates: List<X509Certificate>,
)
