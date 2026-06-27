package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * `POST /api/v1/books/list` の検索条件。
 *
 * Komga側の検索DSLは段階拡張するため、任意の`condition`を通せる最小形にする
 * （[SeriesListRequestDto]と同方針）。
 */
@Serializable
data class BookListRequestDto(
    val condition: JsonObject? = null,
    val fullTextSearch: String? = null,
)
