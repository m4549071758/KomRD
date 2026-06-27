package dev.komrd.core.data.reader

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookOverview
import dev.komrd.core.model.Server
import dev.komrd.core.model.toBookMediaProfile
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.KomgaThumbnails
import dev.komrd.core.network.tls.ServerTrustStore

interface BookOverviewRepository {
    suspend fun loadOverview(
        server: Server,
        bookId: String,
    ): KomgaResult<BookOverview>
}

class BookOverviewRepositoryImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : BookOverviewRepository {
    override suspend fun loadOverview(
        server: Server,
        bookId: String,
    ): KomgaResult<BookOverview> {
        trustStore.load(server.id)
        val client = clientFactory.clientFor(server)
        return when (val bookResult = client.getBook(bookId)) {
            is KomgaResult.Failure -> bookResult
            is KomgaResult.Success -> {
                val dto = bookResult.value
                val seriesName =
                    dto.seriesId?.let { seriesId ->
                        when (val seriesResult = client.getSeries(seriesId)) {
                            is KomgaResult.Success -> seriesResult.value.name
                            is KomgaResult.Failure -> null
                        }
                    }
                KomgaResult.Success(
                    BookOverview(
                        id = dto.id,
                        serverId = server.id,
                        name = dto.metadata.title?.takeIf { it.isNotBlank() } ?: dto.name,
                        seriesName = seriesName,
                        pagesCount = dto.media.pagesCount ?: 0,
                        mediaType = dto.media.mediaType,
                        thumbnailUrl = KomgaThumbnails.bookThumbnailUrl(server.baseUrl, dto.id),
                        mediaProfile = dto.media.mediaProfile.toBookMediaProfile(),
                    ),
                )
            }
        }
    }
}
