package dev.komrd.core.database

import androidx.room.Room
import dev.komrd.core.database.dao.ServersDao
import dev.komrd.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

// compileSdkは36だが、Robolectricの対応SDKで実行する
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServersDaoTest {
    private lateinit var db: KomrdDatabase
    private lateinit var dao: ServersDao

    private fun entity(
        id: String,
        createdAt: Long,
    ) = ServerEntity(
        id = id,
        name = "name-$id",
        baseUrl = "https://$id",
        authType = "API_KEY",
        username = null,
        secretCiphertext = byteArrayOf(1, 2, 3),
        secretIv = byteArrayOf(9),
        createdAt = createdAt,
    )

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KomrdDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.serversDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_then_findById_returnsSame() =
        runTest {
            val server = entity("s1", 10L)
            dao.upsert(server)
            assertEquals(server, dao.findById("s1"))
        }

    @Test
    fun observeAll_ordersByCreatedAt() =
        runTest {
            dao.upsert(entity("b", 20L))
            dao.upsert(entity("a", 10L))
            val ids = dao.observeAll().first().map { it.id }
            assertEquals(listOf("a", "b"), ids)
        }

    @Test
    fun deleteById_removesOnlyThatServer() =
        runTest {
            dao.upsert(entity("s1", 1L))
            dao.upsert(entity("s2", 2L))
            dao.deleteById("s1")
            assertNull(dao.findById("s1"))
            assertEquals("s2", dao.findById("s2")?.id)
        }
}
