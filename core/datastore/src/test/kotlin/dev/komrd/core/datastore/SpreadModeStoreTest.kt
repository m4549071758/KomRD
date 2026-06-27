package dev.komrd.core.datastore

import dev.komrd.core.model.SpreadMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpreadModeStoreTest {
    @Test
    fun defaultsToLandscapeOnly() =
        runTest {
            val store = DataStoreSpreadModeStore(RuntimeEnvironment.getApplication(), testDataStoreName("spread"))
            assertEquals(SpreadMode.LANDSCAPE_ONLY, store.spreadMode.first())
        }

    @Test
    fun set_then_read_returnsValue() =
        runTest {
            val store = DataStoreSpreadModeStore(RuntimeEnvironment.getApplication(), testDataStoreName("spread"))
            store.set(SpreadMode.ALWAYS)
            assertEquals(SpreadMode.ALWAYS, store.spreadMode.first())
        }

    @Test
    fun set_overwritesPreviousValue() =
        runTest {
            val store = DataStoreSpreadModeStore(RuntimeEnvironment.getApplication(), testDataStoreName("spread"))
            store.set(SpreadMode.ALWAYS)
            store.set(SpreadMode.OFF)
            assertEquals(SpreadMode.OFF, store.spreadMode.first())
        }

    @Test
    fun set_off_then_read_returnsValue() =
        runTest {
            val store = DataStoreSpreadModeStore(RuntimeEnvironment.getApplication(), testDataStoreName("spread"))
            store.set(SpreadMode.OFF)
            assertEquals(SpreadMode.OFF, store.spreadMode.first())
        }
}
