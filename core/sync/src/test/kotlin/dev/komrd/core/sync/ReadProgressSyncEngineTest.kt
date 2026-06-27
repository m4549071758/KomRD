package dev.komrd.core.sync

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.database.dao.ReadProgressQueueDao
import dev.komrd.core.database.entity.ReadProgressQueueEntity
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.tls.InMemoryServerTrustStore
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadProgressSyncEngineTest {
    private lateinit var ws: MockWebServer
    private lateinit var dao: FakeQueueDao
    private lateinit var engine: ReadProgressSyncEngine

    @Before
    fun setUp() {
        ws = MockWebServer()
        ws.start()
        dao = FakeQueueDao()
        engine =
            ReadProgressSyncEngineImpl(
                clientFactory =
                    KomgaClientFactory(
                        sessionStore = InMemorySessionStore(),
                        trustStore = InMemoryServerTrustStore(),
                    ),
                trustStore = InMemoryServerTrustStore(),
                queueDao = dao,
                clock = { 0L },
            )
    }

    @After
    fun tearDown() {
        ws.close()
    }

    @Test
    fun sync_success_patchesLatestAndClearsQueue() =
        runTest {
            ws.enqueue(MockResponse.Builder().code(200).build())

            val result = engine.sync(server(), "book-1", page = 7, completed = true)

            assertTrue(result is KomgaResult.Success)
            assertNull(dao.find("s1", "book-1"))
            val recorded = ws.takeRequest()
            assertEquals("PATCH", recorded.method)
            assertEquals("/api/v1/books/book-1/read-progress", recorded.target)
            assertEquals("""{"page":7,"completed":true}""", recorded.body?.utf8())
        }

    @Test
    fun sync_failure_retainsQueueForRetry() =
        runTest {
            ws.enqueue(MockResponse.Builder().code(500).build())

            val result = engine.sync(server(), "book-1", page = 5, completed = false)

            assertTrue(result is KomgaResult.Failure)
            assertTrue((result as KomgaResult.Failure).error is KomgaError.Http)
            // 失敗時はキューへ残留(オフライン/瞬断)。最新ページが集約済みで残る。
            val entry = dao.find("s1", "book-1")
            assertEquals(5, entry?.page)
            assertEquals(false, entry?.completed)
        }

    @Test
    fun sync_sameBook_aggregatesToLatestBeforePatch() =
        runTest {
            // 連続settle 5→6→最終7(completed)。各syncがキューを上書きし、PATCHは最新7のみ。
            ws.enqueue(MockResponse.Builder().code(200).build())
            ws.enqueue(MockResponse.Builder().code(500).build()) // 6送信失敗(残留)
            ws.enqueue(MockResponse.Builder().code(200).build()) // 7送信成功

            engine.sync(server(), "book-1", page = 5, completed = false)
            engine.sync(server(), "book-1", page = 6, completed = false)
            val last = engine.sync(server(), "book-1", page = 7, completed = true)

            assertTrue(last is KomgaResult.Success)
            assertNull(dao.find("s1", "book-1"))
            // 最後のPATCH本体は最新(7, completed=true)
            ws.takeRequest() // 5
            ws.takeRequest() // 6
            val third = ws.takeRequest()
            assertEquals("""{"page":7,"completed":true}""", third.body?.utf8())
        }

    @Test
    fun flushPending_drainsAllBooksForServer() =
        runTest {
            // オフライン蓄積: 2book分のキューがある状態で復帰→各book最新を1回PATCH
            dao.upsert(ReadProgressQueueEntity("s1", "b1", page = 3, completed = false, updatedAt = 1L))
            dao.upsert(ReadProgressQueueEntity("s1", "b2", page = 9, completed = true, updatedAt = 2L))
            ws.enqueue(MockResponse.Builder().code(200).build())
            ws.enqueue(MockResponse.Builder().code(200).build())

            engine.flushPending(server())

            assertNull(dao.find("s1", "b1"))
            assertNull(dao.find("s1", "b2"))
            assertEquals(2, ws.requestCount)
        }

    @Test
    fun flushPending_leavesFailedEntriesForNextRetry() =
        runTest {
            dao.upsert(ReadProgressQueueEntity("s1", "b1", page = 3, completed = false, updatedAt = 1L))
            dao.upsert(ReadProgressQueueEntity("s1", "b2", page = 9, completed = true, updatedAt = 2L))
            ws.enqueue(MockResponse.Builder().code(500).build()) // b1 失敗
            ws.enqueue(MockResponse.Builder().code(200).build()) // b2 成功

            engine.flushPending(server())

            assertEquals(3, dao.find("s1", "b1")?.page) // 残留
            assertNull(dao.find("s1", "b2"))
        }

    private fun server() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = ws.url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    /** キューをインメモリMap(複合PK)で模倣。upsert=同book上書き(集約)。 */
    private class FakeQueueDao : ReadProgressQueueDao {
        private val map = mutableMapOf<Pair<String, String>, QueueEntry>()

        override suspend fun upsert(entity: QueueEntry) {
            map[entity.serverId to entity.bookId] = entity
        }

        override suspend fun find(
            serverId: String,
            bookId: String,
        ): QueueEntry? = map[serverId to bookId]

        override suspend fun findByServer(id: String): List<QueueEntry> = map.values.filter { it.serverId == id }

        override suspend fun delete(
            serverId: String,
            bookId: String,
        ) {
            map.remove(serverId to bookId)
        }
    }
}

private typealias QueueEntry = ReadProgressQueueEntity
