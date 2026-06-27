package dev.komrd.core.model

enum class SeriesSort {
    /** タイトル昇順。既定。 */
    TITLE_ASC {
        override fun toApiSort(): List<String> = listOf("metadata.titleSort,asc")
    },

    /** タイトル降順。 */
    TITLE_DESC {
        override fun toApiSort(): List<String> = listOf("metadata.titleSort,desc")
    },

    /** 追加日昇順。 */
    DATE_ADDED_ASC {
        override fun toApiSort(): List<String> = listOf("createdDate,asc")
    },

    /** 追加日降順。 */
    DATE_ADDED_DESC {
        override fun toApiSort(): List<String> = listOf("createdDate,desc")
    },

    /** 更新日昇順。 */
    DATE_UPDATED_ASC {
        override fun toApiSort(): List<String> = listOf("lastModifiedDate,asc")
    },

    /** 更新日降順。 */
    DATE_UPDATED_DESC {
        override fun toApiSort(): List<String> = listOf("lastModifiedDate,desc")
    },
    ;

    /** Komga `/list` の `sort` クエリ値を生成。 */
    abstract fun toApiSort(): List<String>
}
