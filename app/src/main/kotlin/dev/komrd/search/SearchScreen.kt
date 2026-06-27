package dev.komrd.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import dev.komrd.R
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.DropdownMenu
import dev.komrd.core.designsystem.components.DropdownMenuItem
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Switch
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.card.OutlinedCard
import dev.komrd.core.designsystem.components.textfield.OutlinedTextField
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.designsystem.components.topbar.TopBarDefaults
import dev.komrd.core.model.Library
import dev.komrd.core.model.Server
import dev.komrd.feature.library.ThumbnailGrid

@Composable
fun SearchRoute(
    onAddServer: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val series = viewModel.seriesPaging.collectAsLazyPagingItems()
    val books = viewModel.booksPaging.collectAsLazyPagingItems()
    val searchTitle = stringResource(R.string.search_title)

    Scaffold(
        topBar = {
            TopBar(scrollBehavior = TopBarDefaults.pinnedScrollBehavior()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = searchTitle, style = KomrdTheme.typography.h3)
                    if (state.servers.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(16.dp))
                        ServerSelector(
                            activeServerName = state.activeServer?.name,
                            servers = state.servers,
                            onSelect = viewModel::onSelectServer,
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.servers.isEmpty()) {
                Button(
                    text = stringResource(R.string.search_add_server),
                    variant = ButtonVariant.Primary,
                    onClick = onAddServer,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                SearchContent(
                    state = state,
                    series = series,
                    books = books,
                    imageLoaderFor = viewModel::imageLoaderFor,
                    onQueryChanged = viewModel::onQueryChanged,
                    onTabSelected = viewModel::onTabSelected,
                    onSelectLibrary = viewModel::onSelectLibrary,
                    onToggleGlobal = viewModel::onToggleGlobalAllServers,
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SearchContent(
    state: SearchUiState,
    series: androidx.paging.compose.LazyPagingItems<dev.komrd.core.model.Series>,
    books: androidx.paging.compose.LazyPagingItems<dev.komrd.core.model.Book>,
    imageLoaderFor: (Server) -> coil3.ImageLoader,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (SearchTab) -> Unit,
    onSelectLibrary: (String?) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        SearchControls(
            state = state,
            onQueryChanged = onQueryChanged,
            onTabSelected = onTabSelected,
            onSelectLibrary = onSelectLibrary,
            onToggleGlobal = onToggleGlobal,
        )
        Spacer(modifier = Modifier.height(12.dp))
        SearchResults(
            state = state,
            series = series,
            books = books,
            imageLoaderFor = imageLoaderFor,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun SearchControls(
    state: SearchUiState,
    onQueryChanged: (String) -> Unit,
    onTabSelected: (SearchTab) -> Unit,
    onSelectLibrary: (String?) -> Unit,
    onToggleGlobal: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(R.string.search_query_placeholder), style = KomrdTheme.typography.body2) },
        singleLine = true,
    )
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.search_all_servers_label), style = KomrdTheme.typography.body3)
        Switch(checked = state.globalAllServers, onCheckedChange = onToggleGlobal)
    }
    if (!state.globalAllServers && state.libraries.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        LibraryFilterDropdown(
            libraries = state.libraries,
            selectedLibraryId = state.selectedLibraryId,
            onSelect = onSelectLibrary,
        )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TabButton(
            label = stringResource(R.string.search_tab_series),
            selected = state.selectedTab == SearchTab.SERIES,
        ) { onTabSelected(SearchTab.SERIES) }
        TabButton(
            label = stringResource(R.string.search_tab_books),
            selected = state.selectedTab == SearchTab.BOOKS,
        ) { onTabSelected(SearchTab.BOOKS) }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SearchResults(
    state: SearchUiState,
    series: androidx.paging.compose.LazyPagingItems<dev.komrd.core.model.Series>,
    books: androidx.paging.compose.LazyPagingItems<dev.komrd.core.model.Book>,
    imageLoaderFor: (Server) -> coil3.ImageLoader,
) {
    if (state.query.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.search_empty_prompt), style = KomrdTheme.typography.body2)
        }
        return
    }
    val activeServer = state.activeServer ?: return
    val imageLoader = imageLoaderFor(activeServer)
    when (state.selectedTab) {
        SearchTab.SERIES ->
            ThumbnailGrid(
                items = series,
                imageLoader = imageLoader,
                keyOf = { it.id },
                thumbnailUrlOf = { it.thumbnailUrl },
                labelOf = { it.name },
                onClick = { /* 検索結果からシリーズ遷移は今後拡張 */ },
                emptyMessage = stringResource(R.string.search_empty_series),
            )
        SearchTab.BOOKS ->
            ThumbnailGrid(
                items = books,
                imageLoader = imageLoader,
                keyOf = { it.id },
                thumbnailUrlOf = { it.thumbnailUrl },
                labelOf = { it.name },
                onClick = { /* 検索結果からブック遷移は今後拡張 */ },
                emptyMessage = stringResource(R.string.search_empty_books),
            )
    }
}

@Composable
private fun TabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val variant = if (selected) ButtonVariant.Primary else ButtonVariant.Ghost
    Button(text = label, variant = variant, onClick = onClick)
}

@Composable
private fun DropdownField(
    label: String,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(
        onClick = onExpand,
        modifier = modifier.fillMaxWidth(),
        content = {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(label, style = KomrdTheme.typography.body1)
                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
            }
        },
    )
}

@Composable
private fun LibraryFilterDropdown(
    libraries: List<Library>,
    selectedLibraryId: String?,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel =
        libraries.find { it.id == selectedLibraryId }?.name
            ?: stringResource(R.string.search_filter_all_libraries)
    DropdownField(label = selectedLabel, onExpand = { expanded = true })
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.search_filter_all_libraries)) },
            onClick = {
                expanded = false
                onSelect(null)
            },
        )
        libraries.forEach { lib ->
            DropdownMenuItem(
                text = { Text(lib.name) },
                onClick = {
                    expanded = false
                    onSelect(lib.id)
                },
            )
        }
    }
}

@Composable
private fun ServerSelector(
    activeServerName: String?,
    servers: List<Server>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    DropdownField(
        label = activeServerName ?: stringResource(R.string.search_select_server),
        onExpand = { expanded = true },
    )
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        servers.forEach { server ->
            DropdownMenuItem(
                text = { Text(server.name) },
                onClick = {
                    expanded = false
                    onSelect(server.id)
                },
            )
        }
    }
}
