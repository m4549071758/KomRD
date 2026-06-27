package dev.komrd.core.data.prefetch

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClient
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.dto.BookDto
import dev.komrd.core.network.tls.ServerTrustStore

interface NextBookResolver {
    suspend fun resolve(
        server: Server,
        currentBookId: String,
        context: ReadingContext,
    ): KomgaResult<NextBook?>
}

class NextBookResolverImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : NextBookResolver {
    override suspend fun resolve(
        server: Server,
        currentBookId: String,
        context: ReadingContext,
    ): KomgaResult<NextBook?> {
        trustStore.load(server.id)
        val client = clientFactory.clientFor(server)
        return when (context) {
            is ReadingContext.Series -> resolveInSeries(client, context.id, currentBookId)
            is ReadingContext.ReadList -> resolveInReadList(client, context.id, currentBookId)
        }
    }

    /** Series内をnumberSort昇順で全取得し、currentの次を解決。巻数が多い場合もページングで安全に網羅。 */
    private suspend fun resolveInSeries(
        client: KomgaClient,
        seriesId: String,
        currentBookId: String,
    ): KomgaResult<NextBook?> {
        val sort = listOf("metadata.numberSort,asc")
        val all = mutableListOf<BookDto>()
        var page = 0
        while (page < MAX_PAGES) {
            when (val result = client.listBooksInSeries(seriesId, page = page, size = PAGE_SIZE, sort = sort)) {
                is KomgaResult.Failure -> return result
                is KomgaResult.Success -> {
                    val p = result.value
                    all += p.content
                    if (p.last || p.content.isEmpty()) break
                    page++
                }
            }
        }
        return KomgaResult.Success(nextAfter(client, all, currentBookId))
    }

    /** Read Listの並び順でcurrentの次を解決。 */
    private suspend fun resolveInReadList(
        client: KomgaClient,
        readListId: String,
        currentBookId: String,
    ): KomgaResult<NextBook?> =
        when (val result = client.listReadListBooks(readListId)) {
            is KomgaResult.Failure -> result
            is KomgaResult.Success -> KomgaResult.Success(nextAfter(client, result.value, currentBookId))
        }

    /** books中のcurrentの次のBookを[NextBook]へ。current不在・末尾ならnull。 */
    private suspend fun nextAfter(
        client: KomgaClient,
        books: List<BookDto>,
        currentBookId: String,
    ): NextBook? {
        val idx = books.indexOfFirst { it.id == currentBookId }
        if (idx < 0 || idx >= books.lastIndex) return null
        return toNextBook(client, books[idx + 1])
    }

    /** dto.media.pagesCount優先、欠落時はgetBookでフォールバック。いずれも不明ならnull(次冊なし扱い)。 */
    private suspend fun toNextBook(
        client: KomgaClient,
        dto: BookDto,
    ): NextBook? {
        dto.media.pagesCount
            ?.takeIf { it > 0 }
            ?.let { return NextBook(dto.id, it) }
        return when (val bookResult = client.getBook(dto.id)) {
            is KomgaResult.Success ->
                bookResult.value.media.pagesCount
                    ?.takeIf { it > 0 }
                    ?.let { NextBook(dto.id, it) }
            is KomgaResult.Failure -> null
        }
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 100
    }
}
