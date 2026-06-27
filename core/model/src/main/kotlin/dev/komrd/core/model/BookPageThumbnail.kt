package dev.komrd.core.model

data class BookPageThumbnail(
    val serverId: String,
    val bookId: String,
    val pageNumber: Int,
    /** PDF時のthumbnailエンドポイントURL(`/api/v1/books/{id}/pages/{n}/thumbnail`)。画像系はnull。 */
    val url: String? = null,
    /** 媒体プロファイル。PDFのときネット取得フォールバックを使用。既定はIMAGE(従来挙動)。 */
    val mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE,
)
