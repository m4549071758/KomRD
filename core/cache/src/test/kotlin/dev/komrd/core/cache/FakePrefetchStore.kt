package dev.komrd.core.cache

import java.io.File
import java.util.concurrent.atomic.AtomicLong

class FakePrefetchStore(
    private val baseDir: File,
) : PrefetchStore {
    private data class Key(
        val serverId: String,
        val bookId: String,
        val resourcePath: String,
        val variant: String,
    )

    private val entries = mutableMapOf<Key, PrefetchEntry>()
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
        val file = fileFor(serverId, bookId, resourcePath, variant)
        file.parentFile?.mkdirs()
        file.writeBytes(bytes)
        val now = clock.incrementAndGet()
        val entry =
            PrefetchEntry(
                serverId = serverId,
                bookId = bookId,
                resourcePath = resourcePath,
                resourceKind = resourceKind,
                variant = variant,
                file = file,
                sizeBytes = file.length(),
                fetchedAt = now,
                lastAccessedAt = now,
                etag = etag,
                pageNumber = resourcePath.toIntOrNull(),
            )
        entries[key] = entry
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
        fileFor(serverId, bookId, resourcePath, variant).delete()
        return removed
    }

    override suspend fun deleteByBook(
        serverId: String,
        bookId: String,
    ): Int {
        val keys = entries.keys.filter { it.serverId == serverId && it.bookId == bookId }
        keys.forEach {
            entries.remove(it)
            fileFor(it.serverId, it.bookId, it.resourcePath, it.variant).delete()
        }
        return keys.size
    }

    override suspend fun deleteByServer(serverId: String): Int {
        val keys = entries.keys.filter { it.serverId == serverId }
        keys.forEach {
            entries.remove(it)
            fileFor(it.serverId, it.bookId, it.resourcePath, it.variant).delete()
        }
        return keys.size
    }

    private fun fileFor(
        serverId: String,
        bookId: String,
        resourcePath: String,
        variant: String,
    ): File {
        val base = File(File(baseDir, serverId), bookId).resolve(resourcePath)
        return File(base.parentFile, "${base.name}_$variant")
    }
}
