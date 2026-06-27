package dev.komrd.core.prefetch.background

import android.app.job.JobParameters
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.NextBook
import dev.komrd.core.prefetch.FakePrefetchController
import dev.komrd.core.prefetch.PrefetchState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

/**
 * [PrefetchJobService] の軽量単体テスト（M4 / Robolectric）。
 *
 * Hilt EntryPoint 経由の依存取得は [PrefetchJobService.resolveController] 等（protected open）を
 * テスト用サブクラスで上書きして回避する（本番挙動不変）。API34 で検証。
 *
 * 検証範囲（軽量）:
 * - [onStartJob] は serviceScope.launch して true を返す（非同期で継続する宣言）。
 * - [onStopJob] は serviceScope.cancel + notifier.cancel して false を返す（再スケジュールしない）。
 *
 * 完全なFGS昇格〜Idle到達〜jobFinished のHilt込みシナリオは実機検証TODO（PR本文に明記）。
 * 本テストでは restore() が [HangingPrefetchContextStore] で永遠に suspend するよう缚り、
 * launch 本体が jobFinished/startForeground に到達しない状態で同期戻り値のみ検証する。
 * JobParameters は本テストでは使用されない（launch が restore で suspend するため jobFinished 未到達）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchJobServiceTest {
    private lateinit var controller: FakePrefetchController
    private lateinit var restorer: PrefetchRestorer
    private lateinit var service: TestablePrefetchJobService

    @Before
    fun setUp() {
        controller = FakePrefetchController(PrefetchState.Idle)
        // restore() が contextStore.prefetchContext.first() で永遠に suspend するよう缚る。
        restorer =
            PrefetchRestorer(
                HangingPrefetchContextStore(),
                FakeServerRepository(mapOf()),
                FakeReaderRepository(
                    KomgaResult.Success(BookDetail("b1", serverId = "s1", name = "b1", pages = emptyList())),
                ),
                FakeNextBookResolver(KomgaResult.Success(NextBook("b2", 20))),
                controller,
            )
        service = Robolectric.buildService(TestablePrefetchJobService::class.java).get()
        service.controllerOverride = controller
        service.restorerOverride = restorer
        service.notifierOverride = PrefetchNotifier(RuntimeEnvironment.getApplication())
    }

    @After
    fun tearDown() {
        // 残コルーチンを確実にキャンセルしテスト間の状態漏れを防ぐ。
        service.onDestroy()
    }

    @Test
    fun onStartJob_launchesAsyncAndReturnsTrue() {
        val result = service.onStartJob(unusedParams())

        assertEquals("onStartJobは非同期継続を宣言しtrueを返す", true, result)
    }

    @Test
    fun onStopJob_cancelsScopeAndReturnsFalse() {
        // 先に onStartJob して serviceScope.launch を発生させる。
        service.onStartJob(unusedParams())

        val result = service.onStopJob(unusedParams())

        assertEquals("onStopJobは再スケジュールせずfalseを返す", false, result)
    }

    /**
     * launch 本体は restore() で永遠に suspend し jobFinished(params) に到達しないため、
     * params は参照されない。JobParameters は公開コンストラクタ無しのためリフレクションで生成。
     */
    private fun unusedParams(): JobParameters = ReflectionHelpers.newInstance(JobParameters::class.java)

    /**
     * テスト用 [PrefetchJobService] サブクラス。resolveXxx を上書きし、Hilt EntryPoint を経由せず
     * テストから注入した依存を返す。本番クラスの本番挙動は不変（resolveXxx はprotected open）。
     */
    private class TestablePrefetchJobService : PrefetchJobService() {
        var controllerOverride: dev.komrd.core.prefetch.PrefetchController? = null
        var restorerOverride: PrefetchRestorer? = null
        var notifierOverride: PrefetchNotifier? = null

        override fun resolveController() = controllerOverride!!

        override fun resolveRestorer() = restorerOverride!!

        override fun resolveNotifier() = notifierOverride!!
    }
}
