package dev.komrd.core.database.mapper

import dev.komrd.core.database.entity.ServerTrustEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Arrays

class ServerTrustMapperTest {
    @Test
    fun roundTrip_preservesPins_andServerIdAndUpdatedAt() {
        val trust = ServerTrust(pinnedFingerprints = setOf("AA:BB", "CC:DD"), customCaCertificates = emptyList())

        val entity = trust.toEntity(serverId = "s1", updatedAt = 100L)
        val back = entity.toDomain()

        assertEquals("s1", entity.serverId)
        assertEquals(100L, entity.updatedAt)
        assertEquals(setOf("AA:BB", "CC:DD"), back.pinnedFingerprints)
        assertTrue(back.customCaCertificates.isEmpty())
    }

    @Test
    fun roundTrip_preservesCustomCaCertificate() {
        val cert = parsePem(SAMPLE_CA_CERT_PEM)
        val trust = ServerTrust(pinnedFingerprints = emptySet(), customCaCertificates = listOf(cert))

        val entity = trust.toEntity(serverId = "s1", updatedAt = 1L)
        val back = entity.toDomain()

        assertEquals(1, back.customCaCertificates.size)
        val decoded = back.customCaCertificates.single()
        // DER encodedが一致すれば subject/公開鍵/シリアル/署名まで完全に保存復元されている。
        assertTrue(Arrays.equals(cert.encoded, decoded.encoded))
        assertEquals(cert.subjectX500Principal, decoded.subjectX500Principal)
    }

    @Test
    fun emptyTrust_roundTripsToEmpty() {
        val entity = ServerTrust(emptySet(), emptyList()).toEntity(serverId = "s1", updatedAt = 1L)
        val back = entity.toDomain()

        assertTrue(back.pinnedFingerprints.isEmpty())
        assertTrue(back.customCaCertificates.isEmpty())
    }

    @Test
    fun corruptPinsJson_throwsInvalidServerTrustData() {
        val entity =
            ServerTrustEntity(
                serverId = "s1",
                pinnedFingerprintsJson = "{ not valid json",
                customCaCertsPem = "",
                updatedAt = 1L,
            )

        assertThrows(InvalidServerTrustDataException::class.java) { entity.toDomain() }
    }

    @Test
    fun corruptPem_throwsInvalidServerTrustData() {
        val entity =
            ServerTrustEntity(
                serverId = "s1",
                pinnedFingerprintsJson = "[]",
                customCaCertsPem = "-----BEGIN CERTIFICATE-----\nZZZZ\n-----END CERTIFICATE-----",
                updatedAt = 1L,
            )

        assertThrows(InvalidServerTrustDataException::class.java) { entity.toDomain() }
    }

    private fun parsePem(pem: String): X509Certificate =
        CertificateFactory
            .getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(pem.toByteArray())) as X509Certificate
}

/** テスト用自己署名証明書(RSA 2048, CN=komrd-test, 有効100年)。 */
private val SAMPLE_CA_CERT_PEM =
    """
    -----BEGIN CERTIFICATE-----
    MIICzzCCAbegAwIBAgIIVmrens1HMvswDQYJKoZIhvcNAQEMBQAwFTETMBEGA1UE
    AxMKa29tcmQtdGVzdDAgFw0yNjA2MjYwNTQxNThaGA8yMTI2MDYwMjA1NDE1OFow
    FTETMBEGA1UEAxMKa29tcmQtdGVzdDCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
    AQoCggEBAKT/CFMsnarh7ouUzRbe7AC0R++2LlBOGXIeDBzsIv860Lr20dXXl0Jp
    AYOHoYpJqooKUI1Zdnya3vyF2XBK43FcowXm8rE4vyE9aAWYRBwaScGBZduGAn57
    urFItxBmsL7VruBMn3owqEeNGP61bVDWOys0yHuNJgxxgSpf0CLdkkxXdQSiVDkl
    8TmaNseH2hKjy/6s0kutWZHYi6ldmRQ/p1VsThs4n8TuR7qrMBvkwA9llRZF044F
    qJTpqSvHVM2vMtJJdmYCmhcndeDn7xpiwLicWSpoyBJNpgDWrxY92p/q2GVO4d0s
    63w0CP26s2iW+EYeLtRmmvqJrlmNDPECAwEAAaMhMB8wHQYDVR0OBBYEFJoLTs+/
    zP+IwaG9foJ2TmXC5BhtMA0GCSqGSIb3DQEBDAUAA4IBAQBJc1fVuOWYd7eEiL5E
    QdHF3dGsQEfQcC2j2FvNmAqurvvauiahjWN+R9T7J6jqpUMc6pqXWpgtiINNORsW
    NtzUsQdS+dFIbUiL53DbMG8OodNRoqkiirWKr21XkIltB7iPNQYywZShu2vnkwlt
    74Y3Yher9b+8yfRXZXS7TbOmvEhtP75aHkBpcQZPhMEpLu49YUrTEcogLCeUsbYZ
    4BVDcrDX5LTUjNC4O7N6TJpH5Q//W/jtKicpl1ffZKmGV6o2UJF2SFAIpegIEdnA
    bWIV7PS/VnY0CEL1mqiBiqg5N7TTWJrgV17I5DyycrMexRadOzN8gcKE99y/k3DM
    7UIF
    -----END CERTIFICATE-----
    """.trimIndent()
