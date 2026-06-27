package dev.komrd.feature.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.Book

@Composable
fun SeriesDetailRoute(
    serverId: String,
    seriesId: String,
    seriesName: String,
    onBack: () -> Unit,
    onOpenBook: (serverId: String, bookId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId, seriesId) { viewModel.bind(serverId, seriesId) }
    val server by viewModel.server.collectAsStateWithLifecycle()
    val books = viewModel.booksPaging.collectAsLazyPagingItems()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.series_detail_back_button_content_description),
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = seriesName.ifBlank { stringResource(R.string.series_detail_fallback_title) },
                        style = KomrdTheme.typography.h3,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val resolvedServer = server
            if (resolvedServer != null) {
                ThumbnailGrid(
                    items = books,
                    imageLoader = viewModel.imageLoaderFor(resolvedServer),
                    keyOf = { it.id },
                    thumbnailUrlOf = { it.thumbnailUrl },
                    labelOf = { it.name },
                    onClick = { book: Book -> onOpenBook(book.serverId, book.id) },
                    emptyMessage = stringResource(R.string.series_detail_empty_message),
                )
            }
        }
    }
}
