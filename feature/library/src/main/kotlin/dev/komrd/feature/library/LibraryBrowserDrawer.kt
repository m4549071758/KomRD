package dev.komrd.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.HorizontalDivider
import dev.komrd.core.designsystem.components.NavigationDrawerItem
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ReadListSummary

/**
 * サーバごとLibrary/Collection/ReadListツリーを表示するドロワー本体
 * （Issue #68: Home/Library両画面で再利用する共通部品 / M5-05 でCollection/ReadList拡張）。
 *
 * - [state]の[LibraryUiState.serverGroups]をサーバヘッダ+Library/Collection/ReadList行のリストで描画する。
 * - [onSelectServer]が非nullのときサーバヘッダをタップ可能にし、サーバ切替導線として使う（Home画面向け）。
 *   Library画面ではnullを渡し、サーバヘッダは非タップ表示にする。
 * - Library行の選択状態は[LibraryUiState.selectedServer]/[selectedLibrary]でマーク付けする。
 * - Collection/ReadList行は選択状態を持たず、それぞれ[onOpenCollection]/[onOpenReadList]で開く。
 */
@Composable
@Suppress("LongParameterList")
fun LibraryBrowserDrawer(
    state: LibraryUiState,
    onSelectLibrary: (serverId: String, libraryId: String) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenReadList: (ReadListSummary) -> Unit,
    onAddServer: () -> Unit,
    onSelectServer: ((serverId: String) -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 16.dp)) {
        Text(
            text = stringResource(R.string.library_drawer_title),
            style = KomrdTheme.typography.h3,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            state.serverGroups.forEach { group ->
                serverGroupItems(
                    group = group,
                    state = state,
                    onSelectLibrary = onSelectLibrary,
                    onOpenCollection = onOpenCollection,
                    onOpenReadList = onOpenReadList,
                    onSelectServer = onSelectServer,
                )
            }
            item(key = "add-server") {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Button(
                    text = stringResource(R.string.server_add_button),
                    variant = ButtonVariant.Ghost,
                    onClick = onAddServer,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.collectionItems(
    group: ServerLibraries,
    onOpenCollection: (Collection) -> Unit,
) {
    item(key = "cols-header-" + group.server.id) {
        SectionHeader(stringResource(R.string.collection_section_header))
    }
    items(group.collections, key = { c -> group.server.id + "/c/" + c.id }) { collection ->
        NavigationDrawerItem(
            label = { Text(collection.name) },
            selected = false,
            onClick = { onOpenCollection(collection) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.readListItems(
    group: ServerLibraries,
    onOpenReadList: (ReadListSummary) -> Unit,
) {
    item(key = "rls-header-" + group.server.id) { SectionHeader(stringResource(R.string.readlist_section_header)) }
    items(group.readLists, key = { r -> group.server.id + "/r/" + r.id }) { readList ->
        NavigationDrawerItem(
            label = { Text(readList.name) },
            selected = false,
            onClick = { onOpenReadList(readList) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = KomrdTheme.typography.label2,
        color = KomrdTheme.colors.textSecondary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** 1サーバグループ分のドロワー項目（サーバ名 + Library/Collection/ReadList セクション）を描画。 */
@Suppress("LongParameterList")
private fun androidx.compose.foundation.lazy.LazyListScope.serverGroupItems(
    group: ServerLibraries,
    state: LibraryUiState,
    onSelectLibrary: (serverId: String, libraryId: String) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenReadList: (ReadListSummary) -> Unit,
    onSelectServer: ((serverId: String) -> Unit)?,
) {
    item(key = "server-" + group.server.id) {
        val serverSelectable = onSelectServer != null
        NavigationDrawerItem(
            label = { Text(group.server.name) },
            selected = false,
            onClick = { if (serverSelectable) onSelectServer?.invoke(group.server.id) },
            modifier = Modifier.padding(horizontal = 8.dp),
        )
    }
    if (group.libraries.isNotEmpty()) {
        item(key = "libs-header-" + group.server.id) { SectionHeader(stringResource(R.string.library_section_header)) }
        items(group.libraries, key = { lib -> group.server.id + "/" + lib.id }) { library ->
            val selected =
                group.server.id == state.selectedServer?.id && library.id == state.selectedLibrary?.id
            NavigationDrawerItem(
                label = { Text(library.name) },
                selected = selected,
                onClick = { onSelectLibrary(group.server.id, library.id) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        }
    }
    if (group.collections.isNotEmpty()) {
        collectionItems(group, onOpenCollection)
    }
    if (group.readLists.isNotEmpty()) {
        readListItems(group, onOpenReadList)
    }
    if (group.error != null) {
        item(key = "error-" + group.server.id) {
            Text(
                text = stringResource(R.string.library_load_error),
                style = KomrdTheme.typography.body3,
                color = KomrdTheme.colors.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
