package dev.komrd.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ReaderRoute(
    serverId: String,
    bookId: String,
    readListId: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId, bookId, readListId) { viewModel.bind(serverId, bookId, readListId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val spreadMode by viewModel.spreadMode.collectAsStateWithLifecycle()
    val bookmarkedPages by viewModel.bookmarkedPages.collectAsStateWithLifecycle()
    val ready = state as? ReaderUiState.Ready
    val imageLoader = ready?.let { viewModel.imageLoaderFor(it.server) }

    ReaderScreen(
        state = state,
        spreadMode = spreadMode,
        server = ready?.server,
        imageLoader = imageLoader,
        bookmarkedPages = bookmarkedPages,
        onBack = onBack,
        onRetry = viewModel::retry,
        onCurrentPageChanged = viewModel::setCurrentPage,
        onSetSpreadMode = viewModel::setSpreadMode,
        onSetReadingDirection = viewModel::setReadingDirection,
        onToggleBookmark = viewModel::toggleBookmark,
        modifier = modifier,
    )
}
