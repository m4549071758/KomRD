package dev.komrd.core.data.library

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.Book
import dev.komrd.core.model.Library
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    suspend fun libraries(server: Server): KomgaResult<List<Library>>

    fun seriesPager(
        server: Server,
        libraryId: String,
        sort: List<String> = DEFAULT_SERIES_SORT,
        readStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
    ): Flow<PagingData<Series>>

    fun booksPager(
        server: Server,
        seriesId: String,
        sort: List<String> = DEFAULT_BOOK_SORT,
    ): Flow<PagingData<Book>>

    fun readStatusBooksPager(
        server: Server,
        readStatus: String,
        sort: List<String> = DEFAULT_READ_STATUS_SORT,
    ): Flow<PagingData<Book>>

    companion object {
        val DEFAULT_SERIES_SORT = listOf("metadata.titleSort,asc")

        /** Series内Bookの既定ソート=巻数昇順。 */
        val DEFAULT_BOOK_SORT = listOf("metadata.numberSort,asc")

        /** 読書状態別Bookの既定ソート=読書日降順。 */
        val DEFAULT_READ_STATUS_SORT = listOf("readProgress.readDate,desc")

        const val PAGE_SIZE = 20
    }
}

class LibraryRepositoryImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : LibraryRepository {
    override suspend fun libraries(server: Server): KomgaResult<List<Library>> {
        trustStore.load(server.id)
        return when (val result = clientFactory.clientFor(server).listLibraries()) {
            is KomgaResult.Success -> KomgaResult.Success(result.value.map { it.toDomain(server.id) })
            is KomgaResult.Failure -> result
        }
    }

    override fun seriesPager(
        server: Server,
        libraryId: String,
        sort: List<String>,
        readStatusFilter: ReadStatusFilter,
    ): Flow<PagingData<Series>> =
        Pager(config = pagingConfig()) {
            SeriesPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                libraryId = libraryId,
                sort = sort,
                readStatusFilter = readStatusFilter,
                trustStore = trustStore,
            )
        }.flow

    override fun booksPager(
        server: Server,
        seriesId: String,
        sort: List<String>,
    ): Flow<PagingData<Book>> =
        Pager(config = pagingConfig()) {
            BookPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                seriesId = seriesId,
                sort = sort,
                trustStore = trustStore,
            )
        }.flow

    override fun readStatusBooksPager(
        server: Server,
        readStatus: String,
        sort: List<String>,
    ): Flow<PagingData<Book>> =
        Pager(config = pagingConfig()) {
            ReadStatusBookPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                readStatus = readStatus,
                sort = sort,
                trustStore = trustStore,
            )
        }.flow

    private fun pagingConfig() =
        PagingConfig(
            pageSize = LibraryRepository.PAGE_SIZE,
            enablePlaceholders = false,
        )
}
