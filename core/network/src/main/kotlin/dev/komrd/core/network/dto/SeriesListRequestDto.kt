package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class SeriesListRequestDto(
    val condition: JsonObject? = null,
    val fullTextSearch: String? = null,
)
