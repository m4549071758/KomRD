package dev.komrd.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BookDto(
    val id: String,
    val seriesId: String? = null,
    val libraryId: String? = null,
    val name: String,
    val metadata: BookMetadataDto = BookMetadataDto(),
    val media: BookMediaDto = BookMediaDto(),
)

@Serializable
data class BookMetadataDto(
    val title: String? = null,
    val number: String? = null,
    val numberSort: Float? = null,
    val readingDirection: String? = null,
)

@Serializable
data class BookMediaDto(
    val pagesCount: Int? = null,
    @SerialName("mediaProfile") val mediaProfile: String? = null,
    val mediaType: String? = null,
)
