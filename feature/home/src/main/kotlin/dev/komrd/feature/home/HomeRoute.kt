package dev.komrd.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import dev.komrd.core.model.Collection
import dev.komrd.core.model.ReadListSummary

@Composable
fun HomeRoute(
    onAddServer: () -> Unit,
    onOpenBook: (serverId: String, bookId: String) -> Unit,
    onOpenLibrary: (serverId: String, libraryId: String) -> Unit,
    onOpenCollection: (Collection) -> Unit,
    onOpenReadList: (ReadListSummary) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val inProgress = viewModel.inProgressPaging.collectAsLazyPagingItems()
    val read = viewModel.readPaging.collectAsLazyPagingItems()
    HomeScreen(
        state = state,
        inProgress = inProgress,
        read = read,
        imageLoaderFor = viewModel::imageLoaderFor,
        onSelectServer = viewModel::onSelectServer,
        onAddServer = onAddServer,
        onOpenBook = onOpenBook,
        onOpenLibrary = onOpenLibrary,
        onOpenCollection = onOpenCollection,
        onOpenReadList = onOpenReadList,
    )
}
