package dev.komrd.core.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.Collection
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    suspend fun collections(server: Server): KomgaResult<List<Collection>>

    fun seriesPager(
        server: Server,
        collectionId: String,
    ): Flow<PagingData<Series>>
}

class CollectionRepositoryImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : CollectionRepository {
    override suspend fun collections(server: Server): KomgaResult<List<Collection>> {
        trustStore.load(server.id)
        return when (val result = clientFactory.clientFor(server).listCollections()) {
            is KomgaResult.Success -> KomgaResult.Success(result.value.map { it.toDomain(server) })
            is KomgaResult.Failure -> result
        }
    }

    override fun seriesPager(
        server: Server,
        collectionId: String,
    ): Flow<PagingData<Series>> =
        Pager(config = pagingConfig()) {
            CollectionSeriesPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                collectionId = collectionId,
                trustStore = trustStore,
            )
        }.flow

    private fun pagingConfig() =
        PagingConfig(
            pageSize = LibraryRepository.PAGE_SIZE,
            enablePlaceholders = false,
        )
}
