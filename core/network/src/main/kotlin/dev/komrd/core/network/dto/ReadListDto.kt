package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Komga `GET/POST/PATCH /api/v1/readlists` のレスポンス DTO。
 *
 * M5 では閲覧系（list/detail）のみ使用。`bookIds` は `filtered=true` の動的 ReadList では空配列。
 */
@Serializable
data class ReadListDto(
    val id: String,
    val name: String,
    val summary: String = "",
    val ordered: Boolean = false,
    val filtered: Boolean = false,
    val bookIds: List<String> = emptyList(),
    val createdDate: String? = null,
    val lastModifiedDate: String? = null,
)
