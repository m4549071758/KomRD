package dev.komrd.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "epub_progress_queue",
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
data class EpubProgressQueueEntity(
    val serverId: String,
    val bookId: String,
    val locatorJson: String,
    val updatedAt: Long,
)
