package dev.komrd.core.model

data class Bookmark(
    val serverId: String,
    val bookId: String,
    val pageNumber: Int,
    val note: String?,
    val createdAt: Long,
)
