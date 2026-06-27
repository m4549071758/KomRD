package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Komga `GET/POST/PATCH /api/v1/collections` のレスポンス DTO。
 *
 * M5 では閲覧系（list/detail）のみ使用。作成/更新は ADMIN 必須のため本Issueでは未使用。
 * `seriesIds` は `filtered=true` の動的コレクションでは空配列になる。
 */
@Serializable
data class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean = false,
    val filtered: Boolean = false,
    val seriesIds: List<String> = emptyList(),
    val createdDate: String? = null,
    val lastModifiedDate: String? = null,
)
