@file:Suppress("TooManyFunctions")

package dev.komrd.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.AlertDialog
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.Slider
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.designsystem.components.topbar.TopBarDefaults
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookPageThumbnail
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.SpreadMode
import dev.komrd.core.prefetch.PrefetchState

@Suppress("LongParameterList") // Compose画面のstate+コールバック集約。既存パターンに倣う。
@Composable
fun BoxScope.ReaderOverlayBars(
    visible: Boolean,
    book: BookDetail,
    currentPage: Int,
    readingDirection: ReadingDirection,
    spreadMode: SpreadMode,
    prefetchState: dev.komrd.core.prefetch.PrefetchState,
    imageLoader: ImageLoader?,
    bookmarkedPages: Set<Int>,
    onBack: () -> Unit,
    onCurrentPageChanged: (Int) -> Unit,
    onSetSpreadMode: (SpreadMode) -> Unit,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onToggleBookmark: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
    ) {
        ReaderTopBar(title = book.name, onBack = onBack)
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
    ) {
        ReaderBottomBar(
            book = book,
            currentPage = currentPage,
            readingDirection = readingDirection,
            spreadMode = spreadMode,
            prefetchState = prefetchState,
            imageLoader = imageLoader,
            bookmarkedPages = bookmarkedPages,
            onCurrentPageChanged = onCurrentPageChanged,
            onSetSpreadMode = onSetSpreadMode,
            onSetReadingDirection = onSetReadingDirection,
            onToggleBookmark = onToggleBookmark,
        )
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopBar(
        colors =
            TopBarDefaults.topBarColors(
                containerColor = KomrdTheme.colors.surface.copy(alpha = 0.95f),
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_navigate_back_content_description),
                )
            }
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = KomrdTheme.typography.h3,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("LongParameterList", "LongMethod") // Compose下部バーのstate+コールバック集約+しおりUI統合。
@Composable
private fun ReaderBottomBar(
    book: BookDetail,
    currentPage: Int,
    readingDirection: ReadingDirection,
    spreadMode: SpreadMode,
    prefetchState: dev.komrd.core.prefetch.PrefetchState,
    imageLoader: ImageLoader?,
    bookmarkedPages: Set<Int>,
    onCurrentPageChanged: (Int) -> Unit,
    onSetSpreadMode: (SpreadMode) -> Unit,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onToggleBookmark: () -> Unit,
) {
    var spreadDialog by remember { mutableStateOf(false) }
    var directionDialog by remember { mutableStateOf(false) }
    var bookmarkListDialog by remember { mutableStateOf(false) }
    // 現在ページ(0-based index)→Komga pageNumber(1-based)でしおり有無を判定
    val currentPageNumber = book.pages.getOrNull(currentPage)?.number ?: (currentPage + 1)
    val isBookmarked = currentPageNumber in bookmarkedPages
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(KomrdTheme.colors.surface.copy(alpha = 0.95f))
                .padding(8.dp),
    ) {
        ReaderSlider(
            book = book,
            currentPage = currentPage,
            imageLoader = imageLoader,
            bookmarkedPages = bookmarkedPages,
            onPageChanged = onCurrentPageChanged,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    text =
                        stringResource(
                            R.string.reader_spread_mode_button_format,
                            spreadModeLabel(spreadMode),
                        ),
                    variant = ButtonVariant.Ghost,
                    onClick = { spreadDialog = true },
                )
                Button(
                    text =
                        stringResource(
                            R.string.reader_reading_direction_button_format,
                            readingDirectionLabel(readingDirection),
                        ),
                    variant = ButtonVariant.Ghost,
                    onClick = { directionDialog = true },
                )
                Button(
                    text =
                        stringResource(
                            if (isBookmarked) {
                                R.string.reader_bookmark_remove_button
                            } else {
                                R.string.reader_bookmark_add_button
                            },
                        ),
                    variant = ButtonVariant.Ghost,
                    onClick = onToggleBookmark,
                )
                Button(
                    text = stringResource(R.string.reader_bookmark_list_button),
                    variant = ButtonVariant.Ghost,
                    onClick = { bookmarkListDialog = true },
                )
            }

            PrefetchStatusText(prefetchState)
        }
    }
    if (spreadDialog) {
        SpreadModeDialog(
            current = spreadMode,
            onDismiss = { spreadDialog = false },
            onSelect = { mode ->
                onSetSpreadMode(mode)
                spreadDialog = false
            },
        )
    }
    if (directionDialog) {
        ReadingDirectionDialog(
            current = readingDirection,
            onDismiss = { directionDialog = false },
            onSelect = { direction ->
                onSetReadingDirection(direction)
                directionDialog = false
            },
        )
    }
    if (bookmarkListDialog) {
        BookmarkListDialog(
            book = book,
            bookmarkedPages = bookmarkedPages,
            onDismiss = { bookmarkListDialog = false },
            onJumpToPage = { index ->
                onCurrentPageChanged(index)
                bookmarkListDialog = false
            },
        )
    }
}

@Composable
private fun PrefetchStatusText(prefetchState: PrefetchState) {
    val statusText =
        when (prefetchState) {
            is PrefetchState.Idle -> stringResource(R.string.reader_prefetch_status_idle)
            is PrefetchState.Running -> {
                val done = prefetchState.total - prefetchState.remaining
                "$done/${prefetchState.total}"
            }
        }
    Text(
        text = stringResource(R.string.reader_prefetch_status_format, statusText),
        style = KomrdTheme.typography.body3,
        color = KomrdTheme.colors.textSecondary,
        modifier = Modifier.padding(end = 8.dp),
    )
}

@Composable
private fun ReaderSlider(
    book: BookDetail,
    currentPage: Int,
    imageLoader: ImageLoader?,
    bookmarkedPages: Set<Int>,
    onPageChanged: (Int) -> Unit,
) {
    val pageCount = book.pagesCount
    val last = (pageCount - 1).coerceAtLeast(0)
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableIntStateOf(currentPage) }
    LaunchedEffect(currentPage) { if (!scrubbing) scrubValue = currentPage }
    val effective = (if (scrubbing) scrubValue else currentPage).coerceIn(0, last)
    // pageNumber(1-based) → 0-based index へ変換(存在しないページはスキップ)
    val bookmarkIndices =
        remember(book.pages, bookmarkedPages) {
            bookmarkedPages
                .mapNotNull { pageNumber ->
                    val idx = book.pages.indexOfFirst { it.number == pageNumber }
                    if (idx >= 0) idx else null
                }.toSet()
        }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        if (last > 0) {
            BookmarkMarkedSlider(
                value = effective.toFloat(),
                onValueChange = { value ->
                    scrubbing = true
                    scrubValue = value.toInt()
                },
                onValueChangeFinished = {
                    onPageChanged(scrubValue.coerceIn(0, last))
                    scrubbing = false
                },
                valueRange = 0f..last.toFloat(),
                bookmarkIndices = bookmarkIndices,
            )
        }
        Text(
            "${currentPage + 1} / $pageCount",
            modifier = Modifier.padding(vertical = 2.dp),
            style = KomrdTheme.typography.body3,
        )
        ThumbnailRow(
            book = book,
            currentPage = currentPage,
            scrubbing = scrubbing,
            imageLoader = imageLoader,
            onPageChanged = onPageChanged,
        )
    }
}

@Composable
private fun BookmarkMarkedSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    bookmarkIndices: Set<Int>,
    modifier: Modifier = Modifier,
) {
    val last = valueRange.endInclusive.toInt()
    val density = LocalDensity.current
    val markerColor = KomrdTheme.colors.primary
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
        // Track領域に重ねてマーカーを配置(スライダー中央縦位置に目印)。
        // thumbの遊び代は無視し、ratio * maxWidthで近似(視覚的な目安)。
        val widthPx = constraints.maxWidth.toFloat()
        bookmarkIndices.forEach { index ->
            if (last <= 0) return@forEach
            val ratio = index.toFloat() / last.toFloat()
            val xPx = ratio * widthPx
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = with(density) { xPx.toDp() })
                        .width(2.dp)
                        .size(8.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(markerColor),
            )
        }
    }
}

@Composable
private fun ThumbnailRow(
    book: BookDetail,
    currentPage: Int,
    scrubbing: Boolean,
    imageLoader: ImageLoader?,
    onPageChanged: (Int) -> Unit,
) {
    val pageCount = book.pagesCount
    val window = 7
    val half = window / 2
    val start = (currentPage - half).coerceAtLeast(0)
    val end = minOf(start + window, pageCount)
    val adjustedStart = (end - window).coerceAtLeast(0)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
    ) {
        for (index in adjustedStart until end) {
            val isCurrent = index == currentPage
            val page = book.pages.getOrNull(index)
            val pageNumber = page?.number ?: (index + 1)
            // page.url=`.../pages/{n}` なので `/thumbnail` 接尾で同エンドポイントURLを導出(認証/TLSはCoil側)。
            val thumbUrl = page?.url?.let { "$it/thumbnail" }
            val model =
                if (scrubbing) {
                    null
                } else {
                    BookPageThumbnail(book.serverId, book.id, pageNumber, thumbUrl, book.mediaProfile)
                }
            ThumbnailCell(
                model = model,
                pageNumber = pageNumber,
                isCurrent = isCurrent,
                imageLoader = imageLoader,
                onClick = { onPageChanged(index) },
            )
        }
    }
}

/**
 * サムネ1セル。命中(成功)時は縮小画像、それ以外(未取得/スクラブ中)はページ番号を表示。
 * [model]切替で[remember]し直し再取得(スクラブ解除/窓移動で再評価)。
 */
@Composable
private fun ThumbnailCell(
    model: BookPageThumbnail?,
    pageNumber: Int,
    isCurrent: Boolean,
    imageLoader: ImageLoader?,
    onClick: () -> Unit,
) {
    var state by remember(model) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val textColor = if (isCurrent) KomrdTheme.colors.primary else KomrdTheme.colors.onSurface
    Button(
        variant = ButtonVariant.PrimaryOutlined,
        onClick = onClick,
        modifier = Modifier.padding(vertical = 2.dp),
        content = {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (model != null && imageLoader != null) {
                    AsyncImage(
                        model = model,
                        contentDescription = null,
                        imageLoader = imageLoader,
                        onState = { state = it },
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(4.dp)),
                    )
                }
                if (state !is AsyncImagePainter.State.Success) {
                    Text("$pageNumber", color = textColor, style = KomrdTheme.typography.body3)
                }
            }
        },
    )
}

@Composable
private fun ReadingDirectionDialog(
    current: ReadingDirection,
    onDismiss: () -> Unit,
    onSelect: (ReadingDirection) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_reading_direction_dialog_title)) },
        text = {
            Column {
                ReadingDirection.entries.forEach { direction ->
                    val label = readingDirectionLabel(direction)
                    Button(
                        text =
                            if (direction == current) {
                                stringResource(R.string.reader_selected_item_format, label)
                            } else {
                                label
                            },
                        variant = ButtonVariant.PrimaryOutlined,
                        onClick = { onSelect(direction) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                text = stringResource(R.string.reader_dialog_close_button),
                variant = ButtonVariant.Ghost,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun SpreadModeDialog(
    current: SpreadMode,
    onDismiss: () -> Unit,
    onSelect: (SpreadMode) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_spread_mode_dialog_title)) },
        text = {
            Column {
                SpreadMode.entries.forEach { mode ->
                    val label = spreadModeLabel(mode)
                    Button(
                        text =
                            if (mode == current) {
                                stringResource(R.string.reader_selected_item_format, label)
                            } else {
                                label
                            },
                        variant = ButtonVariant.PrimaryOutlined,
                        onClick = { onSelect(mode) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                text = stringResource(R.string.reader_dialog_close_button),
                variant = ButtonVariant.Ghost,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun readingDirectionLabel(direction: ReadingDirection): String =
    when (direction) {
        ReadingDirection.LEFT_TO_RIGHT -> stringResource(R.string.reader_reading_direction_ltr)
        ReadingDirection.RIGHT_TO_LEFT -> stringResource(R.string.reader_reading_direction_rtl)
        ReadingDirection.VERTICAL -> stringResource(R.string.reader_reading_direction_vertical)
        ReadingDirection.WEBTOON -> stringResource(R.string.reader_reading_direction_webtoon)
    }

@Composable
private fun spreadModeLabel(mode: SpreadMode): String =
    when (mode) {
        SpreadMode.ALWAYS -> stringResource(R.string.reader_spread_mode_always)
        SpreadMode.LANDSCAPE_ONLY -> stringResource(R.string.reader_spread_mode_landscape_only)
        SpreadMode.OFF -> stringResource(R.string.reader_spread_mode_off)
    }

@Composable
private fun BookmarkListDialog(
    book: BookDetail,
    bookmarkedPages: Set<Int>,
    onDismiss: () -> Unit,
    onJumpToPage: (Int) -> Unit,
) {
    val entries =
        remember(book.pages, bookmarkedPages) {
            bookmarkedPages
                .mapNotNull { pageNumber ->
                    val index = book.pages.indexOfFirst { it.number == pageNumber }
                    if (index >= 0) index to pageNumber else null
                }.sortedBy { it.second }
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reader_bookmark_list_dialog_title)) },
        text = {
            if (entries.isEmpty()) {
                Text(
                    stringResource(R.string.reader_bookmark_list_empty),
                    style = KomrdTheme.typography.body2,
                )
            } else {
                LazyColumn {
                    items(entries, key = { it.second }) { (index, pageNumber) ->
                        Button(
                            text =
                                stringResource(
                                    R.string.reader_bookmark_jump_to_page_button_format,
                                    pageNumber,
                                ),
                            variant = ButtonVariant.PrimaryOutlined,
                            onClick = { onJumpToPage(index) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                text = stringResource(R.string.reader_dialog_close_button),
                variant = ButtonVariant.Ghost,
                onClick = onDismiss,
            )
        },
    )
}
