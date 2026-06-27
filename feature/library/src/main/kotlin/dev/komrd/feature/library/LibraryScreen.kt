@file:Suppress("TooManyFunctions")

package dev.komrd.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import coil3.ImageLoader
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.AlertDialog
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.DrawerValue
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.ListItem
import dev.komrd.core.designsystem.components.ListItemPadding
import dev.komrd.core.designsystem.components.ModalDrawerSheet
import dev.komrd.core.designsystem.components.ModalNavigationDrawer
import dev.komrd.core.designsystem.components.RadioButton
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.progressindicators.CircularProgressIndicator
import dev.komrd.core.designsystem.components.rememberDrawerState
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.Series
import dev.komrd.core.model.SeriesSort
import dev.komrd.core.model.Server
import kotlinx.coroutines.launch

@Suppress("LongParameterList")
@Composable
fun LibraryScreen(
    state: LibraryUiState,
    series: LazyPagingItems<Series>,
    imageLoaderFor: (Server) -> ImageLoader,
    onSelectLibrary: (serverId: String, libraryId: String) -> Unit,
    onSortChanged: (SeriesSort) -> Unit,
    onFilterChanged: (ReadStatusFilter) -> Unit,
    onRetry: () -> Unit,
    onAddServer: () -> Unit,
    onOpenSeries: (Series) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenReadList: (ReadListSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var showSortDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    ModalNavigationDrawer(
        drawerState = drawerState,
        modifier = modifier,
        drawerContent = {
            ModalDrawerSheet {
                libraryDrawer(
                    state,
                    scope,
                    drawerState,
                    onSelectLibrary,
                    onOpenCollection,
                    onOpenReadList,
                    onAddServer,
                )
            }
        },
    ) {
        LibraryScaffoldContent(
            state = state,
            series = series,
            imageLoaderFor = imageLoaderFor,
            onRetry = onRetry,
            onAddServer = onAddServer,
            onOpenSeries = onOpenSeries,
            onMenuClick = { scope.launch { drawerState.open() } },
            onSortClick = { showSortDialog = true },
            onFilterClick = { showFilterDialog = true },
        )
    }
    LibraryDialogs(
        showSortDialog = showSortDialog,
        showFilterDialog = showFilterDialog,
        currentSort = state.currentSort,
        currentFilter = state.readStatusFilter,
        onSortSelect = {
            onSortChanged(it)
            showSortDialog = false
        },
        onFilterSelect = {
            onFilterChanged(it)
            showFilterDialog = false
        },
        onSortDismiss = { showSortDialog = false },
        onFilterDismiss = { showFilterDialog = false },
    )
}

@Composable
@Suppress("LongParameterList")
private fun libraryDrawer(
    state: LibraryUiState,
    scope: kotlinx.coroutines.CoroutineScope,
    drawerState: dev.komrd.core.designsystem.components.DrawerState,
    onSelectLibrary: (serverId: String, libraryId: String) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenReadList: (ReadListSummary) -> Unit,
    onAddServer: () -> Unit,
) {
    LibraryBrowserDrawer(
        state = state,
        onSelectLibrary = { serverId, libraryId ->
            onSelectLibrary(serverId, libraryId)
            scope.launch { drawerState.close() }
        },
        onOpenCollection = { collection ->
            scope.launch { drawerState.close() }
            onOpenCollection(collection)
        },
        onOpenReadList = { readList ->
            scope.launch { drawerState.close() }
            onOpenReadList(readList)
        },
        onAddServer = {
            scope.launch { drawerState.close() }
            onAddServer()
        },
        onSelectServer = null,
    )
}

@Composable
@Suppress("LongParameterList")
private fun LibraryDialogs(
    showSortDialog: Boolean,
    showFilterDialog: Boolean,
    currentSort: SeriesSort,
    currentFilter: ReadStatusFilter,
    onSortSelect: (SeriesSort) -> Unit,
    onFilterSelect: (ReadStatusFilter) -> Unit,
    onSortDismiss: () -> Unit,
    onFilterDismiss: () -> Unit,
) {
    if (showSortDialog) {
        SortSelectionDialog(current = currentSort, onDismiss = onSortDismiss, onSelect = onSortSelect)
    }
    if (showFilterDialog) {
        FilterSelectionDialog(current = currentFilter, onDismiss = onFilterDismiss, onSelect = onFilterSelect)
    }
}

@Composable
@Suppress("LongParameterList")
private fun LibraryScaffoldContent(
    state: LibraryUiState,
    series: LazyPagingItems<Series>,
    imageLoaderFor: (Server) -> ImageLoader,
    onRetry: () -> Unit,
    onAddServer: () -> Unit,
    onOpenSeries: (Series) -> Unit,
    onMenuClick: () -> Unit,
    onSortClick: () -> Unit,
    onFilterClick: () -> Unit,
) {
    Scaffold(
        topBar = { LibraryTopBar(state, onMenuClick, onSortClick, onFilterClick) },
    ) { padding ->
        LibraryBody(
            state = state,
            series = series,
            imageLoaderFor = imageLoaderFor,
            onRetry = onRetry,
            onAddServer = onAddServer,
            onOpenSeries = onOpenSeries,
            padding = padding,
        )
    }
}

@Composable
@Suppress("LongParameterList")
private fun LibraryTopBar(
    state: LibraryUiState,
    onMenuClick: () -> Unit,
    onSortClick: () -> Unit,
    onFilterClick: () -> Unit,
) {
    TopBar {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenuClick) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.home_menu_content_description),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = state.selectedLibrary?.name ?: stringResource(R.string.library_title),
                style = KomrdTheme.typography.h3,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onSortClick) {
                Text(
                    stringResource(R.string.library_sort_button),
                    style = KomrdTheme.typography.label2,
                )
            }
            IconButton(onClick = onFilterClick) {
                Text(
                    stringResource(R.string.library_filter_button),
                    style = KomrdTheme.typography.label2,
                )
            }
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun LibraryBody(
    state: LibraryUiState,
    series: LazyPagingItems<Series>,
    imageLoaderFor: (Server) -> ImageLoader,
    onRetry: () -> Unit,
    onAddServer: () -> Unit,
    onOpenSeries: (Series) -> Unit,
    padding: PaddingValues,
) {
    Box(modifier = Modifier.fillMaxSize().padding(padding)) {
        when {
            state.loading -> Centered { CircularProgressIndicator() }
            state.noServer -> NoServerMessage(onAddServer = onAddServer)
            state.selectedServerError != null ->
                Centered {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.library_load_error_message),
                            style = KomrdTheme.typography.body2,
                        )
                        Button(
                            text = stringResource(R.string.retry_button),
                            variant = ButtonVariant.Ghost,
                            onClick = onRetry,
                        )
                    }
                }
            state.selectedLibrary == null ->
                Centered { Text(stringResource(R.string.library_empty_message)) }
            state.selectedServer != null ->
                SeriesGrid(
                    series = series,
                    imageLoader = imageLoaderFor(state.selectedServer),
                    onSeriesClick = onOpenSeries,
                )
        }
    }
}

@Composable
private fun SortSelectionDialog(
    current: SeriesSort,
    onDismiss: () -> Unit,
    onSelect: (SeriesSort) -> Unit,
) {
    SelectionDialog(
        title = stringResource(R.string.library_sort_dialog_title),
        options = SeriesSort.entries.toList(),
        current = current,
        labelOf = { seriesSortLabel(it) },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun FilterSelectionDialog(
    current: ReadStatusFilter,
    onDismiss: () -> Unit,
    onSelect: (ReadStatusFilter) -> Unit,
) {
    SelectionDialog(
        title = stringResource(R.string.library_filter_dialog_title),
        options = ReadStatusFilter.entries.toList(),
        current = current,
        labelOf = { readStatusFilterLabel(it) },
        onDismiss = onDismiss,
        onSelect = onSelect,
    )
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    options: List<T>,
    current: T,
    labelOf: @Composable (T) -> String,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { option ->
                    ListItem(
                        headlineContent = { Text(labelOf(option)) },
                        leadingContent = {
                            RadioButton(
                                selected = option == current,
                                onClick = null,
                            )
                        },
                        modifier = Modifier.clickable { onSelect(option) },
                        contentPadding = ListItemPadding(horizontal = 0.dp, vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {},
    )
}

@Composable
internal fun seriesSortLabel(sort: SeriesSort): String =
    stringResource(
        when (sort) {
            SeriesSort.TITLE_ASC -> R.string.series_sort_title_asc
            SeriesSort.TITLE_DESC -> R.string.series_sort_title_desc
            SeriesSort.DATE_ADDED_ASC -> R.string.series_sort_date_added_asc
            SeriesSort.DATE_ADDED_DESC -> R.string.series_sort_date_added_desc
            SeriesSort.DATE_UPDATED_ASC -> R.string.series_sort_date_updated_asc
            SeriesSort.DATE_UPDATED_DESC -> R.string.series_sort_date_updated_desc
        },
    )

@Composable
internal fun readStatusFilterLabel(filter: ReadStatusFilter): String =
    stringResource(
        when (filter) {
            ReadStatusFilter.ALL -> R.string.read_status_all
            ReadStatusFilter.UNREAD -> R.string.read_status_unread
            ReadStatusFilter.IN_PROGRESS -> R.string.read_status_in_progress
            ReadStatusFilter.READ -> R.string.read_status_read
        },
    )

@Composable
private fun NoServerMessage(onAddServer: () -> Unit) {
    Centered {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.no_server_message),
                style = KomrdTheme.typography.h3,
            )
            Button(
                text = stringResource(R.string.add_server_button),
                variant = ButtonVariant.Primary,
                onClick = onAddServer,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
