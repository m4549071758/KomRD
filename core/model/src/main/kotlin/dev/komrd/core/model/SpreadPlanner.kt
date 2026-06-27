package dev.komrd.core.model

fun planSpreads(
    pages: List<BookPage>,
    direction: ReadingDirection,
    spreadActive: Boolean,
): List<Spread> {
    // 非活性・非横方向・空は全ページ単独(空の場合は空リスト)。
    if (!spreadActive || !direction.isHorizontal || pages.isEmpty()) {
        return pages.indices.map { Spread(listOf(it)) }
    }
    val spreads = mutableListOf<Spread>()
    // 表紙は常に単独。
    spreads.add(Spread(listOf(0)))
    var i = 1
    while (i < pages.size) {
        if (pages[i].isWide) {
            spreads.add(Spread(listOf(i)))
            i++
            continue
        }
        val next = i + 1
        if (next < pages.size && !pages[next].isWide) {
            spreads.add(Spread(listOf(i, next)))
            i = next + 1
        } else {
            // 次がワイド or 末尾 → 単独。
            spreads.add(Spread(listOf(i)))
            i++
        }
    }
    return spreads
}
