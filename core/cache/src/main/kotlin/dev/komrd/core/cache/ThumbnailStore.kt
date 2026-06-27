package dev.komrd.core.cache

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.komrd.core.database.dao.PrefetchIndexDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

interface ThumbnailStore {
    /** 既存サムネファイル。未生成はnull。 */
    suspend fun get(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File?

    /** [source]から縮小サムネを生成し書き出す。 */
    suspend fun generate(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        source: File,
    ): File

    suspend fun putBytes(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        bytes: ByteArray,
    ): File

    /** [PrefetchStore]命中ページのサムネを取得(未生成なら生成)。未命中はnull。 */
    suspend fun thumbnailOrNull(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File?
}

/**
 * [ThumbnailStore]の[BitmapFactory]実装。サムネは`thumbnailDir/serverId/bookId/${pageNumber}.jpg`。
 * 予算外・再生成可能のため[PrefetchIndexDao]の索引は持たない(ファイル存在判定のみ)。
 */
class BitmapFactoryThumbnailStore(
    private val thumbnailDir: File,
    private val store: PrefetchStore,
) : ThumbnailStore {
    override suspend fun get(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File? {
        val file = fileFor(serverId, bookId, pageNumber)
        return withContext(Dispatchers.IO) { if (file.exists()) file else null }
    }

    override suspend fun generate(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        source: File,
    ): File =
        withContext(Dispatchers.IO) {
            val sample = sampleSizeFor(source.absolutePath, TARGET_PX)
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap =
                BitmapFactory.decodeFile(source.absolutePath, decodeOpts)
                    ?: throw IOException("Thumbnail decode failed: ${source.absolutePath}")
            try {
                val out = fileFor(serverId, bookId, pageNumber)
                out.parentFile?.mkdirs()
                out.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
                out
            } finally {
                bitmap.recycle()
            }
        }

    override suspend fun putBytes(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        bytes: ByteArray,
    ): File =
        withContext(Dispatchers.IO) {
            val out = fileFor(serverId, bookId, pageNumber)
            out.parentFile?.mkdirs()
            out.writeBytes(bytes)
            out
        }

    override suspend fun thumbnailOrNull(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File? {
        get(serverId, bookId, pageNumber)?.let { return it }
        val source =
            withContext(Dispatchers.IO) {
                store.get(serverId, bookId, pageNumber.toString(), PrefetchStore.VARIANT_FULL)
            }
        return source?.let { generate(serverId, bookId, pageNumber, it.file) }
    }

    private fun fileFor(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File = File(File(File(thumbnailDir, serverId), bookId), "$pageNumber.jpg")

    private fun sampleSizeFor(
        path: String,
        target: Int,
    ): Int {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        var sample = 1
        while (longest / sample > target) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private companion object {
        const val TARGET_PX = 120
        const val JPEG_QUALITY = 80
    }
}
