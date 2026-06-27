package dev.komrd.core.database

import androidx.room.Room
import dev.komrd.core.database.dao.ServerTrustDao
import dev.komrd.core.database.dao.ServersDao
import dev.komrd.core.database.entity.ServerEntity
import dev.komrd.core.database.entity.ServerTrustEntity
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerTrustDaoTest {
    private lateinit var db: KomrdDatabase
    private lateinit var trustDao: ServerTrustDao
    private lateinit var serversDao: ServersDao

    private fun serverEntity(id: String) =
        ServerEntity(
            id = id,
            name = "name-$id",
            baseUrl = "https://$id",
            authType = "API_KEY",
            username = null,
            secretCiphertext = byteArrayOf(1, 2, 3),
            secretIv = byteArrayOf(9),
            createdAt = 1L,
        )

    private fun trustEntity(serverId: String) =
        ServerTrustEntity(
            serverId = serverId,
            pinnedFingerprintsJson = """["AA:BB"]""",
            customCaCertsPem = "",
            updatedAt = 100L,
        )

    @Before
    fun setup() {
        db =
            Room
                .inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), KomrdDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        serversDao = db.serversDao()
        trustDao = db.serverTrustDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun upsert_then_findById_returnsSame() =
        runTest {
            serversDao.upsert(serverEntity("s1"))
            trustDao.upsert(trustEntity("s1"))
            assertEquals(trustEntity("s1"), trustDao.findById("s1"))
        }

    @Test
    fun upsert_overwritesExistingRow() =
        runTest {
            serversDao.upsert(serverEntity("s1"))
            trustDao.upsert(trustEntity("s1"))
            val updated =
                trustEntity("s1").copy(
                    updatedAt = 200L,
                    pinnedFingerprintsJson = """["CC:DD"]""",
                )
            trustDao.upsert(updated)
            assertEquals(updated, trustDao.findById("s1"))
        }

    @Test
    fun deleteByServerId_removesRow() =
        runTest {
            serversDao.upsert(serverEntity("s1"))
            trustDao.upsert(trustEntity("s1"))
            trustDao.deleteByServerId("s1")
            assertNull(trustDao.findById("s1"))
        }

    @Test
    fun cascadeDelete_whenServerDeleted_removesTrustRow() =
        runTest {
            serversDao.upsert(serverEntity("s1"))
            trustDao.upsert(trustEntity("s1"))
            serversDao.deleteById("s1")
            assertNull(trustDao.findById("s1"))
        }

    @Test
    fun observe_emitsUpsertedRows() =
        runTest {
            serversDao.upsert(serverEntity("s1"))
            serversDao.upsert(serverEntity("s2"))
            trustDao.upsert(trustEntity("s1").copy(updatedAt = 10L))
            trustDao.upsert(trustEntity("s2").copy(updatedAt = 5L))
            val ids =
                trustDao
                    .observe()
                    .first()
                    .map { it.serverId }
                    .sorted()
            assertEquals(listOf("s1", "s2"), ids)
        }
}
