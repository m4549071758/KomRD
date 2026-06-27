package dev.komrd.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class KomgaThumbnailsTest {
    @Test
    fun seriesThumbnailUrl_appendsApiPath() {
        assertEquals(
            "http://example.com/api/v1/series/series-1/thumbnail",
            KomgaThumbnails.seriesThumbnailUrl("http://example.com", "series-1"),
        )
    }

    @Test
    fun bookThumbnailUrl_appendsApiPath() {
        assertEquals(
            "http://example.com/api/v1/books/book-1/thumbnail",
            KomgaThumbnails.bookThumbnailUrl("http://example.com", "book-1"),
        )
    }

    @Test
    fun bookPageUrl_usesOneBasedPageNumber() {
        assertEquals(
            "http://example.com/api/v1/books/book-1/pages/3",
            KomgaThumbnails.bookPageUrl("http://example.com", "book-1", 3),
        )
    }

    @Test
    fun bookPageUrl_withConvert_appendsConvertQuery() {
        assertEquals(
            "http://example.com/api/v1/books/book-1/pages/3?convert=jpeg",
            KomgaThumbnails.bookPageUrl("http://example.com", "book-1", 3, convert = "jpeg"),
        )
    }

    @Test
    fun bookPageUrl_withNullConvert_omitsQuery() {
        assertEquals(
            "http://example.com/api/v1/books/book-1/pages/3",
            KomgaThumbnails.bookPageUrl("http://example.com", "book-1", 3, convert = null),
        )
    }

    @Test
    fun bookPageThumbnailUrl_appendsThumbnailPath() {
        assertEquals(
            "http://example.com/api/v1/books/book-1/pages/3/thumbnail",
            KomgaThumbnails.bookPageThumbnailUrl("http://example.com", "book-1", 3),
        )
    }

    @Test
    fun collectionThumbnailUrl_appendsCollectionsPath() {
        assertEquals(
            "http://example.com/api/v1/collections/c1/thumbnail",
            KomgaThumbnails.collectionThumbnailUrl("http://example.com", "c1"),
        )
    }

    @Test
    fun readListThumbnailUrl_appendsReadlistsPath() {
        assertEquals(
            "http://example.com/api/v1/readlists/r1/thumbnail",
            KomgaThumbnails.readListThumbnailUrl("http://example.com", "r1"),
        )
    }

    @Test
    fun trailingSlashInBaseUrl_isCollapsed() {
        // trimmedBaseUrl() の挙動が変わっていないことを確認(回帰)
        assertEquals(
            "http://example.com/api/v1/collections/c1/thumbnail",
            KomgaThumbnails.collectionThumbnailUrl("http://example.com/", "c1"),
        )
    }
}
