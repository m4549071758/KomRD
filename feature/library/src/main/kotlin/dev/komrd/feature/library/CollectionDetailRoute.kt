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
import dev.komrd.core.model.Series

@Composable
fun CollectionDetailRoute(
    serverId: String,
    collectionId: String,
    collectionName: String,
    onBack: () -> Unit,
    onOpenSeries: (Series) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CollectionDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId, collectionId) { viewModel.bind(serverId, collectionId) }
    val server by viewModel.server.collectAsStateWithLifecycle()
    val series = viewModel.seriesPaging.collectAsLazyPagingItems()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = collectionName.ifBlank { stringResource(R.string.collection_detail_default_title) },
                        style = KomrdTheme.typography.h3,
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            val resolvedServer = server
            if (resolvedServer != null) {
                SeriesGrid(
                    series = series,
                    imageLoader = viewModel.imageLoaderFor(resolvedServer),
                    onSeriesClick = onOpenSeries,
                )
            }
        }
    }
}
