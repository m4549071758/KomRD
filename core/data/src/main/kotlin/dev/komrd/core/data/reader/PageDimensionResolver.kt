package dev.komrd.core.data.reader

import android.graphics.BitmapFactory
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookPage
import dev.komrd.core.network.KomgaClient

interface PageDimensionResolver {
    suspend fun resolve(
        client: KomgaClient,
        bookId: String,
        page: BookPage,
    ): BookPage
}

/**
 * 何もしない既定実装(ページをそのまま返す)。寸法が既に揃っている、または
 * フォールバック不要な場面(主にテスト)で使う。
 */
object NoOpPageDimensionResolver : PageDimensionResolver {
    override suspend fun resolve(
        client: KomgaClient,
        bookId: String,
        page: BookPage,
    ): BookPage = page
}

/**
 * ページ画像を取得してヘッダのみデコード([BitmapFactory.Options.inJustDecodeBounds])し
 * 実寸を読み取る本番実装。ビットマップ本体は確保しない(メモリ抑制)。
 * nullページが稀(Komgaは通常寸法を返す)ため全体コストは小さい。
 */
class BitmapFactoryPageDimensionResolver : PageDimensionResolver {
    override suspend fun resolve(
        client: KomgaClient,
        bookId: String,
        page: BookPage,
    ): BookPage {
        if (page.width != null && page.height != null) return page
        return when (val result = client.getBookPage(bookId, page.number)) {
            is KomgaResult.Failure -> page
            is KomgaResult.Success -> {
                val body = result.value
                val options =
                    BitmapFactory
                        .Options()
                        .apply { inJustDecodeBounds = true }
                body.use { responseBody ->
                    responseBody.byteStream().use { stream ->
                        BitmapFactory.decodeStream(stream, null, options)
                    }
                }
                if (options.outWidth > 0 && options.outHeight > 0) {
                    page.copy(width = options.outWidth, height = options.outHeight)
                } else {
                    page
                }
            }
        }
    }
}
