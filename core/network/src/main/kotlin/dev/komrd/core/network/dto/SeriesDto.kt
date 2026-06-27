package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class SeriesDto(
    val id: String,
    val libraryId: String? = null,
    val name: String,
    val metadata: SeriesMetadataDto = SeriesMetadataDto(),
)

@Serializable
data class SeriesMetadataDto(
    val title: String? = null,
    val titleSort: String? = null,
    val status: String? = null,
    val readingDirection: String? = null,
)
