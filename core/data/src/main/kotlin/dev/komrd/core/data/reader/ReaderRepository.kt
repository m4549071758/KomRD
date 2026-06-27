package dev.komrd.core.data.reader

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.Server
import dev.komrd.core.model.toBookMediaProfile
import dev.komrd.core.network.KomgaClient
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.dto.BookDto
import dev.komrd.core.network.tls.ServerTrustStore

interface ReaderRepository {
    suspend fun loadBook(
        server: Server,
        bookId: String,
    ): KomgaResult<BookDetail>
}

class ReaderRepositoryImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
    private val dimensionResolver: PageDimensionResolver = NoOpPageDimensionResolver,
) : ReaderRepository {
    override suspend fun loadBook(
        server: Server,
        bookId: String,
    ): KomgaResult<BookDetail> {
        trustStore.load(server.id)
        val client = clientFactory.clientFor(server)
        return when (val bookResult = client.getBook(bookId)) {
            is KomgaResult.Failure -> bookResult
            is KomgaResult.Success -> loadPagesOrEmpty(server, client, bookId, bookResult.value)
        }
    }

    /**
     * EPUBなら`getBookPages`を呼ばず空ページで[BookDetail]構築(本文はEpubRepository経由)。
     * 画像系(PDF/DIVINA/IMAGE)は従来通り`getBookPages`→寸法解決。
     */
    private suspend fun loadPagesOrEmpty(
        server: Server,
        client: KomgaClient,
        bookId: String,
        book: BookDto,
    ): KomgaResult<BookDetail> {
        if (book.media.mediaProfile.toBookMediaProfile() == BookMediaProfile.EPUB) {
            val detail =
                book.toBookDetail(
                    server = server,
                    pages = emptyList(),
                    seriesReadingDirection = book.resolveSeriesDirection(client),
                )
            return KomgaResult.Success(detail)
        }
        return when (val pagesResult = client.getBookPages(bookId)) {
            is KomgaResult.Failure -> pagesResult
            is KomgaResult.Success -> {
                val detail =
                    book.toBookDetail(
                        server = server,
                        pages = pagesResult.value,
                        seriesReadingDirection = book.resolveSeriesDirection(client),
                    )
                KomgaResult.Success(detail.copy(pages = resolveDimensions(client, bookId, detail.pages)))
            }
        }
    }

    /** null寸法ページを実寸解決(非致命)。失敗時は元のページをそのまま残す。 */
    private suspend fun resolveDimensions(
        client: KomgaClient,
        bookId: String,
        pages: List<BookPage>,
    ): List<BookPage> =
        pages.map { page ->
            if (page.width != null && page.height != null) {
                page
            } else {
                dimensionResolver.resolve(client, bookId, page)
            }
        }
}

/** SeriesのreadingDirection(決定階層 ②)をbest-effortで取得。失敗時はnull。 */
private suspend fun BookDto.resolveSeriesDirection(client: KomgaClient): ReadingDirection? {
    val seriesId = seriesId ?: return null
    return when (val seriesResult = client.getSeries(seriesId)) {
        is KomgaResult.Success -> {
            val direction = seriesResult.value.metadata.readingDirection
            direction.toReadingDirection()
        }
        is KomgaResult.Failure -> null
    }
}
