package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class ReadProgressUpdateDto(
    val page: Int,
    val completed: Boolean,
)
