package dev.komrd.core.cache

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.komrd.core.model.BookPageImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException

class PrefetchFetcherFactory(
    private val callFactory: Call.Factory,
    private val store: PrefetchStore,
) : Fetcher.Factory<BookPageImage> {
    override fun create(
        data: BookPageImage,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = PrefetchFetcher(data, callFactory, store)
}

internal class PrefetchFetcher(
    private val data: BookPageImage,
    private val callFactory: Call.Factory,
    private val store: PrefetchStore,
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val variant = data.variant
        val resourcePath = data.pageNumber.toString()
        val cached =
            withContext(Dispatchers.IO) {
                store.get(data.serverId, data.bookId, resourcePath, variant)
            }
        if (cached != null) {
            val mime = withContext(Dispatchers.IO) { sniffFileMime(cached.file) }
            return SourceFetchResult(
                source = ImageSource(cached.file.toOkioPath(), FileSystem.SYSTEM),
                mimeType = mime,
                dataSource = DataSource.DISK,
            )
        }
        val bytes = fetchFromNetwork()
        val entry =
            withContext(Dispatchers.IO) {
                store.put(
                    serverId = data.serverId,
                    bookId = data.bookId,
                    resourcePath = resourcePath,
                    resourceKind = PrefetchStore.RESOURCE_KIND_PAGE,
                    variant = variant,
                    bytes = bytes,
                    etag = null,
                )
            }
        return SourceFetchResult(
            source = ImageSource(entry.file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = sniffImageMime(bytes),
            dataSource = DataSource.NETWORK,
        )
    }

    private suspend fun fetchFromNetwork(): ByteArray =
        withContext(Dispatchers.IO) {
            val response = callFactory.newCall(Request.Builder().url(data.url).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw IOException("Page fetch failed: ${it.code}")
                }
                it.body?.bytes() ?: throw IOException("Empty page body")
            }
        }
}

class PrefetchKeyer : Keyer<BookPageImage> {
    override fun key(
        data: BookPageImage,
        options: Options,
    ): String = "prefetch:${data.serverId}:${data.bookId}:${data.pageNumber}:${data.variant}"
}

/** 画像magic bytesからMIMEを推定(不明はnull・Coil Decoderへ推譲)。 */
internal fun sniffImageMime(bytes: ByteArray): String? =
    when {
        bytes.size < 4 -> null
        isJpeg(bytes) -> "image/jpeg"
        isPng(bytes) -> "image/png"
        isWebp(bytes) -> "image/webp"
        isGif(bytes) -> "image/gif"
        else -> null
    }

private fun isJpeg(bytes: ByteArray): Boolean = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte()

private fun isPng(bytes: ByteArray): Boolean =
    bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()

private fun isWebp(bytes: ByteArray): Boolean =
    bytes.size >= 12 &&
        bytes[0] == 'R'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == 'F'.code.toByte() &&
        bytes[8] == 'W'.code.toByte() &&
        bytes[9] == 'E'.code.toByte() &&
        bytes[10] == 'B'.code.toByte() &&
        bytes[11] == 'P'.code.toByte()

private fun isGif(bytes: ByteArray): Boolean =
    bytes[0] == 'G'.code.toByte() &&
        bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte() &&
        bytes[3] == '8'.code.toByte()

/** ファイル先頭16バイトを読んでMIMEを推定(全文読込回避)。 */
internal fun sniffFileMime(file: File): String? {
    file.inputStream().use { input ->
        val head = ByteArray(16)
        val read = input.read(head)
        if (read <= 0) return null
        return sniffImageMime(if (read < 16) head.copyOf(read) else head)
    }
}
