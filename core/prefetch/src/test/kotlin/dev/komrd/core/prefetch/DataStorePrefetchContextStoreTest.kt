package dev.komrd.core.prefetch

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
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
class DataStorePrefetchContextStoreTest {
    @Before
    fun clearStore() {
        // Robolectricがテストメソッド間でfilesDirを再利用しDataStoreが残るため、各テスト前にクリア。
        runBlocking { DataStorePrefetchContextStore(RuntimeEnvironment.getApplication()).clear() }
    }

    @Test
    fun prefetchContext_startsNull() =
        runTest {
            val store = DataStorePrefetchContextStore(RuntimeEnvironment.getApplication())
            assertNull(store.prefetchContext.first())
        }

    @Test
    fun save_then_read_returnsContext() =
        runTest {
            val store = DataStorePrefetchContextStore(RuntimeEnvironment.getApplication())
            store.save(PrefetchContext("s1", "b1", 5, "b2", 30))
            val ctx = store.prefetchContext.first()
            assertEquals("s1", ctx?.serverId)
            assertEquals("b1", ctx?.bookId)
            assertEquals(5, ctx?.currentPage)
            assertEquals("b2", ctx?.nextBookId)
            assertEquals(30, ctx?.nextBookPagesCount)
        }

    @Test
    fun save_withoutNextBook_preservesNullOptionals() =
        runTest {
            val store = DataStorePrefetchContextStore(RuntimeEnvironment.getApplication())
            store.save(PrefetchContext("s1", "b1", 0, null, null))
            val ctx = store.prefetchContext.first()
            assertEquals("s1", ctx?.serverId)
            assertEquals("b1", ctx?.bookId)
            assertEquals(0, ctx?.currentPage)
            assertNull(ctx?.nextBookId)
            assertNull(ctx?.nextBookPagesCount)
        }

    @Test
    fun save_overwritesPreviousContext() =
        runTest {
            val store = DataStorePrefetchContextStore(RuntimeEnvironment.getApplication())
            store.save(PrefetchContext("s1", "b1", 5, "b2", 30))
            store.save(PrefetchContext("s2", "b3", 12, null, null))
            val ctx = store.prefetchContext.first()
            assertEquals("s2", ctx?.serverId)
            assertEquals("b3", ctx?.bookId)
            assertEquals(12, ctx?.currentPage)
            assertNull(ctx?.nextBookId)
            assertNull(ctx?.nextBookPagesCount)
        }

    @Test
    fun clear_removesContext() =
        runTest {
            val store = DataStorePrefetchContextStore(RuntimeEnvironment.getApplication())
            store.save(PrefetchContext("s1", "b1", 5, "b2", 30))
            store.clear()
            assertNull(store.prefetchContext.first())
        }
}
