package dev.komrd.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ReadListSummary
import dev.komrd.core.model.Series

@Composable
fun LibraryRoute(
    onAddServer: () -> Unit = {},
    onOpenSeries: (Series) -> Unit = {},
    onOpenCollection: (Collection) -> Unit = {},
    onOpenReadList: (ReadListSummary) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val series = viewModel.seriesPaging.collectAsLazyPagingItems()
    LibraryScreen(
        state = state,
        series = series,
        imageLoaderFor = viewModel::imageLoaderFor,
        onSelectLibrary = viewModel::onSelectLibrary,
        onSortChanged = viewModel::onSortChanged,
        onFilterChanged = viewModel::onFilterChanged,
        onRetry = viewModel::onRetry,
        onAddServer = onAddServer,
        onOpenSeries = onOpenSeries,
        onOpenCollection = onOpenCollection,
        onOpenReadList = onOpenReadList,
        modifier = modifier,
    )
}
