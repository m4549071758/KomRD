package dev.komrd.core.network.tls

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

class TlsTrustConfig(
    val trustManager: KomgaTrustManager,
    val sslSocketFactory: SSLSocketFactory,
)

fun tlsTrustConfig(
    serverId: String,
    trustStore: ServerTrustStore,
): TlsTrustConfig {
    val trustManager = KomgaTrustManager(serverId, trustStore)
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, arrayOf(trustManager), null)
    return TlsTrustConfig(trustManager, sslContext.socketFactory)
}
