package dev.komrd.core.model

data class Book(
    val id: String,
    val serverId: String,
    val seriesId: String?,
    val name: String,
    val thumbnailUrl: String,
)
