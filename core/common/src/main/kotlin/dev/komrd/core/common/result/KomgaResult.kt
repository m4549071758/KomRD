package dev.komrd.core.common.result

import dev.komrd.core.common.error.KomgaError

sealed interface KomgaResult<out T> {
    data class Success<T>(
        val value: T,
    ) : KomgaResult<T>

    data class Failure(
        val error: KomgaError,
    ) : KomgaResult<Nothing>
}
