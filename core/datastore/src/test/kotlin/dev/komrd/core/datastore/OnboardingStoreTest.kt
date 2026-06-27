package dev.komrd.core.datastore

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
class OnboardingStoreTest {
    @Test
    fun defaultsToFalse() =
        runTest {
            val store = DataStoreOnboardingStore(RuntimeEnvironment.getApplication(), testDataStoreName("onboarding"))
            assertEquals(false, store.readingDirectionFirstLaunchDone.first())
        }

    @Test
    fun markDone_setsTrue() =
        runTest {
            val store = DataStoreOnboardingStore(RuntimeEnvironment.getApplication(), testDataStoreName("onboarding"))
            store.markReadingDirectionFirstLaunchDone()
            assertEquals(true, store.readingDirectionFirstLaunchDone.first())
        }
}
