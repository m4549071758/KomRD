package dev.komrd.core.data.server

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.ServerSettings
import dev.komrd.core.model.SettingsUpdate
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore

interface ServerSettingsRepository {
    suspend fun get(serverId: String): KomgaResult<ServerSettings>

    suspend fun update(
        serverId: String,
        update: SettingsUpdate,
    ): KomgaResult<Unit>
}

class ServerSettingsRepositoryImpl(
    private val serverRepository: ServerRepository,
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : ServerSettingsRepository {
    override suspend fun get(serverId: String): KomgaResult<ServerSettings> {
        val server = serverRepository.byId(serverId) ?: return KomgaResult.Failure(noSuchServer(serverId))
        trustStore.load(server.id)
        return when (val result = clientFactory.clientFor(server).getSettings()) {
            is KomgaResult.Success -> KomgaResult.Success(result.value.toDomain())
            is KomgaResult.Failure -> result
        }
    }

    @Suppress("ReturnCount") // 空差分・サーバ未存在の早期returnが明示的なため許容
    override suspend fun update(
        serverId: String,
        update: SettingsUpdate,
    ): KomgaResult<Unit> {
        // 差分無しは通信せず成功扱い（空PATCHでサーバへ送信する意味がない）。
        if (update.isEmpty) return KomgaResult.Success(Unit)
        val server = serverRepository.byId(serverId) ?: return KomgaResult.Failure(noSuchServer(serverId))
        trustStore.load(server.id)
        return clientFactory.clientFor(server).updateSettings(update.toDto())
    }

    private fun noSuchServer(serverId: String): dev.komrd.core.common.error.KomgaError =
        dev.komrd.core.common.error.KomgaError
            .Unknown("Server $serverId not found")
}
