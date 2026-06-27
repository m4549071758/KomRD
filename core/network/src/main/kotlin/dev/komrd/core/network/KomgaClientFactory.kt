package dev.komrd.core.network

import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.network.api.KomgaApi
import dev.komrd.core.network.auth.BasicSessionAuthenticator
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.auth.KomgaAuthInterceptor
import dev.komrd.core.network.auth.SessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import dev.komrd.core.network.tls.ServerTrustStore
import dev.komrd.core.network.tls.tlsTrustConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.ConcurrentHashMap

class KomgaClientFactory(
    private val sessionStore: SessionStore = InMemorySessionStore(),
    private val trustStore: ServerTrustStore = InMemoryServerTrustStore(),
    private val okHttpClientBuilder: () -> OkHttpClient.Builder = { OkHttpClient.Builder() },
) {
    private val clients = ConcurrentHashMap<String, KomgaClient>()

    fun clientFor(server: Server): KomgaClient =
        clients.computeIfAbsent(server.id) {
            createClient(server)
        }

    fun invalidate(serverId: String) {
        clients.remove(serverId)
        sessionStore.clearToken(serverId)
    }

    private fun createClient(server: Server): KomgaClient {
        val okHttpClient = createOkHttpClient(server)
        val api =
            Retrofit
                .Builder()
                .baseUrl(server.baseUrl.retrofitBaseUrl())
                .client(okHttpClient)
                .addConverterFactory(KomgaJson.asConverterFactory(JSON_MEDIA_TYPE.toMediaType()))
                .build()
                .create(KomgaApi::class.java)

        return KomgaClient(server = server, api = api, okHttpClient = okHttpClient)
    }

    private fun createOkHttpClient(server: Server): OkHttpClient {
        val auth = server.auth
        val tlsTrustConfig = tlsTrustConfig(server.id, trustStore)
        return okHttpClientBuilder()
            .sslSocketFactory(tlsTrustConfig.sslSocketFactory, tlsTrustConfig.trustManager)
            .addInterceptor(KomgaAuthInterceptor(server.id, auth, sessionStore))
            .apply {
                if (auth is AuthMethod.Basic) {
                    authenticator(BasicSessionAuthenticator(server.id, auth, sessionStore))
                }
            }.build()
    }
}

private const val JSON_MEDIA_TYPE = "application/json"
