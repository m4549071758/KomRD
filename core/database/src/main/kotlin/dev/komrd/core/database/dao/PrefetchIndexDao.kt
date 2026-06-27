package dev.komrd.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.komrd.core.database.entity.PrefetchIndexEntity

@Dao
interface PrefetchIndexDao {
    @Upsert
    suspend fun upsert(entity: PrefetchIndexEntity)

    @Query(
        """
        SELECT * FROM prefetch_index
        WHERE serverId = :serverId AND bookId = :bookId
            AND resourcePath = :resourcePath AND variant = :variant
        """,
    )
    suspend fun find(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): PrefetchIndexEntity?

    @Query("SELECT * FROM prefetch_index WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun listByBook(
        serverId: String,
        bookId: String,
    ): List<PrefetchIndexEntity>

    @Query("SELECT * FROM prefetch_index WHERE serverId = :serverId")
    suspend fun listByServer(serverId: String): List<PrefetchIndexEntity>

    @Query("SELECT * FROM prefetch_index ORDER BY lastAccessedAt ASC")
    suspend fun listAll(): List<PrefetchIndexEntity>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM prefetch_index")
    suspend fun sumBytes(): Long

    @Query(
        """
        DELETE FROM prefetch_index
        WHERE serverId = :serverId AND bookId = :bookId
            AND resourcePath = :resourcePath AND variant = :variant
        """,
    )
    suspend fun deleteByKey(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    )

    @Query("DELETE FROM prefetch_index WHERE serverId = :serverId AND bookId = :bookId")
    suspend fun deleteByBook(
        serverId: String,
        bookId: String,
    )

    @Query("DELETE FROM prefetch_index WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query(
        """
        UPDATE prefetch_index SET lastAccessedAt = :timestamp
        WHERE serverId = :serverId AND bookId = :bookId
            AND resourcePath = :resourcePath AND variant = :variant
        """,
    )
    suspend fun touchLastAccessedAt(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
        timestamp: Long,
    )
}
