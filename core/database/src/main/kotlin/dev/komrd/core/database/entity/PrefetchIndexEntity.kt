package dev.komrd.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "prefetch_index",
    primaryKeys = ["serverId", "bookId", "resourcePath", "variant"],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["lastAccessedAt"]), Index(value = ["resourceKind"])],
)
data class PrefetchIndexEntity(
    val serverId: String,
    val bookId: String,
    val pageNumber: Int?,
    val variant: String,
    val resourcePath: String,
    val resourceKind: String,
    val filePath: String,
    val sizeBytes: Long,
    val fetchedAt: Long,
    val lastAccessedAt: Long,
    val etag: String?,
)
