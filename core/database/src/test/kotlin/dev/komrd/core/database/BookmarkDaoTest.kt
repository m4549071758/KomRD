package dev.komrd.core.database

import androidx.room.Room
import dev.komrd.core.database.dao.BookmarkDao
import dev.komrd.core.database.entity.BookmarkEntity
import dev.komrd.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BookmarkDaoTest {
    private lateinit var db: KomrdDatabase
    private lateinit var dao: BookmarkDao

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KomrdDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.bookmarkDao()
        // FK制約のため親サーバを登録しておく
        runTest { db.serversDao().upsert(server("s1")) }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_then_observeByBook_returnsEntry() =
        runTest {
            dao.upsert(entry("s1", "b1", pageNumber = 5, note = null, createdAt = 100L))
            val list = dao.observeByBook("s1", "b1").first()
            assertEquals(1, list.size)
            assertEquals(5, list.single().pageNumber)
            assertEquals(null, list.single().note)
        }

    @Test
    fun upsert_samePage_overwritesNote_andKeepsOneRow() =
        runTest {
            dao.upsert(entry("s1", "b1", pageNumber = 5, note = null, createdAt = 100L))
            dao.upsert(entry("s1", "b1", pageNumber = 5, note = "memo", createdAt = 200L))

            val list = dao.observeByBook("s1", "b1").first()
            assertEquals(1, list.size)
            assertEquals("memo", list.single().note)
            assertEquals(200L, list.single().createdAt)
        }

    @Test
    fun observeByBook_ordersByPageNumberAsc() =
        runTest {
            dao.upsert(entry("s1", "b1", pageNumber = 9, createdAt = 1L))
            dao.upsert(entry("s1", "b1", pageNumber = 3, createdAt = 2L))
            dao.upsert(entry("s1", "b1", pageNumber = 5, createdAt = 3L))

            val pages = dao.observeByBook("s1", "b1").first().map { it.pageNumber }
            assertEquals(listOf(3, 5, 9), pages)
        }

    @Test
    fun observeByBook_isolatesByServerAndBook() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", pageNumber = 1, createdAt = 1L))
            dao.upsert(entry("s1", "b2", pageNumber = 2, createdAt = 2L))
            dao.upsert(entry("s2", "b1", pageNumber = 3, createdAt = 3L))

            assertEquals(listOf(1), dao.observeByBook("s1", "b1").first().map { it.pageNumber })
            assertEquals(listOf(2), dao.observeByBook("s1", "b2").first().map { it.pageNumber })
            assertEquals(listOf(3), dao.observeByBook("s2", "b1").first().map { it.pageNumber })
        }

    @Test
    fun delete_removesOnlyThatPage() =
        runTest {
            dao.upsert(entry("s1", "b1", pageNumber = 3, createdAt = 1L))
            dao.upsert(entry("s1", "b1", pageNumber = 5, createdAt = 2L))

            dao.delete("s1", "b1", pageNumber = 3)

            val pages = dao.observeByBook("s1", "b1").first().map { it.pageNumber }
            assertEquals(listOf(5), pages)
        }

    @Test
    fun delete_otherServer_sameBookPage_isIsolated() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", pageNumber = 3, createdAt = 1L))
            dao.upsert(entry("s2", "b1", pageNumber = 3, createdAt = 2L))

            dao.delete("s1", "b1", pageNumber = 3)

            assertTrue(dao.observeByBook("s1", "b1").first().isEmpty())
            assertEquals(listOf(3), dao.observeByBook("s2", "b1").first().map { it.pageNumber })
        }

    @Test
    fun deletingServer_cascadesAndDeletesBookmarks() =
        runTest {
            dao.upsert(entry("s1", "b1", pageNumber = 1, createdAt = 1L))
            dao.upsert(entry("s1", "b1", pageNumber = 2, createdAt = 2L))

            db.serversDao().deleteById("s1")

            assertTrue(dao.observeByBook("s1", "b1").first().isEmpty())
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
        pageNumber: Int,
        note: String? = null,
        createdAt: Long,
    ) = BookmarkEntity(
        serverId = serverId,
        bookId = bookId,
        pageNumber = pageNumber,
        note = note,
        createdAt = createdAt,
    )
}
