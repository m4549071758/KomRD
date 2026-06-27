package dev.komrd.core.database

import androidx.room.Room
import dev.komrd.core.database.dao.PrefetchIndexDao
import dev.komrd.core.database.entity.PrefetchIndexEntity
import dev.komrd.core.database.entity.ServerEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchIndexDaoTest {
    private lateinit var db: KomrdDatabase
    private lateinit var dao: PrefetchIndexDao

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KomrdDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.prefetchIndexDao()
        // FK制約のため親サーバを登録しておく
        runTest { db.serversDao().upsert(server("s1")) }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_then_find_returnsEntry() =
        runTest {
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 100L, fetchedAt = 10L))

            val found = dao.find("s1", "b1", "2", "full")
            assertEquals("full", found?.variant)
            assertEquals(100L, found?.sizeBytes)
            assertEquals(10L, found?.fetchedAt)
            assertNull(found?.etag)
            assertEquals("PAGE", found?.resourceKind)
            assertEquals(2, found?.pageNumber)
        }

    @Test
    fun upsert_sameKey_overwritesWithLatest() =
        runTest {
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 100L, fetchedAt = 10L, etag = "e1"))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 200L, fetchedAt = 20L, etag = null))

            val found = dao.find("s1", "b1", "2", "full")
            assertEquals(200L, found?.sizeBytes)
            assertEquals(20L, found?.fetchedAt)
            assertNull(found?.etag)
        }

    @Test
    fun samePage_differentVariant_coexist() =
        runTest {
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 100L, fetchedAt = 10L))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 50L, fetchedAt = 11L, variant = "zero"))

            assertEquals(100L, dao.find("s1", "b1", "2", "full")?.sizeBytes)
            assertEquals(50L, dao.find("s1", "b1", "2", "zero")?.sizeBytes)
        }

    @Test
    fun listByBook_and_listByServer_isolateByServer() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", "1", sizeBytes = 10L, fetchedAt = 1L))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 20L, fetchedAt = 2L))
            dao.upsert(entry("s1", "b2", "1", sizeBytes = 30L, fetchedAt = 3L))
            dao.upsert(entry("s2", "b1", "1", sizeBytes = 40L, fetchedAt = 4L))

            assertEquals(2, dao.listByBook("s1", "b1").size)
            assertEquals(3, dao.listByServer("s1").size)
            assertTrue(dao.listByServer("s2").all { it.serverId == "s2" })
        }

    @Test
    fun sumBytes_aggregatesAllServers() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", "1", sizeBytes = 100L, fetchedAt = 1L))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 200L, fetchedAt = 2L))
            dao.upsert(entry("s2", "b1", "1", sizeBytes = 300L, fetchedAt = 3L))

            assertEquals(600L, dao.sumBytes())
        }

    @Test
    fun sumBytes_empty_returnsZero() = runTest { assertEquals(0L, dao.sumBytes()) }

    @Test
    fun listAll_returnsAllServers_orderedByLastAccessedAt() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", "1", sizeBytes = 10L, fetchedAt = 1L))
            dao.upsert(entry("s2", "b1", "1", sizeBytes = 30L, fetchedAt = 3L))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 20L, fetchedAt = 2L))
            dao.touchLastAccessedAt("s1", "b1", "2", "full", 5L)

            val all = dao.listAll()
            assertEquals(3, all.size)
            assertEquals(listOf(1L, 3L, 5L), all.map { it.lastAccessedAt })
        }

    @Test
    fun deleteByKey_removesOnlyThatEntry() =
        runTest {
            dao.upsert(entry("s1", "b1", "1", sizeBytes = 10L, fetchedAt = 1L))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 20L, fetchedAt = 2L))

            dao.deleteByKey("s1", "b1", "1", "full")

            assertNull(dao.find("s1", "b1", "1", "full"))
            assertEquals(20L, dao.find("s1", "b1", "2", "full")?.sizeBytes)
        }

    @Test
    fun deleteByServer_removesAllForServer() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", "1", sizeBytes = 10L, fetchedAt = 1L))
            dao.upsert(entry("s1", "b2", "1", sizeBytes = 20L, fetchedAt = 2L))
            dao.upsert(entry("s2", "b1", "1", sizeBytes = 30L, fetchedAt = 3L))

            dao.deleteByServer("s1")

            assertTrue(dao.listByServer("s1").isEmpty())
            assertEquals(1, dao.listByServer("s2").size)
        }

    @Test
    fun touchLastAccessedAt_updatesOnlyThatEntry() =
        runTest {
            dao.upsert(entry("s1", "b1", "1", sizeBytes = 10L, fetchedAt = 1L))
            dao.upsert(entry("s1", "b1", "2", sizeBytes = 20L, fetchedAt = 2L))

            dao.touchLastAccessedAt("s1", "b1", "1", "full", 999L)

            assertEquals(999L, dao.find("s1", "b1", "1", "full")?.lastAccessedAt)
            assertEquals(2L, dao.find("s1", "b1", "2", "full")?.lastAccessedAt)
        }

    @Test
    fun upsert_epubHtmlEntry_preservesNullPageNumberAndResourceKind() =
        runTest {
            dao.upsert(
                PrefetchIndexEntity(
                    serverId = "s1",
                    bookId = "b1",
                    pageNumber = null,
                    variant = "full",
                    resourcePath = "OEBPS/ch1.xhtml",
                    resourceKind = "HTML",
                    filePath = "/tmp/p",
                    sizeBytes = 512L,
                    fetchedAt = 10L,
                    lastAccessedAt = 10L,
                    etag = null,
                ),
            )

            val found = dao.find("s1", "b1", "OEBPS/ch1.xhtml", "full")
            assertEquals("HTML", found?.resourceKind)
            assertNull(found?.pageNumber)
            assertEquals("OEBPS/ch1.xhtml", found?.resourcePath)
        }

    private fun server(id: String) =
        ServerEntity(
            id = id,
            name = "name-$id",
            baseUrl = "https://$id",
            authType = "API_KEY",
            username = null,
            secretCiphertext = byteArrayOf(1, 2, 3),
            secretIv = byteArrayOf(9),
            createdAt = 0L,
        )

    private fun entry(
        serverId: String,
        bookId: String,
        resourcePath: String,
        sizeBytes: Long,
        fetchedAt: Long,
        variant: String = "full",
        etag: String? = null,
    ) = PrefetchIndexEntity(
        serverId = serverId,
        bookId = bookId,
        pageNumber = resourcePath.toIntOrNull(),
        variant = variant,
        resourcePath = resourcePath,
        resourceKind = "PAGE",
        filePath = "/tmp/$serverId/$bookId/${resourcePath}_$variant",
        sizeBytes = sizeBytes,
        fetchedAt = fetchedAt,
        lastAccessedAt = fetchedAt,
        etag = etag,
    )
}
