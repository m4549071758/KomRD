package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.EpubChapter
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.EpubResource
import dev.komrd.core.model.NextBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchPlannerTest {
    @Test
    fun plan_midBook_noNext_returnsOnlyCurrentRemainder() {
        val book = book("b1", pages = listOf(1, 2, 3, 4, 5))

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 3, nextBook = null)

        assertEquals(
            listOf(t("b1", 3), t("b1", 4), t("b1", 5)),
            targets,
        )
    }

    @Test
    fun plan_lastPage_withNext_returnsLastPageAndAllNextBookPages() {
        val book = book("b1", pages = listOf(1, 2, 3))
        val next = NextBook("b2", pagesCount = 4)

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 3, nextBook = next)

        assertEquals(
            listOf(t("b1", 3), t("b2", 1), t("b2", 2), t("b2", 3), t("b2", 4)),
            targets,
        )
    }

    @Test
    fun plan_contextEnd_noNext_returnsOnlyCurrentRemainder() {
        val book = book("b1", pages = listOf(1, 2, 3))

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 2, nextBook = null)

        assertEquals(listOf(t("b1", 2), t("b1", 3)), targets)
    }

    @Test
    fun plan_emptyCurrentBook_withNext_returnsOnlyNextBookPages() {
        val book = book("b1", pages = emptyList())
        val next = NextBook("b2", pagesCount = 2)

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 1, nextBook = next)

        assertEquals(listOf(t("b2", 1), t("b2", 2)), targets)
    }

    @Test
    fun plan_currentPageNumberNotFound_fallsBackToFirstPage() {
        val book = book("b1", pages = listOf(1, 2, 3))
        val next = NextBook("b2", pagesCount = 1)

        // 存在しないページ番号を渡す→先頭から(異常時の安全フォールバック)。
        val targets = PrefetchPlanner.plan(book, currentPageNumber = 99, nextBook = next)

        assertEquals(
            listOf(t("b1", 1), t("b1", 2), t("b1", 3), t("b2", 1)),
            targets,
        )
    }

    @Test
    fun plan_nextBookZeroPages_omitsNextTargets() {
        val book = book("b1", pages = listOf(1, 2))
        val next = NextBook("b2", pagesCount = 0)

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 1, nextBook = next)

        assertEquals(listOf(t("b1", 1), t("b1", 2)), targets)
    }

    @Test
    fun plan_nonContiguousPageNumbers_preservesActualNumbers() {
        // Komgaは通常1..N連続だが、page.numberを実値として扱うことを確認。
        val book = book("b1", pages = listOf(10, 11, 12))

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 11, nextBook = null)

        assertEquals(listOf(t("b1", 11), t("b1", 12)), targets)
    }

    @Test
    fun plan_pdfBook_usesJpegVariant() {
        val book = book("b1", pages = listOf(1, 2), mediaProfile = BookMediaProfile.PDF)

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 1, nextBook = null)

        assertTrue("PDFはjpeg variant", targets.all { it.variant == PrefetchStore.VARIANT_JPEG })
        assertTrue("画像系はPAGE resourceKind", targets.all { it.resourceKind == PrefetchStore.RESOURCE_KIND_PAGE })
    }

    @Test
    fun plan_imageBook_usesFullVariant() {
        val book = book("b1", pages = listOf(1, 2), mediaProfile = BookMediaProfile.IMAGE)

        val targets = PrefetchPlanner.plan(book, currentPageNumber = 1, nextBook = null)

        assertTrue("画像系はfull variant", targets.all { it.variant == PrefetchStore.VARIANT_FULL })
    }

    @Test
    fun planEpub_fromCurrentChapter_returnsRemainingChaptersAndResources() {
        val manifest =
            EpubManifest(
                readingOrder =
                    listOf(
                        EpubChapter("ch1.xhtml", "application/xhtml+xml", "Ch1"),
                        EpubChapter("ch2.xhtml", "application/xhtml+xml", "Ch2"),
                        EpubChapter("ch3.xhtml", "application/xhtml+xml", "Ch3"),
                    ),
                toc = emptyList(),
                resources =
                    listOf(
                        EpubResource("style.css", "text/css", null),
                        EpubResource("images/cover.png", "image/png", "cover"),
                    ),
                title = null,
                readingProgression = "ltr",
            )

        val targets = PrefetchPlanner.planEpub("b1", manifest, currentChapterIndex = 1)

        // 現章(ch2)以降 + resources(CSS/画像)。重複除去。
        val paths = targets.map { it.resourcePath to it.resourceKind }
        assertTrue("ch2.xhtml HTML", paths.contains("ch2.xhtml" to PrefetchStore.RESOURCE_KIND_HTML))
        assertTrue("ch3.xhtml HTML", paths.contains("ch3.xhtml" to PrefetchStore.RESOURCE_KIND_HTML))
        assertTrue("style.css CSS", paths.contains("style.css" to PrefetchStore.RESOURCE_KIND_CSS))
        assertTrue("images/cover.png IMAGE", paths.contains("images/cover.png" to PrefetchStore.RESOURCE_KIND_IMAGE))
        assertTrue("ch1.xhtmlは現章より前で除外", paths.none { it.first == "ch1.xhtml" })
        assertTrue("全target variant=full", targets.all { it.variant == PrefetchStore.VARIANT_FULL })
        assertTrue("全target pageNumber=null", targets.all { it.pageNumber == null })
        assertTrue("全target bookId=b1", targets.all { it.bookId == "b1" })
    }

    @Test
    fun planEpub_deduplicatesSameResourcePathAcrossChaptersAndResources() {
        val manifest =
            EpubManifest(
                readingOrder = listOf(EpubChapter("ch1.xhtml", "application/xhtml+xml", "Ch1")),
                toc = emptyList(),
                resources = listOf(EpubResource("ch1.xhtml", "application/xhtml+xml", null)),
                title = null,
                readingProgression = null,
            )

        val targets = PrefetchPlanner.planEpub("b1", manifest, currentChapterIndex = 0)

        // readingOrderとresourcesで同一hrefは1件に畳み込み。
        assertEquals(1, targets.count { it.resourcePath == "ch1.xhtml" })
    }

    @Test
    fun planEpub_withNextBookManifests_appendsNextChapters() {
        val current =
            EpubManifest(
                readingOrder = listOf(EpubChapter("a.xhtml", "application/xhtml+xml", "A")),
                toc = emptyList(),
                resources = emptyList(),
                title = null,
                readingProgression = null,
            )
        val next =
            EpubManifest(
                readingOrder = listOf(EpubChapter("b.xhtml", "application/xhtml+xml", "B")),
                toc = emptyList(),
                resources = emptyList(),
                title = null,
                readingProgression = null,
            )

        val targets =
            PrefetchPlanner.planEpub("b1", current, currentChapterIndex = 0, nextBookManifests = listOf("b2" to next))

        assertTrue("次冊の章b.xhtmlが追加", targets.any { it.resourcePath == "b.xhtml" })
        assertTrue("次冊target bookId=b2", targets.any { it.bookId == "b2" && it.resourcePath == "b.xhtml" })
    }

    private fun book(
        id: String,
        pages: List<Int>,
        mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE,
    ): BookDetail =
        BookDetail(
            id = id,
            serverId = "s1",
            name = id,
            pages = pages.map { BookPage(number = it, url = "u$it") },
            mediaProfile = mediaProfile,
        )

    private fun t(
        bookId: String,
        pageNumber: Int,
    ): PrefetchTarget =
        PrefetchTarget(
            bookId = bookId,
            resourcePath = pageNumber.toString(),
            resourceKind = PrefetchStore.RESOURCE_KIND_PAGE,
            variant = PrefetchStore.VARIANT_FULL,
            pageNumber = pageNumber,
        )
}
