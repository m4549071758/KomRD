package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.model.BookMediaProfile

data class PrefetchTarget(
    val bookId: String,
    val resourcePath: String,
    val resourceKind: String,
    val variant: String,
    val pageNumber: Int? = null,
)

internal fun variantFor(mediaProfile: BookMediaProfile): String =
    when (mediaProfile) {
        BookMediaProfile.PDF -> PrefetchStore.VARIANT_JPEG
        else -> PrefetchStore.VARIANT_FULL
    }
