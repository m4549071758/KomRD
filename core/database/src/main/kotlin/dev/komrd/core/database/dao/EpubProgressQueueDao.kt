package dev.komrd.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.komrd.core.database.entity.EpubProgressQueueEntity

@Dao
interface EpubProgressQueueDao {
    @Upsert
    suspend fun upsert(entity: EpubProgressQueueEntity)

    @Query("SELECT * FROM epub_progress_queue WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun find(
        serverId: String,
        bookId: String,
    ): EpubProgressQueueEntity?

    @Query("SELECT * FROM epub_progress_queue WHERE serverId = :serverId")
    suspend fun findByServer(serverId: String): List<EpubProgressQueueEntity>

    @Query("DELETE FROM epub_progress_queue WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun delete(
        serverId: String,
        bookId: String,
    )
}
