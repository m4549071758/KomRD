package dev.komrd.feature.readerepub

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EpubReaderRoute(
    serverId: String,
    bookId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    readListId: String? = null,
    viewModel: EpubReaderViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId, bookId, readListId) { viewModel.bind(serverId, bookId, readListId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val ready = state as? EpubReaderUiState.Ready
    val server = ready?.let { viewModel.serverFor() }
    val callFactory = server?.let { viewModel.callFactoryFor(it) }

    EpubReaderScreen(
        state = state,
        server = server,
        callFactory = callFactory,
        bookId = bookId,
        onBack = onBack,
        onRetry = viewModel::retry,
        onPrevChapter = viewModel::prevChapter,
        onNextChapter = viewModel::nextChapter,
        onSelectChapter = viewModel::setCurrentChapter,
        onScrollProgression = viewModel::onScrollProgression,
        modifier = modifier,
    )
}
