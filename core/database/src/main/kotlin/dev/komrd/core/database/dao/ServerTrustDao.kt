package dev.komrd.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.komrd.core.database.entity.ServerTrustEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerTrustDao {
    @Query("SELECT * FROM server_trust WHERE serverId = :serverId")
    suspend fun findById(serverId: String): ServerTrustEntity?

    @Query("SELECT * FROM server_trust ORDER BY updatedAt ASC")
    fun observe(): Flow<List<ServerTrustEntity>>

    @Upsert
    suspend fun upsert(entity: ServerTrustEntity)

    @Query("DELETE FROM server_trust WHERE serverId = :serverId")
    suspend fun deleteByServerId(serverId: String)
}
