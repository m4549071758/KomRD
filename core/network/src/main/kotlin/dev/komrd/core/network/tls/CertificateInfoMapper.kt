package dev.komrd.core.network.tls

import dev.komrd.core.common.error.CertificateInfo
import java.security.MessageDigest
import java.security.cert.X509Certificate

fun X509Certificate.toCertificateInfo(): CertificateInfo =
    CertificateInfo(
        sha256Fingerprint = sha256Fingerprint(),
        subject = subjectX500Principal.name,
        issuer = issuerX500Principal.name,
        notBefore = notBefore.toInstant(),
        notAfter = notAfter.toInstant(),
    )

fun X509Certificate.sha256Fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(encoded)
    return digest.joinToString(":") { byte ->
        (byte.toInt() and BYTE_MASK)
            .toString(HEX_RADIX)
            .uppercase()
            .padStart(HEX_BYTE_LENGTH, '0')
    }
}

internal fun String.normalizedFingerprint(): String = replace(":", "").uppercase()

private const val BYTE_MASK = 0xFF
private const val HEX_RADIX = 16
private const val HEX_BYTE_LENGTH = 2
