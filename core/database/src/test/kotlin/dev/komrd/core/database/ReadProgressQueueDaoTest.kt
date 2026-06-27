package dev.komrd.core.database

import androidx.room.Room
import dev.komrd.core.database.dao.ReadProgressQueueDao
import dev.komrd.core.database.entity.ReadProgressQueueEntity
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
class ReadProgressQueueDaoTest {
    private lateinit var db: KomrdDatabase
    private lateinit var dao: ReadProgressQueueDao

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KomrdDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.readProgressQueueDao()
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
            dao.upsert(entry("s1", "b1", page = 5, completed = false, updatedAt = 100L))
            val found = dao.find("s1", "b1")
            assertEquals(5, found?.page)
            assertEquals(false, found?.completed)
        }

    @Test
    fun upsert_sameBook_overwritesWithLatest_pageAggregation() =
        runTest {
            dao.upsert(entry("s1", "b1", page = 5, completed = false, updatedAt = 100L))
            dao.upsert(entry("s1", "b1", page = 6, completed = false, updatedAt = 200L))
            dao.upsert(entry("s1", "b1", page = 7, completed = true, updatedAt = 300L))

            val found = dao.find("s1", "b1")
            assertEquals(7, found?.page)
            assertEquals(true, found?.completed)
            assertEquals(300L, found?.updatedAt)
        }

    @Test
    fun findByServer_isolatesByServer() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", page = 3, completed = false, updatedAt = 1L))
            dao.upsert(entry("s1", "b2", page = 9, completed = true, updatedAt = 2L))
            dao.upsert(entry("s2", "b1", page = 1, completed = false, updatedAt = 3L))

            val s1 = dao.findByServer("s1")
            assertEquals(2, s1.size)
            assertTrue(s1.any { it.bookId == "b1" && it.page == 3 })
            assertTrue(s1.any { it.bookId == "b2" && it.page == 9 })
            assertTrue(dao.findByServer("s2").all { it.serverId == "s2" })
        }

    @Test
    fun delete_removesOnlyThatBook() =
        runTest {
            dao.upsert(entry("s1", "b1", page = 3, completed = false, updatedAt = 1L))
            dao.upsert(entry("s1", "b2", page = 4, completed = false, updatedAt = 2L))

            dao.delete("s1", "b1")

            assertNull(dao.find("s1", "b1"))
            assertEquals(4, dao.find("s1", "b2")?.page)
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
        page: Int,
        completed: Boolean,
        updatedAt: Long,
    ) = ReadProgressQueueEntity(
        serverId = serverId,
        bookId = bookId,
        page = page,
        completed = completed,
        updatedAt = updatedAt,
    )
}
