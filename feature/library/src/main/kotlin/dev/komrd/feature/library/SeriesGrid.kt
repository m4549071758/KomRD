package dev.komrd.feature.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.paging.compose.LazyPagingItems
import coil3.ImageLoader
import dev.komrd.core.model.Series

/** Seriesのサムネ付きグリッド（[ThumbnailGrid]の薄いラッパ）。 */
@Composable
fun SeriesGrid(
    series: LazyPagingItems<Series>,
    imageLoader: ImageLoader,
    onSeriesClick: (Series) -> Unit,
    modifier: Modifier = Modifier,
) {
    ThumbnailGrid(
        items = series,
        imageLoader = imageLoader,
        keyOf = { it.id },
        thumbnailUrlOf = { it.thumbnailUrl },
        labelOf = { it.name },
        onClick = onSeriesClick,
        modifier = modifier,
        emptyMessage = "シリーズがありません",
    )
}
