package dev.komrd.core.data.server

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.UserAccount
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore

interface UserRepository {
    suspend fun currentUser(serverId: String): KomgaResult<UserAccount>

    fun isAdmin(user: UserAccount): Boolean
}

class UserRepositoryImpl(
    private val serverRepository: ServerRepository,
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : UserRepository {
    override suspend fun currentUser(serverId: String): KomgaResult<UserAccount> {
        val server = serverRepository.byId(serverId) ?: return KomgaResult.Failure(noSuchServer(serverId))
        trustStore.load(server.id)
        return when (val result = clientFactory.clientFor(server).getCurrentUser()) {
            is KomgaResult.Success -> KomgaResult.Success(result.value.toDomain())
            is KomgaResult.Failure -> result
        }
    }

    override fun isAdmin(user: UserAccount): Boolean = ROLE_ADMIN in user.roles

    private fun noSuchServer(serverId: String): dev.komrd.core.common.error.KomgaError =
        dev.komrd.core.common.error.KomgaError
            .Unknown("Server $serverId not found")

    private companion object {
        const val ROLE_ADMIN = "ADMIN"
    }
}
