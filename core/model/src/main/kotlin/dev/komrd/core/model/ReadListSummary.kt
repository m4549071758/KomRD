package dev.komrd.core.model

/**
 * KomgaのRead Listのサマリ（CONTEXT: Read List）。Seriesをまたいで順序付きにグルーピングされたBook群。
 *
 * 表示用に最適化した軽量表現。`bookIds` 等の内部状態は持たない。
 * 一覧画面・ドロワー用。詳細画面では別途[dev.komrd.core.network.KomgaClient.getReadList]で詳細を取得する。
 * [summary] は Komga の Read List に付随する自由テキスト（任意・空文字可）。
 * [serverId] はドロワーから詳細画面へ遷移する際のサーバ特定用（Komga単独だがアプリは複数サーバ登録可）。
 */
data class ReadListSummary(
    val id: String,
    val serverId: String,
    val name: String,
    val bookCount: Int,
    val thumbnailUrl: String,
    val summary: String,
)
