package dev.komrd.core.prefetch

import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.Server
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchControllerNetworkPolicyTest {
    @Test
    fun offlinePauses_resumesOnRun_completesRemaining() =
        runTest {
            val fetcher = FakePageFetcher().apply { gate = CompletableDeferred() }
            val store = FakePrefetchStore()
            val policy = FakeNetworkPolicy(NetworkDecision.Pause(NetworkPauseReason.Offline))
            val ctrl = controller(this, fetcher, store, policy, parallelism = 1)

            ctrl.start(server(), book("b1", listOf(1, 2, 3)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle() // policyJobがPause→pause()、ワーカーはpage1でgate待機

            fetcher.releaseGate()
            advanceUntilIdle() // page1完了→page2でawaitNotPaused待機(pause中)
            assertEquals("page1のみ開始・page2以降はpauseで停止", 1, fetcher.calls.size)
            assertTrue("page1はキャッシュ済", store.isCached("s1", "b1", 1))
            assertFalse("page2はpause中なので未取得", store.isCached("s1", "b1", 2))

            policy.emit(NetworkDecision.Run) // NetworkCallback復帰想定
            advanceUntilIdle()
            ctrl.stop()

            assertEquals("復帰後は残り全件取得", 3, fetcher.calls.size)
        }

    @Test
    fun runProceeds_completesAll() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            val policy = FakeNetworkPolicy(NetworkDecision.Run)
            val ctrl = controller(this, fetcher, store, policy, parallelism = 1)

            ctrl.start(server(), book("b1", listOf(1, 2, 3)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle()
            ctrl.stop()

            assertEquals("Run中は全件取得", 3, fetcher.calls.size)
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun controller(
        scope: TestScope,
        fetcher: FakePageFetcher,
        store: FakePrefetchStore,
        policy: NetworkPolicy,
        parallelism: Int,
    ): PrefetchControllerImpl {
        val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        return PrefetchControllerImpl(
            scope = scope,
            pageFetcher = fetcher,
            store = store,
            networkPolicy = policy,
            parallelismFlow = kotlinx.coroutines.flow.flowOf(parallelism),
            backoff = BackoffConfig(baseMillis = 100, maxAttempts = 5),
            dispatcher = dispatcher,
        )
    }

    private fun server(): Server =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = "https://example.com",
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun book(
        id: String,
        pages: List<Int>,
    ): BookDetail =
        BookDetail(
            id = id,
            serverId = "s1",
            name = id,
            pages = pages.map { BookPage(number = it, url = "u$it") },
        )
}
