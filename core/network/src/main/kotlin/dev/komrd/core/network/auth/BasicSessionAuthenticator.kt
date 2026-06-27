package dev.komrd.core.network.auth

import dev.komrd.core.model.AuthMethod
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class BasicSessionAuthenticator(
    private val serverId: String,
    private val auth: AuthMethod.Basic,
    private val sessionStore: SessionStore,
) : Authenticator {
    override fun authenticate(
        route: Route?,
        response: Response,
    ): Request? {
        // 再試行上限超過時も諦める。
        if (response.request.isBasicSessionLoginAttempt() || response.responseCount >= MAX_AUTH_ATTEMPTS) {
            return null
        }

        sessionStore.clearToken(serverId)
        return response.request
            .newBuilder()
            .basicSessionLogin(auth.username, auth.password)
            .build()
    }

    private val Response.responseCount: Int
        get() {
            var count = 1
            var current = priorResponse
            while (current != null) {
                count++
                current = current.priorResponse
            }
            return count
        }

    private companion object {
        const val MAX_AUTH_ATTEMPTS = 2
    }
}
