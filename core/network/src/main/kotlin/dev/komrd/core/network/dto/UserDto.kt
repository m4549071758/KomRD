package dev.komrd.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: String? = null,
    val email: String? = null,
    val roles: List<String> = emptyList(),
    val sharedAllLibraries: Boolean? = null,
    val sharedLibrariesIds: List<String> = emptyList(),
    val labelsAllow: List<String> = emptyList(),
    val labelsExclude: List<String> = emptyList(),
    val ageRestriction: AgeRestrictionDto? = null,
)

/** ユーザの年齢制限（Komga `AgeRestriction`）。[restriction]は`ALLOW_ONLY`/`EXCLUDE`。 */
@Serializable
data class AgeRestrictionDto(
    val age: Int? = null,
    val restriction: String? = null,
)
