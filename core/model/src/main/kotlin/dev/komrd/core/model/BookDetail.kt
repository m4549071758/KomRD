package dev.komrd.core.model

data class BookDetail(
    val id: String,
    val seriesId: String? = null,
    val serverId: String,
    val name: String,
    val pages: List<BookPage>,
    val readingDirection: ReadingDirection? = null,
    val mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE,
    val mediaType: String? = null,
) {
    val pagesCount: Int get() = pages.size

    /** 画像系ページリーダーで扱うべき媒体か(EPUB以外)。 */
    val isPageImageReader: Boolean get() = !mediaProfile.isEpub
}

data class BookPage(
    val number: Int,
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null,
) {
    val isWide: Boolean
        get() = width != null && height != null && width > height
}
