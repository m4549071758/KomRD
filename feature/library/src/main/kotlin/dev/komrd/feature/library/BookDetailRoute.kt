package dev.komrd.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.HorizontalDivider
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.progressindicators.CircularProgressIndicator
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.BookOverview
import dev.komrd.core.model.isEpub

@Composable
fun BookDetailRoute(
    serverId: String,
    bookId: String,
    onBack: () -> Unit,
    onRead: (serverId: String, bookId: String, isEpub: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId, bookId) { viewModel.bind(serverId, bookId) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.book_detail_back_content_description),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            when (val s = state) {
                is BookDetailUiState.Loading -> CircularProgressIndicator()
                is BookDetailUiState.Error -> Text(s.message, style = KomrdTheme.typography.body2)
                is BookDetailUiState.Success ->
                    BookDetailContent(
                        overview = s.overview,
                        imageLoader = viewModel.imageLoaderFor(s.server),
                        onRead = { onRead(serverId, bookId, s.overview.mediaProfile.isEpub) },
                    )
            }
        }
    }
}

private val ThumbnailWidth = 140.dp
private const val THUMBNAIL_ASPECT = 0.7f

@Composable
private fun BookDetailContent(
    overview: BookOverview,
    imageLoader: coil3.ImageLoader,
    onRead: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
    ) {
        BookDetailHeader(overview = overview, imageLoader = imageLoader)

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            text = stringResource(R.string.book_detail_read_button),
            variant = ButtonVariant.Primary,
            onClick = onRead,
            modifier = Modifier.fillMaxWidth(),
        )

        val mediaType = overview.mediaType
        if (mediaType != null) {
            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))
            MetadataRow(label = stringResource(R.string.book_detail_format_label), value = formatMediaType(mediaType))
        }
    }
}

@Composable
private fun BookDetailHeader(
    overview: BookOverview,
    imageLoader: coil3.ImageLoader,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        AsyncImage(
            model = overview.thumbnailUrl,
            contentDescription = overview.name,
            imageLoader = imageLoader,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .width(ThumbnailWidth)
                    .aspectRatio(THUMBNAIL_ASPECT)
                    .clip(RoundedCornerShape(8.dp)),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val seriesName = overview.seriesName
            if (seriesName != null) {
                Text(
                    text = seriesName,
                    style = KomrdTheme.typography.body3,
                    color = KomrdTheme.colors.textSecondary,
                )
            }
            Text(
                text = overview.name,
                style = KomrdTheme.typography.h2,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${overview.pagesCount}${stringResource(R.string.book_detail_pages_suffix)}",
                style = KomrdTheme.typography.body2,
                color = KomrdTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun MetadataRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = KomrdTheme.typography.body2, color = KomrdTheme.colors.textSecondary)
        Text(text = value, style = KomrdTheme.typography.body2)
    }
}

private fun formatMediaType(mediaType: String): String =
    when {
        mediaType.contains("zip", ignoreCase = true) -> "CBZ"
        mediaType.contains("rar", ignoreCase = true) -> "CBR"
        mediaType.contains("pdf", ignoreCase = true) -> "PDF"
        mediaType.contains("epub", ignoreCase = true) -> "EPUB"
        else -> mediaType
    }
