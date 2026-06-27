package dev.komrd.core.model

sealed interface SearchScope {
    /** 登録された全サーバ横断検索。 */
    data object GlobalAllServers : SearchScope

    /** 単一サーバ内の指定Libraryに絞り込み。`libraryId == null` ならサーバ内全Library横断。 */
    data class Library(
        val libraryId: String?,
    ) : SearchScope
}
