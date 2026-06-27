package dev.komrd.core.data.bookmark

import dev.komrd.core.database.dao.BookmarkDao
import dev.komrd.core.database.entity.BookmarkEntity
import dev.komrd.core.database.mapper.toDomain
import dev.komrd.core.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface BookmarkRepository {
    fun observe(
        serverId: String,
        bookId: String,
    ): Flow<List<Bookmark>>

    suspend fun toggle(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        note: String? = null,
    )

    suspend fun delete(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    )
}

class BookmarkRepositoryImpl(
    private val bookmarkDao: BookmarkDao,
) : BookmarkRepository {
    override fun observe(
        serverId: String,
        bookId: String,
    ): Flow<List<Bookmark>> =
        bookmarkDao.observeByBook(serverId, bookId).map { entities ->
            entities.map(BookmarkEntity::toDomain)
        }

    override suspend fun toggle(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        note: String?,
    ) {
        // Room Flowは即座に現在状態を発行するためfirst()で現状を取得し、対象ページの有無で追加/削除を切替。
        val current = bookmarkDao.observeByBook(serverId, bookId).first()
        if (current.any { it.pageNumber == pageNumber }) {
            bookmarkDao.delete(serverId, bookId, pageNumber)
        } else {
            bookmarkDao.upsert(
                BookmarkEntity(
                    serverId = serverId,
                    bookId = bookId,
                    pageNumber = pageNumber,
                    note = note,
                    createdAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun delete(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ) {
        bookmarkDao.delete(serverId, bookId, pageNumber)
    }
}
