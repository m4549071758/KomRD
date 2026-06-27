package dev.komrd.core.prefetch

import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.Server
import kotlinx.coroutines.flow.StateFlow

interface PrefetchController {
    val state: StateFlow<PrefetchState>

    /** 現在[start]で束縛中のserverId。未start時はnull。 */
    val boundServerId: String?

    /** 現在[start]で束縛中のbookId。未start時はnull。 */
    val boundBookId: String?

    suspend fun start(
        server: Server,
        currentBook: BookDetail,
        currentPageNumber: Int,
        nextBook: NextBook?,
    )

    fun onPageChanged(currentPageNumber: Int)

    fun demand(
        bookId: String,
        pageNumber: Int,
    )

    fun pause()

    fun resume()

    fun stop()

    companion object {
        const val DEFAULT_PARALLELISM: Int = 2
    }
}
