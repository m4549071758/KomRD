package dev.komrd.feature.library

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.model.Collection
import dev.komrd.core.model.Library
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.SeriesSort
import dev.komrd.core.model.Server

data class ServerLibraries(
    val server: Server,
    val libraries: List<Library>,
    val collections: List<Collection> = emptyList(),
    val readLists: List<ReadListSummary> = emptyList(),
    val error: KomgaError? = null,
)

/** ライブラリ画面のUI状態。 */
data class LibraryUiState(
    /** 初回のサーバ/Library読み込み中。 */
    val loading: Boolean = true,
    val noServer: Boolean = false,
    /** ドロワー用: サーバごとにグルーピングしたLibrary一覧。 */
    val serverGroups: List<ServerLibraries> = emptyList(),
    val selectedServer: Server? = null,
    val selectedLibrary: Library? = null,
    /** 選択中サーバのLibrary取得失敗（初回ロード失敗時のエラー表示用）。 */
    val selectedServerError: KomgaError? = null,
    val currentSort: SeriesSort = SeriesSort.TITLE_ASC,
    val readStatusFilter: ReadStatusFilter = ReadStatusFilter.ALL,
) {
    val selectedServerLibraries: List<Library>
        get() = serverGroups.firstOrNull { it.server.id == selectedServer?.id }?.libraries.orEmpty()
}
