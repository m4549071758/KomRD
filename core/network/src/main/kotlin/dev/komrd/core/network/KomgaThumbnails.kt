package dev.komrd.core.network

object KomgaThumbnails {
    fun seriesThumbnailUrl(
        baseUrl: String,
        seriesId: String,
    ): String = "${baseUrl.trimmedBaseUrl()}/api/v1/series/$seriesId/thumbnail"

    fun bookThumbnailUrl(
        baseUrl: String,
        bookId: String,
    ): String = "${baseUrl.trimmedBaseUrl()}/api/v1/books/$bookId/thumbnail"

    fun bookPageUrl(
        baseUrl: String,
        bookId: String,
        pageNumber: Int,
        convert: String? = null,
    ): String {
        val base = "${baseUrl.trimmedBaseUrl()}/api/v1/books/$bookId/pages/$pageNumber"
        return if (convert != null) "$base?convert=$convert" else base
    }

    fun bookPageThumbnailUrl(
        baseUrl: String,
        bookId: String,
        pageNumber: Int,
    ): String = "${baseUrl.trimmedBaseUrl()}/api/v1/books/$bookId/pages/$pageNumber/thumbnail"

    /** Collectionの代表サムネ URL（M5 で Collection 一覧/詳細表示用）。 */
    fun collectionThumbnailUrl(
        baseUrl: String,
        collectionId: String,
    ): String = "${baseUrl.trimmedBaseUrl()}/api/v1/collections/$collectionId/thumbnail"

    /** Read Listの代表サムネ URL（M5 で Read List 一覧/詳細表示用）。 */
    fun readListThumbnailUrl(
        baseUrl: String,
        readListId: String,
    ): String = "${baseUrl.trimmedBaseUrl()}/api/v1/readlists/$readListId/thumbnail"
}
