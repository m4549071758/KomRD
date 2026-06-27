package dev.komrd.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesSortTest {
    @Test
    fun toApiSort_returnsMetadataTitleSortAscAscForTitleAsc() {
        assertEquals(listOf("metadata.titleSort,asc"), SeriesSort.TITLE_ASC.toApiSort())
    }

    @Test
    fun toApiSort_returnsMetadataTitleSortDescForTitleDesc() {
        assertEquals(listOf("metadata.titleSort,desc"), SeriesSort.TITLE_DESC.toApiSort())
    }

    @Test
    fun toApiSort_returnsCreatedDateForDateAdded() {
        assertEquals(listOf("createdDate,asc"), SeriesSort.DATE_ADDED_ASC.toApiSort())
        assertEquals(listOf("createdDate,desc"), SeriesSort.DATE_ADDED_DESC.toApiSort())
    }

    @Test
    fun toApiSort_returnsLastModifiedDateForDateUpdated() {
        assertEquals(listOf("lastModifiedDate,asc"), SeriesSort.DATE_UPDATED_ASC.toApiSort())
        assertEquals(listOf("lastModifiedDate,desc"), SeriesSort.DATE_UPDATED_DESC.toApiSort())
    }
}

class ReadStatusFilterTest {
    @Test
    fun toApiValue_returnsNullForAll() {
        assertNull(ReadStatusFilter.ALL.toApiValue())
    }

    @Test
    fun toApiValue_returnsKomgaEnumForOthers() {
        assertEquals("UNREAD", ReadStatusFilter.UNREAD.toApiValue())
        assertEquals("IN_PROGRESS", ReadStatusFilter.IN_PROGRESS.toApiValue())
        assertEquals("READ", ReadStatusFilter.READ.toApiValue())
    }
}

class CollectionTest {
    @Test
    fun holdsDisplayFieldsOnly() {
        val collection =
            Collection(
                id = "c1",
                serverId = "s1",
                name = "My Collection",
                seriesCount = 7,
                thumbnailUrl = "https://example.com/api/v1/collections/c1/thumbnail",
            )
        assertEquals("c1", collection.id)
        assertEquals("s1", collection.serverId)
        assertEquals("My Collection", collection.name)
        assertEquals(7, collection.seriesCount)
        assertEquals("https://example.com/api/v1/collections/c1/thumbnail", collection.thumbnailUrl)
    }
}

class ReadListSummaryTest {
    @Test
    fun holdsDisplayFieldsOnly() {
        val summary =
            ReadListSummary(
                id = "r1",
                serverId = "s1",
                name = "My Read List",
                bookCount = 12,
                thumbnailUrl = "https://example.com/api/v1/readlists/r1/thumbnail",
                summary = "Books I want to read",
            )
        assertEquals("r1", summary.id)
        assertEquals("s1", summary.serverId)
        assertEquals("My Read List", summary.name)
        assertEquals(12, summary.bookCount)
        assertEquals("https://example.com/api/v1/readlists/r1/thumbnail", summary.thumbnailUrl)
        assertEquals("Books I want to read", summary.summary)
    }
}
