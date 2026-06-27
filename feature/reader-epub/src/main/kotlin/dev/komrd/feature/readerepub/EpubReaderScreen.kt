package dev.komrd.feature.readerepub

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.Server
import dev.komrd.core.prefetch.PrefetchState
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response

@Suppress("LongParameterList") // Compose画面のstate+コールバック集約。既存パターンに倣う。
@Composable
fun EpubReaderScreen(
    state: EpubReaderUiState,
    server: Server?,
    callFactory: Call.Factory?,
    bookId: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onScrollProgression: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val title =
        (state as? EpubReaderUiState.Ready)
            ?.let { chapterTitle(it.manifest, it.currentChapterIndex) }
            ?: "EPUB"
    Scaffold(
        modifier = modifier,
        topBar = { EpubReaderTopBar(title = title, onBack = onBack) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            EpubReaderContent(
                state = state,
                server = server,
                callFactory = callFactory,
                bookId = bookId,
                onRetry = onRetry,
                onPrevChapter = onPrevChapter,
                onNextChapter = onNextChapter,
                onSelectChapter = onSelectChapter,
                onScrollProgression = onScrollProgression,
            )
        }
    }
}

@Composable
private fun EpubReaderTopBar(
    title: String,
    onBack: () -> Unit,
) {
    TopBar {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_epub_nav_back),
                )
            }
            Text(
                text = title,
                style = KomrdTheme.typography.body2,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Suppress("LongParameterList") // state分岐でコールバック集約。既存パターンに倣う。
@Composable
private fun EpubReaderContent(
    state: EpubReaderUiState,
    server: Server?,
    callFactory: Call.Factory?,
    bookId: String,
    onRetry: () -> Unit,
    onPrevChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSelectChapter: (Int) -> Unit,
    onScrollProgression: (Float) -> Unit,
) {
    when (state) {
        EpubReaderUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.reader_epub_loading), style = KomrdTheme.typography.body2)
            }

        is EpubReaderUiState.Error ->
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(state.message, style = KomrdTheme.typography.body2)
                androidx.compose.foundation.layout
                    .Spacer(Modifier.padding(16.dp))
                dev.komrd.core.designsystem.components.Button(
                    text = stringResource(R.string.reader_epub_retry),
                    variant = dev.komrd.core.designsystem.components.ButtonVariant.Ghost,
                    onClick = onRetry,
                )
            }

        is EpubReaderUiState.Ready -> {
            val ready = state
            if (server != null && callFactory != null) {
                EpubChapterWebView(
                    server = server,
                    bookId = bookId,
                    manifest = ready.manifest,
                    chapterIndex = ready.currentChapterIndex,
                    callFactory = callFactory,
                    onScrollProgression = onScrollProgression,
                    modifier = Modifier.fillMaxSize(),
                )
                EpubChapterNavOverlay(
                    manifest = ready.manifest,
                    chapterIndex = ready.currentChapterIndex,
                    onPrev = onPrevChapter,
                    onNext = onNextChapter,
                    onSelect = onSelectChapter,
                    prefetchState = ready.prefetchState,
                )
            }
        }
    }
}

@Composable
private fun EpubChapterWebView(
    server: Server,
    bookId: String,
    manifest: EpubManifest,
    chapterIndex: Int,
    callFactory: Call.Factory,
    onScrollProgression: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val chapter = manifest.readingOrder.getOrNull(chapterIndex) ?: return
    val chapterUrl = remember(server, bookId, chapter.href) { chapterResourceUrl(server, bookId, chapter.href) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            ScrollTrackingWebView(ctx, onScrollProgression).apply {
                layoutParams =
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                settings.javaScriptEnabled = false
                settings.allowFileAccess = false
                settings.builtInZoomControls = true
                settings.displayZoomControls = false
                webViewClient = KomgaResourceWebViewClient(server, bookId, callFactory)
                loadUrl(chapterUrl)
            }
        },
        update = { webView ->
            if (webView.url != chapterUrl) {
                webView.loadUrl(chapterUrl)
            }
        },
    )
}

/** 章リソースURL: `{baseUrl}api/v1/books/{bookId}/resource/{href}`。href先頭`/`は除去。 */
private fun chapterResourceUrl(
    server: Server,
    bookId: String,
    href: String,
): String {
    val base = if (server.baseUrl.endsWith("/")) server.baseUrl else "${server.baseUrl}/"
    val cleanHref = href.removePrefix("/")
    return "${base}api/v1/books/$bookId/resource/$cleanHref"
}

/**
 * `shouldInterceptRequest`でKomgaリソースprefixへの全リクエストを横取りし、サーバ別OkHttp
 * (認証/TLS共有)で取得→[WebResourceResponse]へ詰める。本フレーム(XHTML)・画像/CSS/フォント含む。
 * prefix外(data:・about:)はnullを返しWebView既定へ委譲。
 */
private class KomgaResourceWebViewClient(
    private val server: Server,
    private val bookId: String,
    private val callFactory: Call.Factory,
) : WebViewClient() {
    private val resourcePrefix: String =
        run {
            val base = if (server.baseUrl.endsWith("/")) server.baseUrl else "${server.baseUrl}/"
            "${base}api/v1/books/$bookId/resource/"
        }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val url = request?.url
        if (url == null || !url.toString().startsWith(resourcePrefix)) return null
        return runCatching { fetch(url) }.getOrNull()
    }

    private fun fetch(uri: Uri): WebResourceResponse {
        val request = Request.Builder().url(uri.toString()).build()
        val response: Response = callFactory.newCall(request).execute()
        if (!response.isSuccessful) {
            response.close()
            return WebResourceResponse("text/plain", "utf-8", "".byteInputStream())
        }
        val body = response.body
        val (mime, charset) = parseContentType(body.contentType()?.toString())
        return WebResourceResponse(mime, charset, body.byteStream())
    }

    private fun parseContentType(contentType: String?): Pair<String, String> {
        if (contentType.isNullOrBlank()) return "application/octet-stream" to "utf-8"
        val parts = contentType.split(";").map { it.trim() }
        val mime = parts.firstOrNull()?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
        val charset =
            parts
                .firstNotNullOfOrNull { p -> p.takeIf { it.startsWith("charset=") }?.substringAfter("charset=") }
                ?: "utf-8"
        return mime to charset
    }
}

/**
 * スクロール進捗(章内0..1)を`onScrollChanged`で算出し[onScrollProgression]へ通知するカスタムWebView。
 * `evaluateJavascript`を使わずネイティブ経路で取得(CSP script-src noneでも影響なし)。
 */
private class ScrollTrackingWebView(
    context: android.content.Context,
    private val onProgress: (Float) -> Unit,
) : WebView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onScrollChanged(
        l: Int,
        t: Int,
        oldl: Int,
        oldt: Int,
    ) {
        super.onScrollChanged(l, t, oldl, oldt)
        val extent = computeVerticalScrollExtent()
        val range = computeVerticalScrollRange()
        if (range > extent && extent > 0) {
            val progression = (t.toFloat() / (range - extent)).coerceIn(0f, 1f)
            onProgress(progression)
        }
    }
}

/** 章タイトル(manifest.readingOrderのtitle優先、無ければtoc、無ければhref)。 */
private fun chapterTitle(
    manifest: EpubManifest,
    chapterIndex: Int,
): String {
    val chapter = manifest.readingOrder.getOrNull(chapterIndex) ?: return "EPUB"
    return chapter.title ?: manifest.toc.firstOrNull { it.href == chapter.href }?.title ?: chapter.href
}

@Composable
private fun EpubChapterNavOverlay(
    manifest: EpubManifest,
    chapterIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onSelect: (Int) -> Unit,
    prefetchState: PrefetchState,
) {
    val chapterCount = manifest.readingOrder.size.coerceAtLeast(1)
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = onPrev, enabled = chapterIndex > 0) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.reader_epub_chapter_previous),
                )
            }
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                dev.komrd.core.designsystem.components.Slider(
                    value = chapterIndex.toFloat(),
                    onValueChange = { onSelect(it.toInt()) },
                    valueRange = 0f..(chapterCount - 1).toFloat(),
                )
                Text(
                    text = "${chapterIndex + 1} / $chapterCount",
                    style = KomrdTheme.typography.label3,
                )
                if (prefetchState is PrefetchState.Running) {
                    Text(
                        text =
                            stringResource(
                                R.string.reader_epub_prefetch_progress,
                                prefetchState.total - prefetchState.remaining,
                                prefetchState.total,
                            ),
                        style = KomrdTheme.typography.label3,
                    )
                }
            }
            IconButton(onClick = onNext, enabled = chapterIndex < chapterCount - 1) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.reader_epub_chapter_next),
                )
            }
        }
    }
}
