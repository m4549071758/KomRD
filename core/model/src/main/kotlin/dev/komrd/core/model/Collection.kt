package dev.komrd.core.model

/**
 * KomgaのCollection（CONTEXT: Collection）。横断的にグルーピングされた順序付きSeries群。
 *
 * 表示用に最適化した軽量表現。`seriesIds` 等の内部状態は持たない（[CollectionDto][dev.komrd.core.network.dto.CollectionDto]
 * で参照する）。`seriesCount` は UI のバッジ用途。`thumbnailUrl` はサーバ別 OkHttp クライアントが認証/TLS を付与する。
 * [serverId] はドロワーから詳細画面へ遷移する際のサーバ特定用（Komga単独だがアプリは複数サーバ登録可）。
 */
data class Collection(
    val id: String,
    val serverId: String,
    val name: String,
    val seriesCount: Int,
    val thumbnailUrl: String,
)
