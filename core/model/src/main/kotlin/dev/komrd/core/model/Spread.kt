package dev.komrd.core.model

data class Spread(
    val pageIndices: List<Int>,
) {
    val isSingle: Boolean get() = pageIndices.size == 1
    val firstIndex: Int get() = pageIndices.first()

    init {
        require(pageIndices.isNotEmpty()) { "Spread must contain at least one page" }
        require(pageIndices.size <= MAX_PAGES) { "A spread can contain at most $MAX_PAGES pages" }
    }

    private companion object {
        const val MAX_PAGES = 2
    }
}
