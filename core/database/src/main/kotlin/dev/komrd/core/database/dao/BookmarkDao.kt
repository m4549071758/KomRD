package dev.komrd.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.komrd.core.database.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Upsert
    suspend fun upsert(entity: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE serverId = :serverId AND bookId = :bookId AND pageNumber = :pageNumber")
    suspend fun delete(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    )

    @Query("SELECT * FROM bookmarks WHERE serverId = :serverId AND bookId = :bookId ORDER BY pageNumber ASC")
    fun observeByBook(
        serverId: String,
        bookId: String,
    ): Flow<List<BookmarkEntity>>
}
