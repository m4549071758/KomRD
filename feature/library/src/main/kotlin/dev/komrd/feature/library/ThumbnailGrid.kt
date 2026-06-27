package dev.komrd.feature.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.itemKey
import coil3.ImageLoader
import coil3.compose.AsyncImage
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.Surface
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.progressindicators.CircularProgressIndicator
import dev.komrd.core.designsystem.components.progressindicators.LinearProgressIndicator
import kotlinx.coroutines.delay

/** Komga表紙の概ねの縦長比（width:height ≈ 0.7）。 */
private const val THUMBNAIL_ASPECT = 0.7f

@Suppress("LongParameterList")
@Composable
fun <T : Any> ThumbnailGrid(
    items: LazyPagingItems<T>,
    imageLoader: ImageLoader,
    keyOf: (T) -> Any,
    thumbnailUrlOf: (T) -> String,
    labelOf: (T) -> String,
    onClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = stringResource(R.string.thumbnail_grid_empty_message),
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> CenterBox(modifier) { CircularProgressIndicator() }
        refresh is LoadState.Error && items.itemCount == 0 ->
            CenterBox(modifier) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.thumbnail_grid_load_error), style = KomrdTheme.typography.body2)
                    Button(
                        text = stringResource(R.string.thumbnail_grid_retry_button),
                        variant = ButtonVariant.Ghost,
                        onClick = { items.retry() },
                    )
                }
            }
        items.itemCount == 0 -> CenterBox(modifier) { Text(emptyMessage) }
        else -> {
            val appendError = items.loadState.append as? LoadState.Error
            LaunchedEffect(appendError) {
                if (appendError != null) {
                    delay(3_000L)
                    items.retry()
                }
            }
            Box(modifier = modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(count = items.itemCount, key = items.itemKey(keyOf)) { index ->
                        items[index]?.let { item ->
                            ThumbnailCard(
                                label = labelOf(item),
                                thumbnailUrl = thumbnailUrlOf(item),
                                imageLoader = imageLoader,
                                onClick = { onClick(item) },
                            )
                        }
                    }
                }
                // 追加取得失敗または再試行中に上部バナーを表示する。
                if (appendError != null || items.loadState.append is LoadState.Loading) {
                    ReconnectingBanner(modifier = Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun ThumbnailCard(
    label: String,
    thumbnailUrl: String,
    imageLoader: ImageLoader,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.clickable(onClick = onClick).fillMaxWidth()) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 4.dp,
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = label,
                imageLoader = imageLoader,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(THUMBNAIL_ASPECT)
                        .clip(RoundedCornerShape(12.dp)),
            )
        }
        Text(
            text = label,
            style = KomrdTheme.typography.body3,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun ReconnectingBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = KomrdTheme.colors.secondary,
    ) {
        Column {
            Text(
                text = stringResource(R.string.thumbnail_grid_reconnecting),
                style = KomrdTheme.typography.label2,
                color = KomrdTheme.colors.onSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CenterBox(
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Preview(name = "ReconnectingBanner")
@Composable
private fun ReconnectingBannerPreview() {
    KomrdTheme { ReconnectingBanner() }
}

@Preview(name = "ThumbnailCard")
@Composable
private fun ThumbnailCardPreview() {
    KomrdTheme {
        ThumbnailCard(
            label = "ワンピース 1巻",
            thumbnailUrl = "",
            imageLoader = ImageLoader.Builder(androidx.compose.ui.platform.LocalContext.current).build(),
            onClick = {},
        )
    }
}
