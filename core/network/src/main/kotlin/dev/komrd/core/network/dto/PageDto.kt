package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class PageDto<T>(
    val content: List<T> = emptyList(),
    val empty: Boolean = content.isEmpty(),
    val first: Boolean = true,
    val last: Boolean = true,
    val number: Int = 0,
    val numberOfElements: Int = content.size,
    val size: Int = content.size,
    val totalElements: Long = content.size.toLong(),
    val totalPages: Int = 1,
)
