package dev.komrd.core.data.library

import dev.komrd.core.model.Book
import dev.komrd.core.model.Library
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaThumbnails
import dev.komrd.core.network.dto.BookDto
import dev.komrd.core.network.dto.LibraryDto
import dev.komrd.core.network.dto.SeriesDto

internal fun LibraryDto.toDomain(serverId: String): Library =
    Library(
        id = id,
        serverId = serverId,
        name = name,
    )

internal fun SeriesDto.toDomain(server: Server): Series =
    Series(
        id = id,
        serverId = server.id,
        libraryId = libraryId,
        name = metadata.title?.takeIf { it.isNotBlank() } ?: name,
        thumbnailUrl = KomgaThumbnails.seriesThumbnailUrl(server.baseUrl, id),
    )

internal fun BookDto.toDomain(server: Server): Book =
    Book(
        id = id,
        serverId = server.id,
        seriesId = seriesId,
        name = metadata.title?.takeIf { it.isNotBlank() } ?: name,
        thumbnailUrl = KomgaThumbnails.bookThumbnailUrl(server.baseUrl, id),
    )
