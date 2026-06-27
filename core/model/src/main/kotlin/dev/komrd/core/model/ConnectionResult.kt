package dev.komrd.core.model

sealed interface ConnectionResult {
    data class Authenticated(
        val userId: String? = null,
    ) : ConnectionResult
}
