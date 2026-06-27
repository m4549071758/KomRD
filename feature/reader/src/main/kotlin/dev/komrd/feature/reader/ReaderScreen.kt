@file:Suppress("TooManyFunctions")

package dev.komrd.feature.reader

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.progressindicators.CircularProgressIndicator
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.BookPageImage
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.Server
import dev.komrd.core.model.Spread
import dev.komrd.core.model.SpreadMode
import dev.komrd.core.model.planSpreads
import dev.komrd.core.prefetch.PrefetchState
import kotlinx.coroutines.launch
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

@Suppress("LongParameterList") // Compose画面のstate+コールバック集約。既存パターンに倣う。
@Composable
fun ReaderScreen(
    state: ReaderUiState,
    spreadMode: SpreadMode,
    server: Server?,
    imageLoader: ImageLoader?,
    bookmarkedPages: Set<Int>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onCurrentPageChanged: (Int) -> Unit,
    onSetSpreadMode: (SpreadMode) -> Unit,
    onSetReadingDirection: (ReadingDirection) -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuVisible by remember { mutableStateOf(false) }
    val ready = state as? ReaderUiState.Ready
    Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            ReaderUiState.Loading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            is ReaderUiState.Error ->
                ErrorPane(error = state.error, onRetry = onRetry)

            is ReaderUiState.Ready -> {
                val serverNonNull = server
                val loaderNonNull = imageLoader
                if (serverNonNull != null && loaderNonNull != null) {
                    ReaderPager(
                        book = state.book,
                        readingDirection = state.readingDirection,
                        spreadMode = spreadMode,
                        currentPage = state.currentPage,
                        imageLoader = loaderNonNull,
                        menuVisible = menuVisible,
                        onCurrentPageChanged = onCurrentPageChanged,
                        onMenuToggle = { menuVisible = !menuVisible },
                    )
                }
            }
        }
        if (ready != null) {
            PrefetchIndicator(
                state = ready.prefetchState,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
            ReaderOverlayBars(
                visible = menuVisible,
                book = ready.book,
                currentPage = ready.currentPage,
                readingDirection = ready.readingDirection,
                spreadMode = spreadMode,
                prefetchState = ready.prefetchState,
                imageLoader = imageLoader,
                bookmarkedPages = bookmarkedPages,
                onBack = onBack,
                onCurrentPageChanged = onCurrentPageChanged,
                onSetSpreadMode = onSetSpreadMode,
                onSetReadingDirection = onSetReadingDirection,
                onToggleBookmark = onToggleBookmark,
            )
        }
    }
}

@Suppress("LongParameterList") // ページャ種別の分岐でstate+コールバックが集約。既存パターンに倣う。
@Composable
private fun ReaderPager(
    book: BookDetail,
    readingDirection: ReadingDirection,
    spreadMode: SpreadMode,
    currentPage: Int,
    imageLoader: ImageLoader,
    menuVisible: Boolean,
    onCurrentPageChanged: (Int) -> Unit,
    onMenuToggle: () -> Unit,
) {
    when (readingDirection) {
        ReadingDirection.LEFT_TO_RIGHT, ReadingDirection.RIGHT_TO_LEFT ->
            HorizontalSpreadPager(
                book = book,
                readingDirection = readingDirection,
                spreadMode = spreadMode,
                currentPage = currentPage,
                imageLoader = imageLoader,
                menuVisible = menuVisible,
                onCurrentPageChanged = onCurrentPageChanged,
                onMenuToggle = onMenuToggle,
            )

        ReadingDirection.VERTICAL ->
            VerticalReaderPager(
                book = book,
                currentPage = currentPage,
                imageLoader = imageLoader,
                onCurrentPageChanged = onCurrentPageChanged,
                onMenuToggle = onMenuToggle,
            )

        ReadingDirection.WEBTOON ->
            WebtoonReaderPager(
                book = book,
                currentPage = currentPage,
                imageLoader = imageLoader,
                onCurrentPageChanged = onCurrentPageChanged,
                onMenuToggle = onMenuToggle,
            )
    }
}

@Suppress("LongParameterList") // ページャのstate+コールバック集約。既存パターンに倣う。
@Composable
private fun HorizontalSpreadPager(
    book: BookDetail,
    readingDirection: ReadingDirection,
    spreadMode: SpreadMode,
    currentPage: Int,
    imageLoader: ImageLoader,
    menuVisible: Boolean,
    onCurrentPageChanged: (Int) -> Unit,
    onMenuToggle: () -> Unit,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val spreadActive =
        spreadMode == SpreadMode.ALWAYS ||
            (spreadMode == SpreadMode.LANDSCAPE_ONLY && isLandscape)
    val spreads =
        remember(book.pages, readingDirection, spreadActive) {
            planSpreads(book.pages, readingDirection, spreadActive)
        }
    val pageCount = spreads.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pagerState, spreads) {
        snapshotFlow { pagerState.currentPage }.collect { spreadIndex ->
            val idx = spreadIndex.coerceIn(0, spreads.lastIndex)
            onCurrentPageChanged(spreads[idx].firstIndex)
        }
    }
    // 回転/スライダージャンプでcurrentPageが変わったら当該スプレッドへ再seek(位置保持)。
    // スワイプ由来の更新は同一スプレッド=seek不要(no-op)で安全。
    LaunchedEffect(spreads, currentPage) {
        val target = spreads.indexOfFirst { currentPage in it.pageIndices }.coerceAtLeast(0)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            reverseLayout = readingDirection.isRtl,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { spreadIndex ->
            SpreadView(
                spread = spreads[spreadIndex],
                pages = book.pages,
                readingDirection = readingDirection,
                serverId = book.serverId,
                bookId = book.id,
                mediaProfile = book.mediaProfile,
                imageLoader = imageLoader,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ReaderTapOverlay(
            onMenuToggle = onMenuToggle,
            // メニュー表示中は左右タップでページ移動しない(中央で閉じる)。
            onMove =
                if (menuVisible) {
                    null
                } else {
                    { delta ->
                        val target = (pagerState.currentPage + delta).coerceIn(0, spreads.lastIndex)
                        scope.launch { pagerState.scrollToPage(target) }
                    }
                },
            isRtl = readingDirection.isRtl,
        )
    }
}

/**
 * 1スプレッドの描画。単独は1枚、2ページは`Row`で並べる。ペア内順序は[Spread.pageIndices](読書順)のまま
 * 並べ、読書方向で`LayoutDirection`を切替(LTR=先頭が左 / RTL=先頭が右)。
 */
@Suppress("LongParameterList") // Composeのstate+画像取得引数集約。既存パターンに倣う。
@Composable
private fun SpreadView(
    spread: Spread,
    pages: List<BookPage>,
    readingDirection: ReadingDirection,
    serverId: String,
    bookId: String,
    mediaProfile: BookMediaProfile,
    imageLoader: ImageLoader,
    modifier: Modifier = Modifier,
) {
    if (spread.isSingle) {
        val page = pages[spread.firstIndex]
        ReaderPage(
            image =
                BookPageImage(
                    serverId = serverId,
                    bookId = bookId,
                    pageNumber = page.number,
                    url = pageImageUrl(page, mediaProfile),
                    variant = pageImageVariant(mediaProfile),
                ),
            imageLoader = imageLoader,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize(),
        )
        return
    }
    val layoutDirection = if (readingDirection.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        Row(modifier = modifier.fillMaxSize()) {
            spread.pageIndices.forEach { pageIndex ->
                val page = pages[pageIndex]
                ReaderPage(
                    image =
                        BookPageImage(
                            serverId = serverId,
                            bookId = bookId,
                            pageNumber = page.number,
                            url = pageImageUrl(page, mediaProfile),
                            variant = pageImageVariant(mediaProfile),
                        ),
                    imageLoader = imageLoader,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.weight(1f).fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun VerticalReaderPager(
    book: BookDetail,
    currentPage: Int,
    imageLoader: ImageLoader,
    onCurrentPageChanged: (Int) -> Unit,
    onMenuToggle: () -> Unit,
) {
    val pageCount = book.pagesCount.coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pageCount })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { onCurrentPageChanged(it) }
    }
    LaunchedEffect(currentPage) {
        if (pagerState.currentPage != currentPage) pagerState.scrollToPage(currentPage)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        VerticalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            val page = book.pages[pageIndex]
            ReaderPage(
                image =
                    BookPageImage(
                        serverId = book.serverId,
                        bookId = book.id,
                        pageNumber = page.number,
                        url = pageImageUrl(page, book.mediaProfile),
                        variant = pageImageVariant(book.mediaProfile),
                    ),
                imageLoader = imageLoader,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        ReaderTapOverlay(
            onMenuToggle = onMenuToggle,
            onMove = null,
            isRtl = false,
        )
    }
}

@Composable
private fun WebtoonReaderPager(
    book: BookDetail,
    currentPage: Int,
    imageLoader: ImageLoader,
    onCurrentPageChanged: (Int) -> Unit,
    onMenuToggle: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { onCurrentPageChanged(it) }
    }
    LaunchedEffect(currentPage) {
        if (listState.firstVisibleItemIndex != currentPage) listState.scrollToItem(currentPage)
    }
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(book.pagesCount, key = { index -> book.pages[index].number }) { index ->
                val page = book.pages[index]
                ReaderPage(
                    image =
                        BookPageImage(
                            serverId = book.serverId,
                            bookId = book.id,
                            pageNumber = page.number,
                            url = pageImageUrl(page, book.mediaProfile),
                            variant = pageImageVariant(book.mediaProfile),
                        ),
                    imageLoader = imageLoader,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        ReaderTapOverlay(
            onMenuToggle = onMenuToggle,
            onMove = null,
            isRtl = false,
        )
    }
}

private fun pageImageUrl(
    page: BookPage,
    mediaProfile: BookMediaProfile,
): String = if (mediaProfile == BookMediaProfile.PDF) "${page.url}?convert=jpeg" else page.url

private fun pageImageVariant(mediaProfile: BookMediaProfile): String =
    when (mediaProfile) {
        BookMediaProfile.PDF -> "jpeg"
        else -> "full"
    }

@Composable
private fun ReaderTapOverlay(
    onMenuToggle: () -> Unit,
    onMove: ((Int) -> Unit)?,
    isRtl: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val widthPx = constraints.maxWidth.toFloat()
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(onMove, isRtl) {
                        detectTapGestures(
                            onTap = { offset ->
                                val x = offset.x
                                when {
                                    x < widthPx / 3f -> onMove?.let { it(if (isRtl) 1 else -1) }
                                    x > 2f * widthPx / 3f -> onMove?.let { it(if (isRtl) -1 else 1) }
                                    else -> onMenuToggle()
                                }
                            },
                        )
                    },
        )
    }
}

@Composable
private fun ReaderPage(
    image: BookPageImage,
    imageLoader: ImageLoader,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    var retryKey by remember { mutableIntStateOf(0) }
    var painterState by remember {
        mutableStateOf<coil3.compose.AsyncImagePainter.State>(coil3.compose.AsyncImagePainter.State.Empty)
    }
    val zoomableState = rememberZoomableState()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        key(retryKey) {
            coil3.compose.AsyncImage(
                model = image,
                contentDescription = null,
                imageLoader = imageLoader,
                onState = { painterState = it },
                contentScale = contentScale,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zoomable(
                            state = zoomableState,
                            onDoubleClick = { _, _ -> },
                        ),
            )
        }
        when (painterState) {
            is coil3.compose.AsyncImagePainter.State.Loading ->
                CircularProgressIndicator()

            is coil3.compose.AsyncImagePainter.State.Error ->
                Button(
                    text = stringResource(R.string.reader_page_retry_button),
                    variant = ButtonVariant.Ghost,
                    onClick = { retryKey++ },
                )

            else -> Unit
        }
    }
}

@Composable
private fun ErrorPane(
    error: dev.komrd.core.common.error.KomgaError,
    onRetry: () -> Unit,
) {
    val message =
        when (error) {
            is dev.komrd.core.common.error.KomgaError.Unauthorized ->
                stringResource(R.string.reader_error_unauthorized)

            is dev.komrd.core.common.error.KomgaError.UntrustedCertificate ->
                stringResource(R.string.reader_error_untrusted_certificate)

            is dev.komrd.core.common.error.KomgaError.Network ->
                stringResource(R.string.reader_error_network)

            else -> stringResource(R.string.reader_error_generic)
        }
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, style = KomrdTheme.typography.body2)
        Button(
            text = stringResource(R.string.reader_error_retry_button),
            variant = ButtonVariant.Ghost,
            onClick = onRetry,
            modifier = Modifier.padding(top = 48.dp),
        )
    }
}

@Composable
private fun PrefetchIndicator(
    state: PrefetchState,
    modifier: Modifier = Modifier,
) {
    if (state is PrefetchState.Running) {
        Box(
            modifier =
                modifier
                    .background(
                        color = KomrdTheme.colors.surface.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(8.dp),
                    ).padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text =
                    stringResource(
                        R.string.reader_prefetch_progress,
                        state.total - state.remaining,
                        state.total,
                    ),
                style = KomrdTheme.typography.label3,
                color = KomrdTheme.colors.textSecondary,
            )
        }
    }
}
