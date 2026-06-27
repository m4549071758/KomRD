package dev.komrd.core.network.error

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.network.HTTP_UNAUTHORIZED
import dev.komrd.core.network.tls.UntrustedServerCertificateException
import kotlinx.serialization.SerializationException
import retrofit2.HttpException
import java.io.IOException

fun Throwable.toKomgaError(): KomgaError =
    when (this) {
        is HttpException -> toHttpKomgaError()
        is SerializationException -> KomgaError.Serialization(message ?: "Failed to parse Komga response.")
        is IOException -> toNetworkKomgaError()
        else -> KomgaError.Unknown(message ?: "Unexpected Komga error.")
    }

private fun HttpException.toHttpKomgaError(): KomgaError {
    val message = message()
    return if (code() == HTTP_UNAUTHORIZED) {
        KomgaError.Unauthorized(message)
    } else {
        KomgaError.Http(code(), message)
    }
}

private fun IOException.toNetworkKomgaError(): KomgaError {
    val untrustedCertificate =
        causeChain()
            .filterIsInstance<UntrustedServerCertificateException>()
            .firstOrNull()

    return if (untrustedCertificate == null) {
        KomgaError.Network(message ?: "Network error while communicating with Komga.")
    } else {
        KomgaError.UntrustedCertificate(
            certificate = untrustedCertificate.certificateInfo,
            message = untrustedCertificate.message ?: "Komga TLS certificate is not trusted.",
        )
    }
}

private fun Throwable.causeChain(): Sequence<Throwable> = generateSequence(this) { it.cause }
