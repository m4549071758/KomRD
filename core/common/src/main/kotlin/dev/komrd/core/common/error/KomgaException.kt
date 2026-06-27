package dev.komrd.core.common.error

class KomgaException(
    val error: KomgaError,
) : Exception(error.message)

/** [KomgaError] を Throwable 境界へ載せる。 */
fun KomgaError.toException(): KomgaException = KomgaException(this)
