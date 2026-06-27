package dev.komrd.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "bookmarks",
    primaryKeys = ["serverId", "bookId", "pageNumber"],
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class BookmarkEntity(
    val serverId: String,
    val bookId: String,
    val pageNumber: Int,
    val note: String?,
    val createdAt: Long,
)
