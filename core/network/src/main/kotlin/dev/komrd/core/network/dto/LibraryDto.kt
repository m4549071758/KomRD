package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

/** `GET /api/v1/libraries` の1要素。 */
@Serializable
data class LibraryDto(
    val id: String,
    val name: String,
)
