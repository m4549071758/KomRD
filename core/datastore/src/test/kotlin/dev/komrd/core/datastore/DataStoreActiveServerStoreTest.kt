package dev.komrd.core.datastore

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DataStoreActiveServerStoreTest {
    @Test
    fun activeServerId_startsNull() =
        runTest {
            val store = DataStoreActiveServerStore(RuntimeEnvironment.getApplication(), testDataStoreName("active"))
            assertNull(store.activeServerId.first())
        }

    @Test
    fun setActive_then_read_returnsId() =
        runTest {
            val store = DataStoreActiveServerStore(RuntimeEnvironment.getApplication(), testDataStoreName("active"))
            store.setActive("s1")
            assertEquals("s1", store.activeServerId.first())
        }

    @Test
    fun clear_removesActiveId() =
        runTest {
            val store = DataStoreActiveServerStore(RuntimeEnvironment.getApplication(), testDataStoreName("active"))
            store.setActive("s1")
            store.clear()
            assertNull(store.activeServerId.first())
        }

    @Test
    fun setActive_overwritesPreviousValue() =
        runTest {
            val store = DataStoreActiveServerStore(RuntimeEnvironment.getApplication(), testDataStoreName("active"))
            store.setActive("s1")
            store.setActive("s2")
            assertEquals("s2", store.activeServerId.first())
        }
}
