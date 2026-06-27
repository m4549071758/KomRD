package dev.komrd.core.model

data class BookPageImage(
    val serverId: String,
    val bookId: String,
    val pageNumber: Int,
    val url: String,
    val variant: String = "full",
)
