package dev.komrd.core.cache

interface PrefetchStore {
    /**
     * リソース実体を保存し索引へ登録/更新。同キー再取得は上書き(ファイル実体+索引)。
     *
     * [resourcePath]はリソース一意パス(画像系=`pageNumber.toString()`・EPUB=章href)。
     * [resourceKind]は[RESOURCE_KIND_PAGE]/[RESOURCE_KIND_HTML]/[RESOURCE_KIND_CSS]等。
     */
    suspend fun put(
        serverId: String,
        bookId: String,
        resourcePath: String,
        resourceKind: String,
        variant: String,
        bytes: ByteArray,
        etag: String?,
    ): PrefetchEntry

    /**
     * 命中時: `lastAccessedAt`を更新してエントリ返却。未命中: null。
     */
    suspend fun get(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): PrefetchEntry?

    suspend fun listByBook(
        serverId: String,
        bookId: String,
    ): List<PrefetchEntry>

    suspend fun listByServer(serverId: String): List<PrefetchEntry>

    suspend fun listAll(): List<PrefetchEntry>

    /** 全サーバ合算の保持バイト数。 */
    suspend fun sumBytes(): Long

    /** エントリをファイル実体ごと削除。存在した場合true。 */
    suspend fun delete(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): Boolean

    /** Book単位でファイル実体+索引を一括削除。削除件数を返す。 */
    suspend fun deleteByBook(
        serverId: String,
        bookId: String,
    ): Int

    /** Server単位でファイル実体+索引を一括削除。削除件数を返す。 */
    suspend fun deleteByServer(serverId: String): Int

    companion object {
        /** 既定のページ画像バリアント(フル解像度)。零スケール等は別variantで格納。EPUBも本variant。 */
        const val VARIANT_FULL: String = "full"
        const val VARIANT_JPEG: String = "jpeg"
        const val RESOURCE_KIND_PAGE: String = "PAGE"
        const val RESOURCE_KIND_HTML: String = "HTML"
        const val RESOURCE_KIND_CSS: String = "CSS"
        const val RESOURCE_KIND_IMAGE: String = "IMAGE"
        const val RESOURCE_KIND_FONT: String = "FONT"
    }
}
