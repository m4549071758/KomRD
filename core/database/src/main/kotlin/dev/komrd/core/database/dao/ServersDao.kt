package dev.komrd.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import dev.komrd.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServersDao {
    @Query("SELECT * FROM servers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun findById(id: String): ServerEntity?

    // @Upsert は ON CONFLICT DO UPDATE を生成し DELETE→INSERT を伴わないため、
    @Upsert
    suspend fun upsert(server: ServerEntity)

    @Delete
    suspend fun delete(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteById(id: String)
}
