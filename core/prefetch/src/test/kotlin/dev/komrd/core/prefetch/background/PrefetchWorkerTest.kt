package dev.komrd.core.prefetch.background

import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.Server
import dev.komrd.core.prefetch.FakePrefetchController
import dev.komrd.core.prefetch.PrefetchContext
import dev.komrd.core.prefetch.PrefetchState
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager

/**
 * [PrefetchWorker] の単体テスト（M4 / Robolectric + work-testing）。
 *
 * WorkManagerのWorkerParametersは [TestListenableWorkerBuilder] でダミー Worker を構築して
 * リフレクションで取り出したものを流用する。本Workerの [PrefetchWorker.enterForeground] は
 * テスト用サブクラスで上書きし、WorkManager の FGS連携（Dispatchers.Main 依存でテスト困難）を
 * 迂回して通知を ShadowNotificationManager へ直接投稿し検証する（本番挙動不変）。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchWorkerTest {
    private val server: Server =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = "https://example.com",
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun book(): BookDetail =
        BookDetail(
            id = "b1",
            seriesId = "series-1",
            serverId = "s1",
            name = "b1",
            pages = (1..10).map { BookPage(number = it, url = "u$it") },
        )

    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters
    private lateinit var notifier: PrefetchNotifier

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        workerParams = extractWorkerParams()
        notifier = PrefetchNotifier(context)
    }

    @Test
    fun doWork_idleAndRestoreFails_returnsSuccessWithoutNotification() =
        runTest {
            val controller = FakePrefetchController(PrefetchState.Idle)
            val restorer =
                PrefetchRestorer(
                    FakePrefetchContextStore(initial = null), // 文脈無し → restore失敗
                    FakeServerRepository(mapOf()),
                    FakeReaderRepository(KomgaResult.Success(book())),
                    FakeNextBookResolver(KomgaResult.Success(null)),
                    controller,
                )
            val worker = TestablePrefetchWorker(context, workerParams, controller, restorer, notifier)

            val result = worker.doWork()

            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            // restore失敗時は enterForeground 呼出なし・通知も出ない
            assertNull(worker.enteredForeground)
            assertEquals(0, shadowNotificationManager().allNotifications.size)
        }

    @Test
    fun doWork_restoreSucceedsThenRunningToIdle_returnsSuccessWithProgressNotification() =
        runTest {
            val controller = FakePrefetchController(PrefetchState.Idle)
            val ctxStore = FakePrefetchContextStore(PrefetchContext("s1", "b1", 3, null, null))
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book())),
                    FakeNextBookResolver(KomgaResult.Success(NextBook("b2", 20))),
                    controller,
                )
            val worker = TestablePrefetchWorker(context, workerParams, controller, restorer, notifier)

            // doWorkは enterForeground→state.collect→first{Idle} を待機する。
            // restoreでcontroller.startが呼ばれ、FakeControllerはstartでRunningへ遷移する。
            val doWorkJob = async(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }
            advanceUntilIdle()

            // Running 中に enterForeground が呼ばれ通知が投稿される
            assertNotNull("Running で enterForeground が呼ばれる", worker.enteredForeground)
            assertTrue(
                "Running 中に進捗通知が出る",
                shadowNotificationManager().getNotification(PrefetchNotifier.NOTIFICATION_ID) != null,
            )

            // Idle へ遷移してdoWork完了・通知消去
            controller.setState(PrefetchState.Idle)
            advanceUntilIdle()

            val result = doWorkJob.await()

            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            assertNull(
                "Idle 到達で通知はキャンセルされる",
                shadowNotificationManager().getNotification(PrefetchNotifier.NOTIFICATION_ID),
            )
            assertEquals("restore で controller.start が呼ばれる", "b1", controller.lastStart?.book?.id)
        }

    @Test
    fun doWork_alreadyRunningSkipsRestore_entersForegroundAndCompletes() =
        runTest {
            val controller = FakePrefetchController(PrefetchState.Running(2, 5))
            val restorer =
                PrefetchRestorer(
                    FakePrefetchContextStore(initial = null),
                    FakeServerRepository(mapOf()),
                    FakeReaderRepository(KomgaResult.Success(book())),
                    FakeNextBookResolver(KomgaResult.Success(null)),
                    controller,
                )
            val worker = TestablePrefetchWorker(context, workerParams, controller, restorer, notifier)

            val doWorkJob = async(start = CoroutineStart.UNDISPATCHED) { worker.doWork() }
            advanceUntilIdle()

            // Running 中に enterForeground 呼出 → 通知投稿
            assertNotNull("Running で enterForeground が呼ばれる", worker.enteredForeground)
            assertNull("restore は呼ばれない（最初から Running のためスキップ）", controller.lastStart)

            // Idle へ遷移して完了
            controller.setState(PrefetchState.Idle)
            advanceUntilIdle()

            val result = doWorkJob.await()

            assertEquals(
                androidx.work.ListenableWorker.Result
                    .success(),
                result,
            )
            // Idle到達で通知キャンセル
            assertNull(
                shadowNotificationManager().getNotification(PrefetchNotifier.NOTIFICATION_ID),
            )
        }

    private fun shadowNotificationManager(): ShadowNotificationManager {
        val nm = context.getSystemService(NotificationManager::class.java)
        return Shadows.shadowOf(nm)
    }

    private fun extractWorkerParams(): WorkerParameters {
        val worker = TestListenableWorkerBuilder.from(context, DummyWorkerForWorkerTest::class.java).build()
        val field =
            ListenableWorker::class.java.declaredFields.first { it.type == WorkerParameters::class.java }
        field.isAccessible = true
        return field.get(worker) as WorkerParameters
    }

    /**
     * テスト用 [PrefetchWorker] サブクラス。[enterForeground] を上書きし WorkManager の FGS連携を迂回して
     * [PrefetchNotifier.buildForegroundInfo] で生成した ForegroundInfo を記録し、通知を直接投稿する。
     * 親の `notifier` は private でアクセス不能のため、本サブクラスで独自に保持して参照する。
     */
    private class TestablePrefetchWorker(
        appContext: Context,
        params: WorkerParameters,
        controller: dev.komrd.core.prefetch.PrefetchController,
        restorer: PrefetchRestorer,
        private val notifier: PrefetchNotifier,
    ) : PrefetchWorker(appContext, params, controller, restorer, notifier) {
        var enteredForeground: PrefetchState.Running? = null
            private set
        var foregroundInfo: ForegroundInfo? = null
            private set

        override suspend fun enterForeground(state: PrefetchState.Running) {
            foregroundInfo = notifier.buildForegroundInfo(state)
            enteredForeground = state
            // テストでは WorkManager の SystemForegroundService を経由せず直接通知を投稿して検証。
            notifier.updateProgress(state)
        }
    }
}

/** テスト用ダミー Worker。トップレベルに置き androidx.work.WorkerFactory のリフレクションから
 *  アクセス可能にする（private inner class だとIllegalAccessException）。 */
class DummyWorkerForWorkerTest(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()
}
