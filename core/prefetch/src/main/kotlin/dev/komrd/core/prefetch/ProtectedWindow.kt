package dev.komrd.core.prefetch

data class ProtectedWindow(
    val serverId: String,
    val currentBookId: String,
    val currentPageNumber: Int,
    val nextBookId: String?,
    val currentChapterIndex: Int? = null,
    val currentChapterHref: String? = null,
)
