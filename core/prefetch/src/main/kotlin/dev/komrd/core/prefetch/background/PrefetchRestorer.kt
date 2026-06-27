package dev.komrd.core.prefetch.background

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.prefetch.NextBookResolver
import dev.komrd.core.data.reader.ReaderRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.prefetch.PrefetchContextStore
import dev.komrd.core.prefetch.PrefetchController
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@Suppress("ReturnCount") // 復元ステップの早期returnが明示的になるため閾値超は許容
class PrefetchRestorer
    @Inject
    constructor(
        private val contextStore: PrefetchContextStore,
        private val serverRepository: ServerRepository,
        private val readerRepository: ReaderRepository,
        private val nextBookResolver: NextBookResolver,
        private val controller: PrefetchController,
    ) {
        /**
         * [PrefetchContextStore]から文脈を復元し [PrefetchController.start] する。
         * @return 復元してstartした場合 true。文脈なし/サーバ・Book解決失敗で何もしない場合 false。
         */
        suspend fun restore(): Boolean {
            val ctx = contextStore.prefetchContext.first() ?: return false
            val server = serverRepository.byId(ctx.serverId) ?: return false
            val bookResult = readerRepository.loadBook(server, ctx.bookId)
            if (bookResult !is KomgaResult.Success) return false
            val book = bookResult.value
            val nextBook = resolveNextBook(server, book, ctx)
            controller.start(server, book, ctx.currentPage, nextBook)
            return true
        }

        private suspend fun resolveNextBook(
            server: dev.komrd.core.model.Server,
            book: dev.komrd.core.model.BookDetail,
            ctx: dev.komrd.core.prefetch.PrefetchContext,
        ): NextBook? {
            val nextBookId = ctx.nextBookId ?: return null
            // pagesCountが永続化されていれば再解決せず復元（Series全走査回避）。
            ctx.nextBookPagesCount?.let { return NextBook(nextBookId, it) }
            // 欠落時のみResolverで解決。解決結果のbookIdが永続化値と一致する場合のみ採用（保守）。
            val seriesId = book.seriesId ?: return null
            val result = nextBookResolver.resolve(server, book.id, ReadingContext.Series(seriesId))
            val resolved = (result as? KomgaResult.Success)?.value ?: return null
            return if (resolved?.bookId == nextBookId) resolved else null
        }
    }
