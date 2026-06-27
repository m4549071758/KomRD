package dev.komrd.core.common.error

import java.time.Instant

/**
 * TLS信頼導線でUIへ提示する証明書情報。
 *
 * SHA-256指紋は`AA:BB:...`形式の大文字hexで保持する。
 */
data class CertificateInfo(
    val sha256Fingerprint: String,
    val subject: String,
    val issuer: String,
    val notBefore: Instant,
    val notAfter: Instant,
)
