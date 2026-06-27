package dev.komrd.core.model

/**
 * 1つのServerに属するLibrary（CONTEXT: Library）。
 * [serverId] でサーバ別名前空間に結び付ける。
 */
data class Library(
    val id: String,
    val serverId: String,
    val name: String,
)
