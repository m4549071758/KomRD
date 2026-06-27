package dev.komrd.core.data.library

import dev.komrd.core.model.Collection
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaThumbnails
import dev.komrd.core.network.dto.CollectionDto
import dev.komrd.core.network.dto.ReadListDto

internal fun CollectionDto.toDomain(server: Server): Collection =
    Collection(
        id = id,
        serverId = server.id,
        name = name,
        seriesCount = seriesIds.size,
        thumbnailUrl = KomgaThumbnails.collectionThumbnailUrl(server.baseUrl, id),
    )

internal fun ReadListDto.toDomain(server: Server): ReadListSummary =
    ReadListSummary(
        id = id,
        serverId = server.id,
        name = name,
        bookCount = bookIds.size,
        thumbnailUrl = KomgaThumbnails.readListThumbnailUrl(server.baseUrl, id),
        summary = summary,
    )
