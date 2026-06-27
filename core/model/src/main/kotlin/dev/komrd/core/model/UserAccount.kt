package dev.komrd.core.model

data class UserAccount(
    val id: String? = null,
    val email: String? = null,
    val roles: Set<String> = emptySet(),
    val sharedAllLibraries: Boolean = false,
    val sharedLibrariesIds: List<String> = emptyList(),
    val ageRestriction: AgeRestriction? = null,
)

data class AgeRestriction(
    val age: Int,
    val restriction: String,
)
