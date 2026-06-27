package dev.komrd.core.cache

import java.io.File

data class PrefetchEntry(
    val serverId: String,
    val bookId: String,
    val resourcePath: String,
    val resourceKind: String,
    val variant: String,
    val file: File,
    val sizeBytes: Long,
    val fetchedAt: Long,
    val lastAccessedAt: Long,
    val etag: String?,
    val pageNumber: Int? = null,
)
