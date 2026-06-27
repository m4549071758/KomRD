package dev.komrd.core.model

enum class ReadStatusFilter {
    /** 絞り込みなし（condition を付与しない）。 */
    ALL,

    /** 未読。 */
    UNREAD,

    /** 読書中。 */
    IN_PROGRESS,

    /** 読了。 */
    READ,
    ;

    /** Komga が受け付ける readStatus 文字列。`ALL` は null を返す（condition 付与なし）。 */
    fun toApiValue(): String? =
        when (this) {
            ALL -> null
            UNREAD -> "UNREAD"
            IN_PROGRESS -> "IN_PROGRESS"
            READ -> "READ"
        }
}
