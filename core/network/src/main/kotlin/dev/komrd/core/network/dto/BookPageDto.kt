package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class BookPageDto(
    val number: Int,
    val fileName: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val mediaType: String? = null,
    val sizeBytes: Long? = null,
)
