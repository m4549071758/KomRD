package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Komga `POST /api/v1/collections` のリクエスト DTO。
 *
 * ADMIN ロール必須。M5 では未使用（型のみ定義）。
 */
@Serializable
data class CollectionCreationDto(
    val name: String,
    val ordered: Boolean,
    val seriesIds: List<String>,
)
