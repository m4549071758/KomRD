package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

/**
 * Komga `POST /api/v1/readlists` のリクエスト DTO。
 *
 * ADMIN ロール必須。M5 では未使用（型のみ定義）。
 */
@Serializable
data class ReadListCreationDto(
    val name: String,
    val summary: String,
    val ordered: Boolean,
    val bookIds: List<String>,
)
