package dev.komrd.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import dev.komrd.R
import kotlinx.serialization.Serializable

sealed interface KomrdRoute

@Serializable
data object HomeDestination : KomrdRoute

@Serializable
data class LibraryDestination(
    val serverId: String? = null,
    val libraryId: String? = null,
) : KomrdRoute

@Serializable
data object SearchDestination : KomrdRoute

@Serializable
data object SettingsDestination : KomrdRoute

/** 設定/ライブラリ未登録から開くサーバ管理画面（ボトムタブには出さない）。 */
@Serializable
data object ServersDestination : KomrdRoute

@Serializable
data class ServerSettingsDestination(
    val serverId: String,
) : KomrdRoute

@Serializable
data object ReadingDirectionOnboardingDestination : KomrdRoute

/** シリーズ詳細（ブック一覧）。 */
@Serializable
data class SeriesDetailDestination(
    val serverId: String,
    val seriesId: String,
    val seriesName: String,
) : KomrdRoute

@Serializable
data class CollectionDetailDestination(
    val serverId: String,
    val collectionId: String,
    val collectionName: String,
) : KomrdRoute

@Serializable
data class ReadListDetailDestination(
    val serverId: String,
    val readListId: String,
    val readListName: String,
) : KomrdRoute

/** ブック概要（サムネイル・メタデータ・読むボタン）。 */
@Serializable
data class BookDetailDestination(
    val serverId: String,
    val bookId: String,
    val readListId: String? = null,
) : KomrdRoute

/** リーダー（ページ画像）。 */
@Serializable
data class ReaderDestination(
    val serverId: String,
    val bookId: String,
    val readListId: String? = null,
) : KomrdRoute

@Serializable
data class EpubReaderDestination(
    val serverId: String,
    val bookId: String,
    val readListId: String? = null,
) : KomrdRoute

enum class TopLevelDestination(
    val route: KomrdRoute,
    @StringRes val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(HomeDestination, R.string.bottom_nav_home, Icons.Default.Home),
    SEARCH(SearchDestination, R.string.bottom_nav_search, Icons.Default.Search),
    SETTINGS(SettingsDestination, R.string.bottom_nav_settings, Icons.Default.Settings),
}
