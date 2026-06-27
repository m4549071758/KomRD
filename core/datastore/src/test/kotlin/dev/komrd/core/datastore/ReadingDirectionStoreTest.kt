package dev.komrd.core.datastore

import dev.komrd.core.model.ReadingDirection
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
class ReadingDirectionStoreTest {
    @Test
    fun defaultsToLeftToRight() =
        runTest {
            val store =
                DataStoreReadingDirectionStore(RuntimeEnvironment.getApplication(), testDataStoreName("reading_dir"))
            assertEquals(ReadingDirection.LEFT_TO_RIGHT, store.readingDirection.first())
        }

    @Test
    fun set_then_read_returnsValue() =
        runTest {
            val store =
                DataStoreReadingDirectionStore(RuntimeEnvironment.getApplication(), testDataStoreName("reading_dir"))
            store.set(ReadingDirection.RIGHT_TO_LEFT)
            assertEquals(ReadingDirection.RIGHT_TO_LEFT, store.readingDirection.first())
        }

    @Test
    fun set_overwritesPreviousValue() =
        runTest {
            val store =
                DataStoreReadingDirectionStore(RuntimeEnvironment.getApplication(), testDataStoreName("reading_dir"))
            store.set(ReadingDirection.RIGHT_TO_LEFT)
            store.set(ReadingDirection.LEFT_TO_RIGHT)
            assertEquals(ReadingDirection.LEFT_TO_RIGHT, store.readingDirection.first())
        }

    @Test
    fun set_vertical_then_read_returnsValue() =
        runTest {
            val store =
                DataStoreReadingDirectionStore(RuntimeEnvironment.getApplication(), testDataStoreName("reading_dir"))
            store.set(ReadingDirection.VERTICAL)
            assertEquals(ReadingDirection.VERTICAL, store.readingDirection.first())
        }

    @Test
    fun set_webtoon_then_read_returnsValue() =
        runTest {
            val store =
                DataStoreReadingDirectionStore(RuntimeEnvironment.getApplication(), testDataStoreName("reading_dir"))
            store.set(ReadingDirection.WEBTOON)
            assertEquals(ReadingDirection.WEBTOON, store.readingDirection.first())
        }
}
