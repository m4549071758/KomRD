package dev.komrd.core.datastore

import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.SeriesSort
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
class LibraryFilterStoreTest {
    @Test
    fun defaultsToTitleAscAndAll() =
        runTest {
            val store = DataStoreLibraryFilterStore(RuntimeEnvironment.getApplication(), testDataStoreName("libfilter"))
            val filters = store.filters("lib-1").first()
            assertEquals(SeriesSort.TITLE_ASC, filters.sort)
            assertEquals(ReadStatusFilter.ALL, filters.readStatusFilter)
        }

    @Test
    fun setSort_then_read_returnsValue() =
        runTest {
            val store = DataStoreLibraryFilterStore(RuntimeEnvironment.getApplication(), testDataStoreName("libfilter"))
            store.setSort("lib-1", SeriesSort.DATE_ADDED_DESC)
            val filters = store.filters("lib-1").first()
            assertEquals(SeriesSort.DATE_ADDED_DESC, filters.sort)
            assertEquals(ReadStatusFilter.ALL, filters.readStatusFilter)
        }

    @Test
    fun setReadStatusFilter_then_read_returnsValue() =
        runTest {
            val store = DataStoreLibraryFilterStore(RuntimeEnvironment.getApplication(), testDataStoreName("libfilter"))
            store.setReadStatusFilter("lib-1", ReadStatusFilter.UNREAD)
            val filters = store.filters("lib-1").first()
            assertEquals(SeriesSort.TITLE_ASC, filters.sort)
            assertEquals(ReadStatusFilter.UNREAD, filters.readStatusFilter)
        }

    @Test
    fun settingsArePerLibrary() =
        runTest {
            val store = DataStoreLibraryFilterStore(RuntimeEnvironment.getApplication(), testDataStoreName("libfilter"))
            store.setSort("lib-1", SeriesSort.TITLE_DESC)
            store.setReadStatusFilter("lib-2", ReadStatusFilter.READ)
            val one = store.filters("lib-1").first()
            val two = store.filters("lib-2").first()
            assertEquals(SeriesSort.TITLE_DESC, one.sort)
            assertEquals(ReadStatusFilter.ALL, one.readStatusFilter)
            assertEquals(SeriesSort.TITLE_ASC, two.sort)
            assertEquals(ReadStatusFilter.READ, two.readStatusFilter)
        }

    @Test
    fun setSort_overwritesPreviousValue() =
        runTest {
            val store = DataStoreLibraryFilterStore(RuntimeEnvironment.getApplication(), testDataStoreName("libfilter"))
            store.setSort("lib-1", SeriesSort.TITLE_DESC)
            store.setSort("lib-1", SeriesSort.DATE_UPDATED_ASC)
            assertEquals(SeriesSort.DATE_UPDATED_ASC, store.filters("lib-1").first().sort)
        }
}
