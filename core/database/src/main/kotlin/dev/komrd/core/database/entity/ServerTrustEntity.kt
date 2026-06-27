package dev.komrd.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "server_trust",
    foreignKeys = [
        ForeignKey(
            entity = ServerEntity::class,
            parentColumns = ["id"],
            childColumns = ["serverId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ServerTrustEntity(
    @PrimaryKey val serverId: String,
    val pinnedFingerprintsJson: String,
    val customCaCertsPem: String,
    val updatedAt: Long,
)
