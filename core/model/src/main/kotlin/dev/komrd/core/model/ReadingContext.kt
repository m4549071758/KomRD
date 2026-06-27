package dev.komrd.core.model

sealed interface ReadingContext {
    val id: String

    /** Series経由の読書文脈。次BookはSeries順(`metadata.numberSort`昇順)の次。 */
    data class Series(
        override val id: String,
    ) : ReadingContext

    /** Read List経由の読書文脈。次BookはRead Listの並び順の次。 */
    data class ReadList(
        override val id: String,
    ) : ReadingContext
}
