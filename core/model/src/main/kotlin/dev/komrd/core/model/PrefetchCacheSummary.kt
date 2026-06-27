package dev.komrd.core.model

/**
 * Prefetch Storeに保存されている1冊あたりのキャッシュ概要。
 *
 * @property serverId サーバID
 * @property bookId ブックID
 * @property entryCount キャッシュエントリ数
 * @property totalBytes 合計バイト数
 * @property pageRanges 画像系ページの連続範囲リスト（例: [1..10, 15..20]）。EPUB等ページ番号を持たないリソースは含まない。
 */
data class PrefetchCacheSummary(
    val serverId: String,
    val bookId: String,
    val entryCount: Int,
    val totalBytes: Long,
    val pageRanges: List<IntRange>,
) {
    /** ページ範囲を人間が読める文字列に変換する（空なら空文字）。 */
    fun pageRangesText(): String =
        pageRanges
            .map { range ->
                if (range.first == range.last) {
                    "${range.first}"
                } else {
                    "${range.first}-${range.last}"
                }
            }.joinToString(", ")
}
