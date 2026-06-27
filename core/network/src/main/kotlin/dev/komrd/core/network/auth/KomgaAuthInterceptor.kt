package dev.komrd.core.network.auth

import dev.komrd.core.model.AuthMethod
import okhttp3.Interceptor
import okhttp3.Response

class KomgaAuthInterceptor(
    private val serverId: String,
    private val auth: AuthMethod,
    private val sessionStore: SessionStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val authenticatedRequest =
            chain
                .request()
                .newBuilder()
                .applyAuth()
                .build()

        val response = chain.proceed(authenticatedRequest)
        captureBasicSessionToken(response)
        return response
    }

    private fun okhttp3.Request.Builder.applyAuth() =
        apply {
            when (auth) {
                is AuthMethod.ApiKey -> apiKey(auth.key)
                is AuthMethod.Basic -> {
                    val token = sessionStore.getToken(serverId)
                    if (token == null) {
                        basicSessionLogin(auth.username, auth.password)
                    } else {
                        sessionToken(token)
                    }
                }
            }
        }

    private fun captureBasicSessionToken(response: Response) {
        if (auth !is AuthMethod.Basic) return

        val token =
            response
                .header(X_AUTH_TOKEN)
                ?.takeIf(String::isNotBlank)
                ?.takeUnless { it.equals("true", ignoreCase = true) }
                ?: return
        sessionStore.putToken(serverId, token)
    }
}
