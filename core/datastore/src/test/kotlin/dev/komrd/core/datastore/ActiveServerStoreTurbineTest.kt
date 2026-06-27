package dev.komrd.core.datastore

import app.cash.turbine.test
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
class ActiveServerStoreTurbineTest {
    @Test
    fun activeServerId_emitsAcrossUpdates() =
        runTest {
            val store =
                DataStoreActiveServerStore(RuntimeEnvironment.getApplication(), testDataStoreName("active_turbine"))

            store.activeServerId.test {
                assertNull(awaitItem())
                store.setActive("s1")
                assertEquals("s1", awaitItem())
                store.setActive("s2")
                assertEquals("s2", awaitItem())
                store.clear()
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
