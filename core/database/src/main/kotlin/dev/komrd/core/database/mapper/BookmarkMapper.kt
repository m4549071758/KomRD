package dev.komrd.core.database.mapper

import dev.komrd.core.database.entity.BookmarkEntity
import dev.komrd.core.model.Bookmark

/** ドメイン[Bookmark] → 永続[BookmarkEntity]。 */
fun Bookmark.toEntity(): BookmarkEntity =
    BookmarkEntity(
        serverId = serverId,
        bookId = bookId,
        pageNumber = pageNumber,
        note = note,
        createdAt = createdAt,
    )

/** 永続[BookmarkEntity] → ドメイン[Bookmark]。 */
fun BookmarkEntity.toDomain(): Bookmark =
    Bookmark(
        serverId = serverId,
        bookId = bookId,
        pageNumber = pageNumber,
        note = note,
        createdAt = createdAt,
    )
