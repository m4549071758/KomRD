package dev.komrd.core.prefetch

import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [PrefetchController]のin-memory Fake（テスト専用・M4バックグラウンド系テストで使用）。
 *
 * - [state] は [MutableStateFlow] で外部から Running/Idle を直接制御可能。
 * - [start] の呼出引数(server/book/currentPage/nextBook)を記録し、[lastStart] で参照可能。
 * - それ以外の操作は記録せず何もしない（M4テストでは使用しないため）。
 */
class FakePrefetchController(
    initialState: PrefetchState = PrefetchState.Idle,
) : PrefetchController {
    private val _state = MutableStateFlow<PrefetchState>(initialState)
    override val state: StateFlow<PrefetchState> = _state.asStateFlow()

    override var boundServerId: String? = null
        private set
    override var boundBookId: String? = null
        private set

    data class StartCall(
        val server: Server,
        val book: BookDetail,
        val currentPage: Int,
        val nextBook: NextBook?,
    )

    val startCalls: MutableList<StartCall> = mutableListOf()
    val lastStart: StartCall? get() = startCalls.lastOrNull()

    /** テストから state を遷移させる。 */
    fun setState(state: PrefetchState) {
        _state.value = state
    }

    override suspend fun start(
        server: Server,
        currentBook: BookDetail,
        currentPageNumber: Int,
        nextBook: NextBook?,
    ) {
        boundServerId = server.id
        boundBookId = currentBook.id
        startCalls += StartCall(server, currentBook, currentPageNumber, nextBook)
        // 本番の PrefetchControllerImpl と同様に start で Running へ遷移（テストで Idle へ戻して完了を駆動）。
        _state.value = PrefetchState.Running(remaining = startCalls.size, total = startCalls.size)
    }

    override fun onPageChanged(currentPageNumber: Int) = Unit

    override fun demand(
        bookId: String,
        pageNumber: Int,
    ) = Unit

    override fun pause() = Unit

    override fun resume() = Unit

    override fun stop() = Unit
}
