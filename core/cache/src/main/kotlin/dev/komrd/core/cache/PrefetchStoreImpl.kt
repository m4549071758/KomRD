package dev.komrd.core.cache

import dev.komrd.core.database.dao.PrefetchIndexDao
import dev.komrd.core.database.entity.PrefetchIndexEntity
import java.io.File
import javax.inject.Inject

@Suppress("TooManyFunctions") // CRUD+list系+内部ヘルパ。Storeの責務集約。分割は可読性を搕くため見送り。
class PrefetchStoreImpl
    @Inject
    constructor(
        private val dao: PrefetchIndexDao,
        private val prefetchDir: File,
        private val clock: () -> Long = System::currentTimeMillis,
    ) : PrefetchStore {
        override suspend fun put(
            serverId: String,
            bookId: String,
            resourcePath: String,
            resourceKind: String,
            variant: String,
            bytes: ByteArray,
            etag: String?,
        ): PrefetchEntry {
            val file = fileFor(serverId, bookId, resourcePath, variant)
            file.parentFile?.mkdirs()
            file.writeBytes(bytes)
            val now = clock()
            val entity =
                PrefetchIndexEntity(
                    serverId = serverId,
                    bookId = bookId,
                    pageNumber = resourcePath.toIntOrNull(),
                    variant = variant,
                    resourcePath = resourcePath,
                    resourceKind = resourceKind,
                    filePath = file.absolutePath,
                    sizeBytes = file.length(),
                    fetchedAt = now,
                    lastAccessedAt = now,
                    etag = etag,
                )
            dao.upsert(entity)
            return toEntry(entity)
        }

        override suspend fun get(
            serverId: String,
            bookId: String,
            resourcePath: String,
            variant: String,
        ): PrefetchEntry? {
            val entity = dao.find(serverId, bookId, resourcePath, variant) ?: return null
            val accessedAt = clock()
            dao.touchLastAccessedAt(serverId, bookId, resourcePath, variant, accessedAt)
            return toEntry(entity).copy(lastAccessedAt = accessedAt)
        }

        override suspend fun listByBook(
            serverId: String,
            bookId: String,
        ): List<PrefetchEntry> = dao.listByBook(serverId, bookId).toEntries()

        override suspend fun listByServer(serverId: String) = dao.listByServer(serverId).toEntries()

        override suspend fun listAll() = dao.listAll().toEntries()

        override suspend fun sumBytes(): Long = dao.sumBytes()

        override suspend fun delete(
            serverId: String,
            bookId: String,
            resourcePath: String,
            variant: String,
        ): Boolean {
            val entity = dao.find(serverId, bookId, resourcePath, variant) ?: return false
            File(entity.filePath).delete()
            dao.deleteByKey(serverId, bookId, resourcePath, variant)
            return true
        }

        override suspend fun deleteByBook(
            serverId: String,
            bookId: String,
        ): Int {
            val entries = dao.listByBook(serverId, bookId)
            for (entity in entries) {
                File(entity.filePath).delete()
            }
            dao.deleteByBook(serverId, bookId)
            return entries.size
        }

        override suspend fun deleteByServer(serverId: String): Int {
            val entries = dao.listByServer(serverId)
            for (entity in entries) {
                File(entity.filePath).delete()
            }
            dao.deleteByServer(serverId)
            return entries.size
        }

        private fun fileFor(
            serverId: String,
            bookId: String,
            resourcePath: String,
            variant: String,
        ): File {
            val base = File(File(prefetchDir.resolve(serverId), bookId), resourcePath)
            return File(base.parentFile, "${base.name}_$variant")
        }

        private fun toEntry(entity: PrefetchIndexEntity): PrefetchEntry =
            PrefetchEntry(
                serverId = entity.serverId,
                bookId = entity.bookId,
                resourcePath = entity.resourcePath,
                resourceKind = entity.resourceKind,
                variant = entity.variant,
                file = File(entity.filePath),
                sizeBytes = entity.sizeBytes,
                fetchedAt = entity.fetchedAt,
                lastAccessedAt = entity.lastAccessedAt,
                etag = entity.etag,
                pageNumber = entity.pageNumber,
            )

        private fun List<PrefetchIndexEntity>.toEntries(): List<PrefetchEntry> = map(::toEntry)
    }
