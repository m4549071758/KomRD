package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class WhoAmIDto(
    val id: String? = null,
    val email: String? = null,
    val name: String? = null,
)
