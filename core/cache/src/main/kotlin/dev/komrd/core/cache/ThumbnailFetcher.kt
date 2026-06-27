package dev.komrd.core.cache

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.BookPageThumbnail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Request
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.IOException

class ThumbnailFetcherFactory(
    private val store: ThumbnailStore,
    private val callFactory: Call.Factory? = null,
) : Fetcher.Factory<BookPageThumbnail> {
    override fun create(
        data: BookPageThumbnail,
        options: Options,
        imageLoader: ImageLoader,
    ): Fetcher = ThumbnailFetcher(data, store, callFactory)
}

internal class ThumbnailFetcher(
    private val data: BookPageThumbnail,
    private val store: ThumbnailStore,
    private val callFactory: Call.Factory?,
) : Fetcher {
    override suspend fun fetch(): FetchResult? =
        when {
            data.mediaProfile == BookMediaProfile.PDF && data.url != null && callFactory != null -> fetchPdfThumbnail()
            else ->
                store.thumbnailOrNull(data.serverId, data.bookId, data.pageNumber)?.let { file ->
                    SourceFetchResult(
                        source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
                        mimeType = "image/jpeg",
                        dataSource = DataSource.DISK,
                    )
                }
        }

    private suspend fun fetchPdfThumbnail(): FetchResult {
        val cached = store.get(data.serverId, data.bookId, data.pageNumber)
        if (cached != null) {
            return SourceFetchResult(
                source = ImageSource(cached.toOkioPath(), FileSystem.SYSTEM),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK,
            )
        }
        val bytes = fetchFromNetwork(data.url!!)
        val file =
            withContext(Dispatchers.IO) {
                store.putBytes(data.serverId, data.bookId, data.pageNumber, bytes)
            }
        return SourceFetchResult(
            source = ImageSource(file.toOkioPath(), FileSystem.SYSTEM),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK,
        )
    }

    private suspend fun fetchFromNetwork(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            val response = callFactory!!.newCall(Request.Builder().url(url).build()).execute()
            response.use {
                if (!it.isSuccessful) {
                    throw IOException("Thumbnail fetch failed: ${it.code}")
                }
                it.body?.bytes() ?: throw IOException("Empty thumbnail body")
            }
        }
}

/** [BookPageThumbnail]のCoilキャッシュキー。 */
class ThumbnailKeyer : Keyer<BookPageThumbnail> {
    override fun key(
        data: BookPageThumbnail,
        options: Options,
    ): String = "thumb:${data.serverId}:${data.bookId}:${data.pageNumber}:${data.mediaProfile}"
}
