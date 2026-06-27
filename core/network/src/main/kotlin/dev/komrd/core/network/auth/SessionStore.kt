package dev.komrd.core.network.auth

interface SessionStore {
    fun getToken(serverId: String): String?

    fun putToken(
        serverId: String,
        token: String,
    )

    fun clearToken(serverId: String)
}

class InMemorySessionStore : SessionStore {
    private val tokens = java.util.concurrent.ConcurrentHashMap<String, String>()

    override fun getToken(serverId: String): String? = tokens[serverId]

    override fun putToken(
        serverId: String,
        token: String,
    ) {
        tokens[serverId] = token
    }

    override fun clearToken(serverId: String) {
        tokens.remove(serverId)
    }
}
