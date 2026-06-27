package dev.komrd.core.data.bookmark

import app.cash.turbine.test
import dev.komrd.core.database.dao.BookmarkDao
import dev.komrd.core.database.entity.BookmarkEntity
import dev.komrd.core.model.Bookmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BookmarkRepositoryTest {
    private lateinit var dao: FakeBookmarkDao

    @Before
    fun setup() {
        dao = FakeBookmarkDao()
    }

    private fun repo() = BookmarkRepositoryImpl(bookmarkDao = dao)

    @Test
    fun observe_emptsInitially_andReflectsDaoState() =
        runTest {
            val repository = repo()
            repository.observe("s1", "b1").test {
                assertTrue(awaitItem().isEmpty())
                dao.upsert(BookmarkEntity("s1", "b1", 3, null, 10L))
                assertEquals(listOf(3), awaitItem().map { it.pageNumber })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun observe_mapsEntityToDomain() =
        runTest {
            val repository = repo()
            dao.upsert(BookmarkEntity("s1", "b1", 5, "memo", 100L))
            val list = repository.observe("s1", "b1").first()
            val bookmark = list.single()
            assertEquals(Bookmark("s1", "b1", 5, "memo", 100L), bookmark)
        }

    @Test
    fun toggle_addsBookmark_whenAbsent() =
        runTest {
            val repository = repo()
            repository.toggle("s1", "b1", pageNumber = 5)

            val entities = dao.observeByBook("s1", "b1").first()
            assertEquals(1, entities.size)
            assertEquals(5, entities.single().pageNumber)
            assertEquals(0, dao.deleteCalls)
        }

    @Test
    fun toggle_removesBookmark_whenPresent() =
        runTest {
            val repository = repo()
            dao.upsert(BookmarkEntity("s1", "b1", 5, null, 10L))

            repository.toggle("s1", "b1", pageNumber = 5)

            assertTrue(dao.observeByBook("s1", "b1").first().isEmpty())
            assertEquals(1, dao.deleteCalls)
            assertEquals(Triple("s1", "b1", 5), dao.deleteCallsArgs.single())
        }

    @Test
    fun toggle_isolatesByPage_otherPagesUntouched() =
        runTest {
            val repository = repo()
            dao.upsert(BookmarkEntity("s1", "b1", 3, null, 10L))

            repository.toggle("s1", "b1", pageNumber = 5)

            val pages =
                dao
                    .observeByBook("s1", "b1")
                    .first()
                    .map { it.pageNumber }
                    .sorted()
            assertEquals(listOf(3, 5), pages)
        }

    @Test
    fun toggle_isolatesByServerAndBook() =
        runTest {
            val repository = repo()
            dao.upsert(BookmarkEntity("s2", "b1", 5, null, 10L))

            repository.toggle("s1", "b1", pageNumber = 5)

            // s2/b1 のしおりは残り、s1/b1 に新規追加
            assertEquals(listOf(5), dao.observeByBook("s2", "b1").first().map { it.pageNumber })
            assertEquals(listOf(5), dao.observeByBook("s1", "b1").first().map { it.pageNumber })
        }

    @Test
    fun delete_removesOnlyThatPage() =
        runTest {
            val repository = repo()
            dao.upsert(BookmarkEntity("s1", "b1", 3, null, 1L))
            dao.upsert(BookmarkEntity("s1", "b1", 5, null, 2L))

            repository.delete("s1", "b1", pageNumber = 3)

            assertEquals(listOf(5), dao.observeByBook("s1", "b1").first().map { it.pageNumber })
        }
}

private class FakeBookmarkDao : BookmarkDao {
    private val byBookFlows = mutableMapOf<Pair<String, String>, MutableStateFlow<List<BookmarkEntity>>>()
    var deleteCalls = 0
        private set
    val deleteCallsArgs = mutableListOf<Triple<String, String, Int>>()

    private fun flowFor(
        serverId: String,
        bookId: String,
    ): MutableStateFlow<List<BookmarkEntity>> = byBookFlows.getOrPut(serverId to bookId) { newBookmarkFlow() }

    private fun newBookmarkFlow(): MutableStateFlow<List<BookmarkEntity>> = MutableStateFlow(emptyList())

    override suspend fun upsert(entity: BookmarkEntity) {
        flowFor(entity.serverId, entity.bookId).update { existing ->
            val others = existing.filter { it.pageNumber != entity.pageNumber }
            (others + entity).sortedBy { it.pageNumber }
        }
    }

    override suspend fun delete(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ) {
        deleteCalls += 1
        deleteCallsArgs.add(Triple(serverId, bookId, pageNumber))
        flowFor(serverId, bookId).update { existing ->
            existing.filter { it.pageNumber != pageNumber }
        }
    }

    override fun observeByBook(
        serverId: String,
        bookId: String,
    ): Flow<List<BookmarkEntity>> = flowFor(serverId, bookId)
}
