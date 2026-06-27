package dev.komrd.core.model

data class BookOverview(
    val id: String,
    val serverId: String,
    val name: String,
    val seriesName: String?,
    val pagesCount: Int,
    val mediaType: String?,
    val thumbnailUrl: String,
    val mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE,
)
