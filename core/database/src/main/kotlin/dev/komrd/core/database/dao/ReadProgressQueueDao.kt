package dev.komrd.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.komrd.core.database.entity.ReadProgressQueueEntity

@Dao
interface ReadProgressQueueDao {
    @Upsert
    suspend fun upsert(entity: ReadProgressQueueEntity)

    @Query("SELECT * FROM read_progress_queue WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun find(
        serverId: String,
        bookId: String,
    ): ReadProgressQueueEntity?

    @Query("SELECT * FROM read_progress_queue WHERE serverId = :serverId")
    suspend fun findByServer(serverId: String): List<ReadProgressQueueEntity>

    @Query("DELETE FROM read_progress_queue WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun delete(
        serverId: String,
        bookId: String,
    )
}
