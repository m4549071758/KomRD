package dev.komrd.core.data.search

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import dev.komrd.core.model.Book
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import kotlinx.coroutines.flow.Flow

open class SearchRepository(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) {
    fun searchSeriesPager(
        server: Server,
        query: String,
        libraryId: String? = null,
    ): Flow<PagingData<Series>> =
        Pager(config = pagingConfig()) {
            SearchSeriesPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                query = query,
                libraryId = libraryId,
                trustStore = trustStore,
            )
        }.flow

    fun searchBooksPager(
        server: Server,
        query: String,
        libraryId: String? = null,
    ): Flow<PagingData<Book>> =
        Pager(config = pagingConfig()) {
            SearchBookPagingSource(
                client = clientFactory.clientFor(server),
                server = server,
                query = query,
                libraryId = libraryId,
                trustStore = trustStore,
            )
        }.flow

    private fun pagingConfig() =
        PagingConfig(
            pageSize = SEARCH_PAGE_SIZE,
            enablePlaceholders = false,
        )

    private companion object {
        const val SEARCH_PAGE_SIZE = 20
    }
}
