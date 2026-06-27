package dev.komrd.core.network.tls

import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class KomgaTrustManager(
    private val serverId: String,
    private val trustStore: ServerTrustStore,
    private val systemTrustManager: X509TrustManager = defaultTrustManager(),
) : X509TrustManager {
    // by lazy は初回ハンドシェイク時にキャッシュが空だと空リストで固定され、以後 load() で
    private val customCaTrustManagers: List<X509TrustManager>
        get() = trustStore.customCaCertificates(serverId).map(::trustManagerForCustomCa)

    override fun checkClientTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) = systemTrustManager.checkClientTrusted(chain, authType)

    override fun checkServerTrusted(
        chain: Array<out X509Certificate>?,
        authType: String?,
    ) {
        val certificates =
            chain?.takeIf(Array<out X509Certificate>::isNotEmpty)
                ?: throw CertificateException("Server certificate chain is empty.")
        val leaf = certificates.first()
        val pinnedFingerprints = trustStore.pinnedCertificateFingerprints(serverId)

        if (pinnedFingerprints.isNotEmpty()) {
            requirePinnedCertificateMatch(pinnedFingerprints, leaf)
        } else {
            requireTrustedBySystemOrCustomCa(certificates, authType, leaf)
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> =
        (
            systemTrustManager.acceptedIssuers.asList() +
                customCaTrustManagers.flatMap { it.acceptedIssuers.asList() }
        ).toTypedArray()

    private fun Set<String>.matches(certificate: X509Certificate): Boolean {
        val actual = certificate.sha256Fingerprint().normalizedFingerprint()
        return any { it.normalizedFingerprint() == actual }
    }

    private fun requirePinnedCertificateMatch(
        pinnedFingerprints: Set<String>,
        leaf: X509Certificate,
    ) {
        if (!pinnedFingerprints.matches(leaf)) {
            throw leaf.untrusted("Pinned TLS certificate does not match for server $serverId.", null)
        }
    }

    private fun requireTrustedBySystemOrCustomCa(
        certificates: Array<out X509Certificate>,
        authType: String?,
        leaf: X509Certificate,
    ) {
        val systemFailure =
            runCatching {
                systemTrustManager.checkServerTrusted(certificates, authType)
            }.exceptionOrNull()
        val customCaTrusted =
            systemFailure != null &&
                customCaTrustManagers.any { trustManager ->
                    runCatching { trustManager.checkServerTrusted(certificates, authType) }.isSuccess
                }

        if (systemFailure != null && !customCaTrusted) {
            throw leaf.untrusted("TLS certificate is not trusted for server $serverId.", systemFailure)
        }
    }

    private fun X509Certificate.untrusted(
        message: String,
        cause: Throwable?,
    ) = UntrustedServerCertificateException(toCertificateInfo(), message, cause)
}

private fun defaultTrustManager(): X509TrustManager {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(null as KeyStore?)
    return trustManagerFactory.x509TrustManager()
}

private fun trustManagerForCustomCa(certificate: X509Certificate): X509TrustManager {
    val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
    keyStore.load(null, null)
    keyStore.setCertificateEntry("custom-ca", certificate)

    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(keyStore)
    return trustManagerFactory.x509TrustManager()
}

private fun TrustManagerFactory.x509TrustManager(): X509TrustManager =
    trustManagers
        .filterIsInstance<X509TrustManager>()
        .singleOrNull()
        ?: error("Expected exactly one X509TrustManager.")
