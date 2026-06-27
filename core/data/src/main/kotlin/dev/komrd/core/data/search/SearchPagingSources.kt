package dev.komrd.core.data.search

import androidx.paging.PagingSource
import androidx.paging.PagingState
import dev.komrd.core.common.error.toException
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.library.toDomain
import dev.komrd.core.model.Book
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClient
import dev.komrd.core.network.tls.ServerTrustStore

internal class SearchSeriesPagingSource(
    private val client: KomgaClient,
    private val server: Server,
    private val query: String,
    private val libraryId: String?,
    private val trustStore: ServerTrustStore,
) : PagingSource<Int, Series>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Series> {
        val page = params.key ?: 0
        if (params.key == null) trustStore.load(server.id)
        val result = client.searchSeries(query = query, libraryId = libraryId, page = page, size = params.loadSize)
        return when (result) {
            is KomgaResult.Success -> {
                val dto = result.value
                val items = dto.content.map { it.toDomain(server) }
                LoadResult.Page(
                    data = items,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (dto.last || items.isEmpty()) null else page + 1,
                )
            }
            is KomgaResult.Failure -> LoadResult.Error(result.error.toException())
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Series>): Int? = defaultRefreshKey(state)
}

internal class SearchBookPagingSource(
    private val client: KomgaClient,
    private val server: Server,
    private val query: String,
    private val libraryId: String?,
    private val trustStore: ServerTrustStore,
) : PagingSource<Int, Book>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Book> {
        val page = params.key ?: 0
        if (params.key == null) trustStore.load(server.id)
        val result = client.searchBooks(query = query, libraryId = libraryId, page = page, size = params.loadSize)
        return when (result) {
            is KomgaResult.Success -> {
                val dto = result.value
                val items = dto.content.map { it.toDomain(server) }
                LoadResult.Page(
                    data = items,
                    prevKey = if (page == 0) null else page - 1,
                    nextKey = if (dto.last || items.isEmpty()) null else page + 1,
                )
            }
            is KomgaResult.Failure -> LoadResult.Error(result.error.toException())
        }
    }

    override fun getRefreshKey(state: PagingState<Int, Book>): Int? = defaultRefreshKey(state)
}

/** PagingSource標準のリフレッシュキー算出（前後ページから推定）。 */
internal fun <T : Any> defaultRefreshKey(state: PagingState<Int, T>): Int? =
    state.anchorPosition?.let { anchor ->
        state.closestPageToPosition(anchor)?.let { page ->
            page.prevKey?.plus(1) ?: page.nextKey?.minus(1)
        }
    }
