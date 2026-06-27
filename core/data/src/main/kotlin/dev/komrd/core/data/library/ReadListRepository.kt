package dev.komrd.core.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.Book
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import kotlinx.coroutines.flow.Flow

interface ReadListRepository {
    suspend fun readLists(server: Server): KomgaResult<List<ReadListSummary>>

    fun booksPager(
        server: Server,
        readListId: String,
    ): Flow<PagingData<Book>>
}

class ReadListRepositoryImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : ReadListRepository {
    override suspend fun readLists(server: Server): KomgaResult<List<ReadListSummary>> {
        trustStore.load(server.id)
        return when (val result = clientFactory.clientFor(server).listReadLists()) {
            is KomgaResult.Success -> KomgaResult.Success(result.value.map { it.toDomain(server) })
            is KomgaResult.Failure -> result
        }
    }

    override fun booksPager(
        server: Server,
        readListId: String,
    ): Flow<PagingData<Book>> =
        Pager(config = pagingConfig()) {
            ReadListBookPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                readListId = readListId,
                trustStore = trustStore,
            )
        }.flow

    private fun pagingConfig() =
        PagingConfig(
            pageSize = LibraryRepository.PAGE_SIZE,
            enablePlaceholders = false,
        )
}
