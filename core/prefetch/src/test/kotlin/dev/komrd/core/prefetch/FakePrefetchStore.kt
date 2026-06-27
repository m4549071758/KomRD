package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchEntry
import dev.komrd.core.cache.PrefetchStore
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class FakePrefetchStore : PrefetchStore {
    private data class Key(
        val serverId: String,
        val bookId: String,
        val resourcePath: String,
        val variant: String,
    )

    private val entries = mutableMapOf<Key, PrefetchEntry>()
    private val bytesStore = mutableMapOf<Key, ByteArray>()
    private val clock = AtomicLong(1_000L)

    override suspend fun put(
        serverId: String,
        bookId: String,
        resourcePath: String,
        resourceKind: String,
        variant: String,
        bytes: ByteArray,
        etag: String?,
    ): PrefetchEntry {
        val key = Key(serverId, bookId, resourcePath, variant)
        val now = clock.incrementAndGet()
        val entry =
            PrefetchEntry(
                serverId = serverId,
                bookId = bookId,
                resourcePath = resourcePath,
                resourceKind = resourceKind,
                variant = variant,
                file = File("/tmp/fake/$serverId/$bookId/${resourcePath}_$variant"),
                sizeBytes = bytes.size.toLong(),
                fetchedAt = now,
                lastAccessedAt = now,
                etag = etag,
                pageNumber = resourcePath.toIntOrNull(),
            )
        entries[key] = entry
        bytesStore[key] = bytes
        return entry
    }

    override suspend fun get(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): PrefetchEntry? {
        val key = Key(serverId, bookId, resourcePath, variant)
        val entry = entries[key] ?: return null
        val accessedAt = clock.incrementAndGet()
        val touched = entry.copy(lastAccessedAt = accessedAt)
        entries[key] = touched
        return touched
    }

    override suspend fun listByBook(
        serverId: String,
        bookId: String,
    ): List<PrefetchEntry> = entries.filter { it.key.serverId == serverId && it.key.bookId == bookId }.values.toList()

    override suspend fun listByServer(serverId: String): List<PrefetchEntry> =
        entries.filter { it.key.serverId == serverId }.values.toList()

    override suspend fun listAll(): List<PrefetchEntry> = entries.values.sortedBy { it.lastAccessedAt }.toList()

    override suspend fun sumBytes(): Long = entries.values.sumOf { it.sizeBytes }

    override suspend fun delete(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): Boolean {
        val key = Key(serverId, bookId, resourcePath, variant)
        val removed = entries.remove(key) != null
        bytesStore.remove(key)
        return removed
    }

    override suspend fun deleteByBook(
        serverId: String,
        bookId: String,
    ): Int {
        val keys = entries.keys.filter { it.serverId == serverId && it.bookId == bookId }
        keys.forEach {
            entries.remove(it)
            bytesStore.remove(it)
        }
        return keys.size
    }

    override suspend fun deleteByServer(serverId: String): Int {
        val keys = entries.keys.filter { it.serverId == serverId }
        keys.forEach {
            entries.remove(it)
            bytesStore.remove(it)
        }
        return keys.size
    }

    /** テスト用: 画像系ページ事前キャッシュ投入(後方互換ヘルパ)。 */
    suspend fun prepopulate(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        variant: String = PrefetchStore.VARIANT_FULL,
        bytes: ByteArray = byteArrayOf(0),
    ) {
        put(serverId, bookId, pageNumber.toString(), PrefetchStore.RESOURCE_KIND_PAGE, variant, bytes, etag = null)
    }

    /**
     * テスト用: `fetchedAt`/`lastAccessedAt`/`sizeBytes`を直接指定して投入(破棄ロジック検証用)。
     * 巨大サイズ(2GB等)を扱うため`bytes`実体は持たず`sizeBytes`のみ設定。画像系PAGEを想定。
     */
    fun putEntry(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        sizeBytes: Long,
        fetchedAt: Long,
        lastAccessedAt: Long,
        variant: String = PrefetchStore.VARIANT_FULL,
    ) {
        val resourcePath = pageNumber.toString()
        val key = Key(serverId, bookId, resourcePath, variant)
        entries[key] =
            PrefetchEntry(
                serverId = serverId,
                bookId = bookId,
                resourcePath = resourcePath,
                resourceKind = PrefetchStore.RESOURCE_KIND_PAGE,
                variant = variant,
                file = File("/tmp/fake/$serverId/$bookId/${resourcePath}_$variant"),
                sizeBytes = sizeBytes,
                fetchedAt = fetchedAt,
                lastAccessedAt = lastAccessedAt,
                etag = null,
                pageNumber = pageNumber,
            )
    }

    /** テスト用: EPUBエントリ投入(破棄ロジック検証用・resourceKind指定)。 */
    @Suppress("LongParameterList") // テストDSL・破棄ロジック検証のため一括指定を許容
    fun putEpubEntry(
        serverId: String,
        bookId: String,
        resourcePath: String,
        resourceKind: String,
        sizeBytes: Long,
        fetchedAt: Long,
        lastAccessedAt: Long,
        variant: String = PrefetchStore.VARIANT_FULL,
    ) {
        val key = Key(serverId, bookId, resourcePath, variant)
        entries[key] =
            PrefetchEntry(
                serverId = serverId,
                bookId = bookId,
                resourcePath = resourcePath,
                resourceKind = resourceKind,
                variant = variant,
                file = File("/tmp/fake/$serverId/$bookId/${resourcePath}_$variant"),
                sizeBytes = sizeBytes,
                fetchedAt = fetchedAt,
                lastAccessedAt = lastAccessedAt,
                etag = null,
                pageNumber = null,
            )
    }

    /** テスト用: 当該画像系ページがキャッシュ済か。 */
    fun isCached(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        variant: String = PrefetchStore.VARIANT_FULL,
    ): Boolean = entries[Key(serverId, bookId, pageNumber.toString(), variant)] != null

    fun isCachedResource(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String = PrefetchStore.VARIANT_FULL,
    ): Boolean = entries[Key(serverId, bookId, resourcePath, variant)] != null
}
