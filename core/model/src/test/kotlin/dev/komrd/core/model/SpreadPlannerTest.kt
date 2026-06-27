package dev.komrd.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadPlannerTest {
    @Test
    fun emptyPages_returnsEmpty() {
        assertEquals(
            emptyList<Spread>(),
            planSpreads(emptyList(), ReadingDirection.LEFT_TO_RIGHT, spreadActive = true),
        )
    }

    @Test
    fun singlePage_isAlone() {
        val pages = listOf(page(1))
        assertEquals(
            listOf(listOf(0)),
            planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true).indices(),
        )
    }

    @Test
    fun spreadInactive_allSingle() {
        val pages = listOf(page(1), page(2), page(3), page(4))
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3)),
            planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = false).indices(),
        )
    }

    @Test
    fun nonHorizontalDirection_allSingleEvenIfActive() {
        val pages = listOf(page(1), page(2), page(3), page(4))
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3)),
            planSpreads(pages, ReadingDirection.VERTICAL, spreadActive = true).indices(),
        )
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3)),
            planSpreads(pages, ReadingDirection.WEBTOON, spreadActive = true).indices(),
        )
    }

    @Test
    fun coverAlwaysAlone_thenNormalPairs() {
        // [cover, n, n, n] -> [0],[1,2],[3]
        val pages = listOf(page(1), page(2), page(3), page(4))
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3)),
            planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true).indices(),
        )
    }

    @Test
    fun widePage_isAlone_andBreaksPairing() {
        // [cover, wide, n, n] -> [0],[1],[2,3]
        val pages = listOf(page(1), wide(2), page(3), page(4))
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2, 3)),
            planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true).indices(),
        )
    }

    @Test
    fun widePage_breaksAdjacentPair_thenResumes() {
        // [cover, n, wide, n, n] -> [0],[1],[2],[3,4]
        val pages = listOf(page(1), page(2), wide(3), page(4), page(5))
        assertEquals(
            listOf(listOf(0), listOf(1), listOf(2), listOf(3, 4)),
            planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true).indices(),
        )
    }

    @Test
    fun lastOddPage_isAlone() {
        // [cover, n, n, n, n, n] -> [0],[1,2],[3,4],[5]
        val pages = listOf(page(1), page(2), page(3), page(4), page(5), page(6))
        assertEquals(
            listOf(listOf(0), listOf(1, 2), listOf(3, 4), listOf(5)),
            planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true).indices(),
        )
    }

    @Test
    fun rtl_sameIndicesOrderAsLtr() {
        // RTLでもpageIndices順序はLTRと同一(左右配置は描画層が反転させる)。
        val pages = listOf(page(1), page(2), page(3), page(4))
        val ltr = planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true).indices()
        val rtl = planSpreads(pages, ReadingDirection.RIGHT_TO_LEFT, spreadActive = true).indices()
        assertEquals(ltr, rtl)
        // ペア [1,2] の先頭は1(先に読むページ)。
        val rtlSpreads = planSpreads(pages, ReadingDirection.RIGHT_TO_LEFT, spreadActive = true)
        assertEquals(listOf(1, 2), rtlSpreads[1].pageIndices)
    }

    @Test
    fun nullDimensions_treatedAsNonWide() {
        // 寸法未解決(null)は非ワイド扱い=対化対象。
        val pages = listOf(page(1), page(2, width = null, height = null), page(3), page(4))
        val spreads = planSpreads(pages, ReadingDirection.LEFT_TO_RIGHT, spreadActive = true)
        // page 2 はnullだがワイドではないため [1,2] で対化される。
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), spreads.indices())
        assertTrue(spreads[1].pageIndices == listOf(1, 2))
    }

    private fun List<Spread>.indices(): List<List<Int>> = map { it.pageIndices }

    private fun page(
        num: Int,
        width: Int? = 800,
        height: Int? = 1200,
    ) = BookPage(number = num, url = "u$num", width = width, height = height)

    /** ワイドページ(width > height)。 */
    private fun wide(num: Int) = BookPage(number = num, url = "u$num", width = 1600, height = 1200)
}
