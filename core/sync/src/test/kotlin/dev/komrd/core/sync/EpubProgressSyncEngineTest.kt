package dev.komrd.core.sync

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.database.dao.EpubProgressQueueDao
import dev.komrd.core.database.entity.EpubProgressQueueEntity
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.EpubLocator
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

class EpubProgressSyncEngineTest {
    private lateinit var ws: MockWebServer
    private lateinit var dao: FakeEpubQueueDao
    private lateinit var engine: EpubProgressSyncEngine

    @Before
    fun setUp() {
        ws = MockWebServer()
        ws.start()
        dao = FakeEpubQueueDao()
        engine =
            EpubProgressSyncEngineImpl(
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
    fun sync_success_putsProgressionAndClearsQueue() =
        runTest {
            ws.enqueue(MockResponse.Builder().code(204).build())

            val locator = locator("OEBPS/ch1.xhtml", progression = 0.5f, totalProgression = 0.25f, position = 1)
            val result = engine.sync(server(), "book-1", locator)

            assertTrue(result is KomgaResult.Success)
            assertNull(dao.find("s1", "book-1"))
            val recorded = ws.takeRequest()
            assertEquals("PUT", recorded.method)
            assertEquals("/api/v1/books/book-1/progression", recorded.target)
            val body = recorded.body?.utf8().orEmpty()
            assertTrue(body.contains("\"href\":\"OEBPS/ch1.xhtml\""))
            assertTrue(body.contains("\"progression\":0.5"))
            assertTrue(body.contains("\"device\""))
            assertTrue(body.contains("\"modified\""))
        }

    @Test
    fun sync_failure_retainsQueueForRetry() =
        runTest {
            ws.enqueue(MockResponse.Builder().code(500).build())

            val locator = locator("OEBPS/ch1.xhtml", progression = 0f, totalProgression = 0f, position = 0)
            val result = engine.sync(server(), "book-1", locator)

            assertTrue(result is KomgaResult.Failure)
            assertTrue((result as KomgaResult.Failure).error is KomgaError.Http)
            // 失敗時はキューへ残留(オフライン/瞬断)。最新locatorが集約済みで残る。
            val entry = dao.find("s1", "book-1")
            assertEquals("book-1", entry?.bookId)
            assertTrue(entry?.locatorJson?.contains("OEBPS/ch1.xhtml") == true)
        }

    @Test
    fun sync_sameBook_aggregatesToLatestBeforePut() =
        runTest {
            // 連続settle: 章頭→スクロール着地。各syncがキューを上書きし、PUTは最新1回。
            ws.enqueue(MockResponse.Builder().code(500).build()) // 1回目失敗(残留)
            ws.enqueue(MockResponse.Builder().code(204).build()) // 2回目成功

            engine.sync(server(), "book-1", locator("ch1.xhtml", 0f, 0f, 0))
            engine.sync(server(), "book-1", locator("ch1.xhtml", 0.75f, 0.375f, 0))

            assertNull(dao.find("s1", "book-1"))
            assertEquals(2, ws.requestCount)
            ws.takeRequest() // 1回目(0f・500失敗)
            val second = ws.takeRequest() // 2回目(0.75f・204成功)
            val body = second.body?.utf8().orEmpty()
            assertTrue(body.contains("\"progression\":0.75"))
        }

    @Test
    fun flushPending_drainsAllBooksForServer() =
        runTest {
            dao.upsert(entry("s1", "b1", "ch1.xhtml", 0.5f, 1L))
            dao.upsert(entry("s1", "b2", "ch2.xhtml", 0.1f, 2L))
            ws.enqueue(MockResponse.Builder().code(204).build())
            ws.enqueue(MockResponse.Builder().code(204).build())

            engine.flushPending(server())

            assertNull(dao.find("s1", "b1"))
            assertNull(dao.find("s1", "b2"))
            assertEquals(2, ws.requestCount)
        }

    @Test
    fun flushPending_leavesFailedEntriesForNextRetry() =
        runTest {
            dao.upsert(entry("s1", "b1", "ch1.xhtml", 0.5f, 1L))
            dao.upsert(entry("s1", "b2", "ch2.xhtml", 0.1f, 2L))
            ws.enqueue(MockResponse.Builder().code(500).build()) // b1 失敗
            ws.enqueue(MockResponse.Builder().code(204).build()) // b2 成功

            engine.flushPending(server())

            assertTrue(dao.find("s1", "b1") != null) // 残留
            assertNull(dao.find("s1", "b2"))
        }

    private fun server() =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = ws.url("/").toString(),
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun locator(
        href: String,
        progression: Float,
        totalProgression: Float,
        position: Int,
    ) = EpubLocator(
        href = href,
        progression = progression,
        totalProgression = totalProgression,
        position = position,
    )

    private fun locatorJson(
        href: String,
        progression: Float,
    ): String = "{\"href\":\"$href\",\"type\":\"application/xhtml+xml\",\"locations\":{\"progression\":$progression}}"

    private fun entry(
        serverId: String,
        bookId: String,
        href: String,
        progression: Float,
        updatedAt: Long,
    ) = EpubProgressQueueEntity(
        serverId = serverId,
        bookId = bookId,
        locatorJson = locatorJson(href, progression),
        updatedAt = updatedAt,
    )

    /** キューをインメモリMap(複合PK)で模倣。upsert=同book上書き(集約)。 */
    private class FakeEpubQueueDao : EpubProgressQueueDao {
        private val map = mutableMapOf<Pair<String, String>, EpubEntry>()

        override suspend fun upsert(entity: EpubEntry) {
            map[entity.serverId to entity.bookId] = entity
        }

        override suspend fun find(
            serverId: String,
            bookId: String,
        ): EpubEntry? = map[serverId to bookId]

        override suspend fun findByServer(id: String): List<EpubEntry> = map.values.filter { it.serverId == id }

        override suspend fun delete(
            serverId: String,
            bookId: String,
        ) {
            map.remove(serverId to bookId)
        }
    }
}

private typealias EpubEntry = EpubProgressQueueEntity
