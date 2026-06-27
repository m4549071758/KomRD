package dev.komrd.core.cache

import java.io.File

/**
 * [ThumbnailStore]のテスト用Fake。生成は行わず、[makeAvailable]で登録したページのみ
 * [thumbnailOrNull]がファイルを返す(未登録はnull=プレースホルダ)。実ファイル不要。
 */
class FakeThumbnailStore : ThumbnailStore {
    private val available = mutableSetOf<String>()

    fun makeAvailable(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ) {
        available.add(key(serverId, bookId, pageNumber))
    }

    override suspend fun get(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File? = if (available.contains(key(serverId, bookId, pageNumber))) dummy(serverId, bookId, pageNumber) else null

    override suspend fun generate(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        source: File,
    ): File {
        available.add(key(serverId, bookId, pageNumber))
        return dummy(serverId, bookId, pageNumber)
    }

    override suspend fun putBytes(
        serverId: String,
        bookId: String,
        pageNumber: Int,
        bytes: ByteArray,
    ): File {
        available.add(key(serverId, bookId, pageNumber))
        return dummy(serverId, bookId, pageNumber)
    }

    override suspend fun thumbnailOrNull(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File? = get(serverId, bookId, pageNumber)

    private fun key(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): String = "$serverId/$bookId/$pageNumber"

    private fun dummy(
        serverId: String,
        bookId: String,
        pageNumber: Int,
    ): File = File("/tmp/fake-thumb/$serverId/$bookId/$pageNumber.jpg")
}
