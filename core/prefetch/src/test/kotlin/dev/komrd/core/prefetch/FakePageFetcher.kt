package dev.komrd.core.prefetch

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.Server
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * [PageFetcher]のFake（テスト専用）。
 *
 * - [calls]: 受信履歴 (bookId, pageNumber)。
 * - [maxConcurrent]: 同時実行数のピーク（並列数検証）。
 * - [gate]: 設定すると各fetchはcompleteまでsuspend（並列数上限・preemptのタイミング制御）。
 * - [delayMillis]: fetch毎の模擬遅延。
 * - target毎に結果キューを設定可（[enqueueResult]）。未設定なら[defaultResult]。
 */
class FakePageFetcher : PageFetcher {
    val calls = mutableListOf<Pair<String, Int>>()
    val mediaProfiles = mutableListOf<BookMediaProfile>()
    val maxConcurrent = AtomicInteger(0)
    private val current = AtomicInteger(0)
    private val sequences = mutableMapOf<Pair<String, Int>, ArrayDeque<KomgaResult<ByteArray>>>()

    var delayMillis: Long = 0L
    var gate: CompletableDeferred<Unit>? = null
    var defaultResult: (bookId: String, pageNumber: Int) -> KomgaResult<ByteArray> =
        { _, page -> KomgaResult.Success(byteArrayOf(page.toByte())) }

    fun enqueueResult(
        bookId: String,
        pageNumber: Int,
        result: KomgaResult<ByteArray>,
    ) {
        sequences.getOrPut(bookId to pageNumber) { ArrayDeque() }.addLast(result)
    }

    /** 指定targetで[error]を[times]回失敗させた後、[successBytes]で成功させる（バックオフ検証用）。 */
    fun failThenSuccess(
        bookId: String,
        pageNumber: Int,
        error: KomgaError,
        times: Int,
        successBytes: ByteArray = byteArrayOf(pageNumber.toByte()),
    ) {
        repeat(times) { enqueueResult(bookId, pageNumber, KomgaResult.Failure(error)) }
        enqueueResult(bookId, pageNumber, KomgaResult.Success(successBytes))
    }

    override suspend fun fetch(
        server: Server,
        bookId: String,
        pageNumber: Int,
        mediaProfile: BookMediaProfile,
    ): KomgaResult<ByteArray> {
        calls += bookId to pageNumber
        mediaProfiles += mediaProfile
        current.incrementAndGet()
        maxConcurrent.updateAndGet { maxOf(it, current.get()) }
        try {
            gate?.await()
            if (delayMillis > 0L) delay(delayMillis)
            val seq = sequences[bookId to pageNumber]
            return seq?.removeFirstOrNull() ?: defaultResult(bookId, pageNumber)
        } finally {
            current.decrementAndGet()
        }
    }

    /** テスト用: gateを解放し待機中のfetchを一斉に進める。 */
    fun releaseGate() {
        gate?.complete(Unit)
    }
}
