package dev.komrd.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "read_progress_queue",
    primaryKeys = ["serverId", "bookId"],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReadProgressQueueEntity(
    val serverId: String,
    val bookId: String,
    val page: Int,
    val completed: Boolean,
    val updatedAt: Long,
)
