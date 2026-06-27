package dev.komrd.core.common.error

/**
 * Komga連携で上位層へ返すドメインエラー。
 *
 * 例外そのものをUI層へ漏らさず、再認証導線・TLS信頼導線・リトライ判断に必要な情報だけを持つ。
 */
sealed interface KomgaError {
    val message: String

    data class Unauthorized(
        override val message: String = "Komga authentication failed.",
    ) : KomgaError

    data class Http(
        val statusCode: Int,
        override val message: String,
    ) : KomgaError

    data class Network(
        override val message: String,
    ) : KomgaError

    data class UntrustedCertificate(
        val certificate: CertificateInfo?,
        override val message: String,
    ) : KomgaError

    data class Serialization(
        override val message: String,
    ) : KomgaError

    data class Unknown(
        override val message: String,
    ) : KomgaError
}
