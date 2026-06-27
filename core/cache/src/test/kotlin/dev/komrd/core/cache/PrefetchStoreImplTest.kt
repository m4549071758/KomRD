package dev.komrd.core.cache

import dev.komrd.core.database.dao.PrefetchIndexDao
import dev.komrd.core.database.entity.PrefetchIndexEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefetchStoreImplTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var dao: FakePrefetchIndexDao
    private lateinit var store: PrefetchStoreImpl
    private var now: Long = 1000L

    @Before
    fun setup() {
        dao = FakePrefetchIndexDao()
        store = PrefetchStoreImpl(dao, tempFolder.newFolder("prefetch")) { now }
    }

    @After
    fun teardown() = tempFolder.delete()

    @Test
    fun put_createsFileAndIndexes() =
        runTest {
            val entry =
                store.put(
                    "s1",
                    "b1",
                    resourcePath = "2",
                    resourceKind = PrefetchStore.RESOURCE_KIND_PAGE,
                    variant = "full",
                    bytes = byteArrayOf(1, 2, 3),
                    etag = "e1",
                )

            assertTrue(entry.file.exists())
            assertEquals(3, entry.file.length())
            assertEquals(3L, entry.sizeBytes)
            assertEquals("e1", entry.etag)
            assertEquals(1, dao.all().size)
            assertEquals(1000L, dao.find("s1", "b1", "2", "full")?.fetchedAt)
        }

    @Test
    fun put_sameKey_overwritesFileAndIndex() =
        runTest {
            store.put("s1", "b1", "2", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1, 2), etag = null)
            now = 2000L
            store.put(
                "s1",
                "b1",
                "2",
                PrefetchStore.RESOURCE_KIND_PAGE,
                "full",
                byteArrayOf(1, 2, 3, 4, 5),
                etag = "e2",
            )

            assertEquals(1, dao.all().size)
            val entry = store.get("s1", "b1", "2", "full")
            assertEquals(5L, entry?.sizeBytes)
            assertEquals("e2", entry?.etag)
            assertEquals(2000L, entry?.fetchedAt)
        }

    @Test
    fun get_hit_touchesLastAccessedAt() =
        runTest {
            store.put("s1", "b1", "2", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            now = 5000L
            val entry = store.get("s1", "b1", "2", "full")

            assertEquals(5000L, entry?.lastAccessedAt)
            assertEquals(5000L, dao.find("s1", "b1", "2", "full")?.lastAccessedAt)
        }

    @Test
    fun get_miss_returnsNull() = runTest { assertNull(store.get("s1", "b1", "2", "full")) }

    @Test
    fun listByServer_isolatesByServer() =
        runTest {
            store.put("s1", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            store.put("s1", "b2", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            store.put("s2", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)

            assertEquals(2, store.listByServer("s1").size)
            assertEquals(1, store.listByBook("s1", "b1").size)
            assertTrue(store.listByServer("s2").all { it.serverId == "s2" })
        }

    @Test
    fun sumBytes_aggregatesAllServers() =
        runTest {
            store.put("s1", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1, 2, 3), etag = null)
            store.put("s2", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1, 2), etag = null)

            assertEquals(5L, store.sumBytes())
        }

    @Test
    fun listAll_returnsAllServers_orderedByLastAccessedAt() =
        runTest {
            store.put("s1", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            now = 3000L
            store.put("s2", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            now = 2000L
            store.put("s1", "b2", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)

            val all = store.listAll()
            assertEquals(3, all.size)
            assertEquals(listOf(1000L, 2000L, 3000L), all.map { it.lastAccessedAt })
        }

    @Test
    fun delete_hit_removesFileAndIndex() =
        runTest {
            store.put("s1", "b1", "2", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1, 2), etag = null)
            val file = store.get("s1", "b1", "2", "full")?.file ?: error("entry missing")

            assertTrue(store.delete("s1", "b1", "2", "full"))
            assertFalse(file.exists())
            assertNull(dao.find("s1", "b1", "2", "full"))
        }

    @Test
    fun delete_miss_returnsFalse() = runTest { assertFalse(store.delete("s1", "b1", "2", "full")) }

    @Test
    fun deleteByServer_removesAllFilesAndIndexes() =
        runTest {
            store.put("s1", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            store.put("s1", "b2", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)
            store.put("s2", "b1", "1", PrefetchStore.RESOURCE_KIND_PAGE, "full", byteArrayOf(1), etag = null)

            assertEquals(2, store.deleteByServer("s1"))
            assertTrue(store.listByServer("s1").isEmpty())
            assertEquals(1, store.listByServer("s2").size)
        }

    @Test
    fun put_epubResource_usesResourcePathAndKind() =
        runTest {
            val entry =
                store.put(
                    "s1",
                    "b1",
                    resourcePath = "OEBPS/ch1.xhtml",
                    resourceKind = PrefetchStore.RESOURCE_KIND_HTML,
                    variant = PrefetchStore.VARIANT_FULL,
                    bytes = byteArrayOf(0x3C, 0x21),
                    etag = null,
                )

            assertEquals("OEBPS/ch1.xhtml", entry.resourcePath)
            assertEquals(PrefetchStore.RESOURCE_KIND_HTML, entry.resourceKind)
            assertNull(entry.pageNumber)
            assertTrue(entry.file.exists())
            // 階層ディレクトリ生成(OEBPS/ch1.xhtml_full)
            assertTrue(entry.file.absolutePath.contains("OEBPS"))
            assertEquals(1, dao.all().size)
            assertEquals(
                PrefetchStore.RESOURCE_KIND_HTML,
                dao.find("s1", "b1", "OEBPS/ch1.xhtml", PrefetchStore.VARIANT_FULL)?.resourceKind,
            )
        }

    private class FakePrefetchIndexDao : PrefetchIndexDao {
        private val rows = mutableListOf<PrefetchIndexEntity>()

        fun all(): List<PrefetchIndexEntity> = rows.toList()

        private fun List<PrefetchIndexEntity>.byServer(id: String) = filter { it.serverId == id }

        private fun keyOf(e: PrefetchIndexEntity) = "${e.serverId}|${e.bookId}|${e.resourcePath}|${e.variant}"

        override suspend fun upsert(entity: PrefetchIndexEntity) {
            val key = keyOf(entity)
            rows.removeAll { keyOf(it) == key }
            rows.add(entity)
        }

        override suspend fun find(
            serverId: String,
            bookId: String,
            resourcePath: String,
            variant: String,
        ): PrefetchIndexEntity? =
            rows.firstOrNull {
                it.serverId == serverId &&
                    it.bookId == bookId &&
                    it.resourcePath == resourcePath &&
                    it.variant == variant
            }

        override suspend fun listByBook(
            serverId: String,
            bookId: String,
        ): List<PrefetchIndexEntity> = rows.filter { it.serverId == serverId && it.bookId == bookId }

        override suspend fun listByServer(serverId: String): List<PrefetchIndexEntity> = rows.byServer(serverId)

        override suspend fun listAll(): List<PrefetchIndexEntity> = rows.sortedBy { it.lastAccessedAt }

        override suspend fun sumBytes(): Long = rows.sumOf { it.sizeBytes }

        override suspend fun deleteByKey(
            serverId: String,
            bookId: String,
            resourcePath: String,
            variant: String,
        ) {
            rows.removeAll {
                it.serverId == serverId &&
                    it.bookId == bookId &&
                    it.resourcePath == resourcePath &&
                    it.variant == variant
            }
        }

        override suspend fun deleteByBook(
            serverId: String,
            bookId: String,
        ) {
            rows.removeAll { it.serverId == serverId && it.bookId == bookId }
        }

        override suspend fun deleteByServer(serverId: String) {
            rows.removeAll { it.serverId == serverId }
        }

        override suspend fun touchLastAccessedAt(
            serverId: String,
            bookId: String,
            resourcePath: String,
            variant: String,
            timestamp: Long,
        ) {
            val idx =
                rows.indexOfFirst {
                    it.serverId == serverId &&
                        it.bookId == bookId &&
                        it.resourcePath == resourcePath &&
                        it.variant == variant
                }
            if (idx >= 0) {
                rows[idx] = rows[idx].copy(lastAccessedAt = timestamp)
            }
        }
    }
}
