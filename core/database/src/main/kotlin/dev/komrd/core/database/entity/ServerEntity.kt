package dev.komrd.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "servers", indices = [Index(value = ["createdAt"])])
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val authType: String,
    val username: String?,
    val secretCiphertext: ByteArray,
    val secretIv: ByteArray,
    val createdAt: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServerEntity) return false
        return id == other.id &&
            name == other.name &&
            baseUrl == other.baseUrl &&
            authType == other.authType &&
            username == other.username &&
            secretCiphertext.contentEquals(other.secretCiphertext) &&
            secretIv.contentEquals(other.secretIv) &&
            createdAt == other.createdAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + baseUrl.hashCode()
        result = 31 * result + authType.hashCode()
        result = 31 * result + (username?.hashCode() ?: 0)
        result = 31 * result + secretCiphertext.contentHashCode()
        result = 31 * result + secretIv.contentHashCode()
        result = 31 * result + createdAt.hashCode()
        return result
    }
}
