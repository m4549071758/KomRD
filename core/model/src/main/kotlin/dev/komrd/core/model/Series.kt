package dev.komrd.core.model

/**
 * Library内の1つのSeries（CONTEXT: Series）。ライブラリ一覧の表示単位。
 * [thumbnailUrl] は代表サムネのURL（取得時の認証はサーバ別OkHttpClientが付与）。
 */
data class Series(
    val id: String,
    val serverId: String,
    val libraryId: String?,
    val name: String,
    val thumbnailUrl: String,
)
