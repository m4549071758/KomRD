package dev.komrd.core.database.mapper

import dev.komrd.core.database.entity.ServerTrustEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64

private val fingerprintsJson = Json { ignoreUnknownKeys = true }

private val fingerprintsSerializer = ListSerializer(String.serializer())

/** PEMのBEGIN/END行を除去して証明書本体だけにする。 */
private fun List<X509Certificate>.toPemConcatenated(): String =
    joinToString(separator = "\n") { cert ->
        val lineSeparator = byteArrayOf('\n'.code.toByte())
        val encoder = Base64.getMimeEncoder(PEM_LINE_LENGTH, lineSeparator)
        val base64 = encoder.encodeToString(cert.encoded)
        "$PEM_BEGIN_CERT\n$base64\n$PEM_END_CERT"
    }

private fun String.decodePemChain(): List<X509Certificate> {
    if (isBlank()) return emptyList()
    val factory = CertificateFactory.getInstance("X.509")
    val pemBlocks = split(PEM_END_CERT).filter { it.isNotBlank() }
    return pemBlocks.mapNotNull { block ->
        val base64 =
            block
                .lineSequence()
                .filter { line -> line.isNotBlank() && !line.startsWith("-----") }
                .joinToString(separator = "")
        if (base64.isBlank()) return@mapNotNull null
        val bytes = Base64.getMimeDecoder().decode(base64)
        factory.generateCertificate(ByteArrayInputStream(bytes)) as X509Certificate
    }
}

/** ドメイン[ServerTrust] → 永続[ServerTrustEntity]。 */
fun ServerTrust.toEntity(
    serverId: String,
    updatedAt: Long,
): ServerTrustEntity =
    ServerTrustEntity(
        serverId = serverId,
        pinnedFingerprintsJson = fingerprintsJson.encodeToString(fingerprintsSerializer, pinnedFingerprints.toList()),
        customCaCertsPem = customCaCertificates.toPemConcatenated(),
        updatedAt = updatedAt,
    )

class InvalidServerTrustDataException(
    val serverId: String,
    cause: Throwable,
) : Exception("Failed to decode server trust data for server $serverId.", cause)

fun ServerTrustEntity.toDomain(): ServerTrust =
    ServerTrust(
        pinnedFingerprints =
            runCatching {
                fingerprintsJson.decodeFromString(fingerprintsSerializer, pinnedFingerprintsJson).toSet()
            }.getOrElse { throw InvalidServerTrustDataException(serverId, it) },
        customCaCertificates =
            runCatching { customCaCertsPem.decodePemChain() }
                .getOrElse { throw InvalidServerTrustDataException(serverId, it) },
    )

private const val PEM_LINE_LENGTH = 64
private const val PEM_BEGIN_CERT = "-----BEGIN CERTIFICATE-----"
private const val PEM_END_CERT = "-----END CERTIFICATE-----"
