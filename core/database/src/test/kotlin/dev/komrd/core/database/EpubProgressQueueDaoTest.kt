package dev.komrd.core.database

import androidx.room.Room
import dev.komrd.core.database.dao.EpubProgressQueueDao
import dev.komrd.core.database.entity.EpubProgressQueueEntity
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
class EpubProgressQueueDaoTest {
    private lateinit var db: KomrdDatabase
    private lateinit var dao: EpubProgressQueueDao

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KomrdDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.epubProgressQueueDao()
        runTest { db.serversDao().upsert(server("s1")) }
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_then_find_returnsEntry() =
        runTest {
            dao.upsert(entry("s1", "b1", locatorJson = "{\"href\":\"ch1.xhtml\"}", updatedAt = 10L))

            val found = dao.find("s1", "b1")
            assertEquals("{\"href\":\"ch1.xhtml\"}", found?.locatorJson)
            assertEquals(10L, found?.updatedAt)
        }

    @Test
    fun upsert_sameBook_overwritesWithLatest() =
        runTest {
            dao.upsert(entry("s1", "b1", locatorJson = "v1", updatedAt = 10L))
            dao.upsert(entry("s1", "b1", locatorJson = "v2", updatedAt = 20L))

            val found = dao.find("s1", "b1")
            assertEquals("v2", found?.locatorJson)
            assertEquals(20L, found?.updatedAt)
        }

    @Test
    fun findByServer_returnsOnlyThatServer() =
        runTest {
            db.serversDao().upsert(server("s2"))
            dao.upsert(entry("s1", "b1", locatorJson = "a", updatedAt = 1L))
            dao.upsert(entry("s1", "b2", locatorJson = "b", updatedAt = 2L))
            dao.upsert(entry("s2", "b1", locatorJson = "c", updatedAt = 3L))

            val s1 = dao.findByServer("s1")
            assertEquals(2, s1.size)
            assertTrue(s1.all { it.serverId == "s1" })
            val s2 = dao.findByServer("s2")
            assertEquals(1, s2.size)
        }

    @Test
    fun delete_removesOnlyThatEntry() =
        runTest {
            dao.upsert(entry("s1", "b1", locatorJson = "a", updatedAt = 1L))
            dao.upsert(entry("s1", "b2", locatorJson = "b", updatedAt = 2L))

            dao.delete("s1", "b1")

            assertNull(dao.find("s1", "b1"))
            assertEquals("b", dao.find("s1", "b2")?.locatorJson)
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
        locatorJson: String,
        updatedAt: Long,
    ) = EpubProgressQueueEntity(
        serverId = serverId,
        bookId = bookId,
        locatorJson = locatorJson,
        updatedAt = updatedAt,
    )
}
