package dev.komrd.core.prefetch.background

import android.app.job.JobScheduler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import dev.komrd.core.prefetch.FakePrefetchController
import dev.komrd.core.prefetch.PrefetchState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * [PrefetchBackgroundCoordinator] の単体テスト（M4 / Robolectric + work-testing）。
 *
 * 前面/背面のLifecycleでRunning時にジョブを投入し、前面復帰でキャンセルする挙動を、
 * API34(JobScheduler userInitiated) と API33(WorkManager unique work) で検証する。
 */
@RunWith(RobolectricTestRunner::class)
class PrefetchBackgroundCoordinatorTest {
    private lateinit var owner: FakeLifecycleOwner
    private lateinit var coordinator: PrefetchBackgroundCoordinator

    @Before
    fun setUp() {
        val app = RuntimeEnvironment.getApplication()
        // WorkManager自動初期化抑制の影響を回避するため、テスト用に明示初期化。
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        owner = FakeLifecycleOwner()
    }

    @After
    fun tearDown() {
        // 残ジョブを掃除し、テスト間のJobScheduler状態漏れを防ぐ。
        RuntimeEnvironment
            .getApplication()
            .getSystemService(JobScheduler::class.java)
            ?.cancel(PrefetchBackgroundCoordinator.JOB_ID)
        WorkManager
            .getInstance(RuntimeEnvironment.getApplication())
            .cancelUniqueWork(PrefetchBackgroundCoordinator.UNIQUE_WORK_NAME)
    }

    @Test
    @Config(sdk = [34])
    fun onStop_whileRunning_schedulesUserInitiatedJob_onApi34() {
        attachCoordinator(FakePrefetchController(PrefetchState.Running(2, 5)))

        coordinator.onStop(owner)
        ShadowLooper.idleMainLooper()

        val scheduler = RuntimeEnvironment.getApplication().getSystemService(JobScheduler::class.java)
        assertNotNull("JobSchedulerが取得できる", scheduler)
        val job = scheduler?.getPendingJob(PrefetchBackgroundCoordinator.JOB_ID)
        assertNotNull("API34はJobSchedulerへuserInitiatedジョブがスケジュールされる", job)
        assertEquals(PrefetchBackgroundCoordinator.JOB_ID, job?.id)
    }

    @Test
    @Config(sdk = [34])
    fun onStart_cancelsScheduledJob_onApi34() {
        attachCoordinator(FakePrefetchController(PrefetchState.Running(2, 5)))

        coordinator.onStop(owner)
        ShadowLooper.idleMainLooper()
        coordinator.onStart(owner)
        ShadowLooper.idleMainLooper()

        val scheduler = RuntimeEnvironment.getApplication().getSystemService(JobScheduler::class.java)
        val job = scheduler?.getPendingJob(PrefetchBackgroundCoordinator.JOB_ID)
        assertNull("前面復帰でJobSchedulerのジョブはキャンセルされる", job)
    }

    @Test
    @Config(sdk = [33])
    fun onStop_whileRunning_enqueuesUniqueWork_onApi33() {
        attachCoordinator(FakePrefetchController(PrefetchState.Running(2, 5)))

        coordinator.onStop(owner)
        ShadowLooper.idleMainLooper()

        val infos =
            WorkManager
                .getInstance(RuntimeEnvironment.getApplication())
                .getWorkInfosForUniqueWork(PrefetchBackgroundCoordinator.UNIQUE_WORK_NAME)
                .get()
        assertTrue("API33はWorkManager unique workが1件enqueueされる", infos.size == 1)
    }

    @Test
    @Config(sdk = [33])
    fun onStart_cancelsUniqueWork_onApi33() {
        attachCoordinator(FakePrefetchController(PrefetchState.Running(2, 5)))

        coordinator.onStop(owner)
        ShadowLooper.idleMainLooper()
        coordinator.onStart(owner)
        ShadowLooper.idleMainLooper()

        val infos =
            WorkManager
                .getInstance(RuntimeEnvironment.getApplication())
                .getWorkInfosForUniqueWork(PrefetchBackgroundCoordinator.UNIQUE_WORK_NAME)
                .get()
        // cancelUniqueWork で ENQUEUED が取り消されることを検証（CANCELLED へ遷移 or 件数0）。
        assertTrue(
            "前面復帰でunique workはキャンセル済み(ENQUEUEDでない)",
            infos.none { it.state == androidx.work.WorkInfo.State.ENQUEUED },
        )
    }

    @Test
    @Config(sdk = [34])
    fun onStop_whileIdle_doesNothing() {
        attachCoordinator(FakePrefetchController(PrefetchState.Idle))

        coordinator.onStop(owner)

        val scheduler = RuntimeEnvironment.getApplication().getSystemService(JobScheduler::class.java)
        assertNull("Idle時はスケジュールしない", scheduler?.getPendingJob(PrefetchBackgroundCoordinator.JOB_ID))
    }

    private fun attachCoordinator(controller: FakePrefetchController) {
        coordinator = PrefetchBackgroundCoordinator(RuntimeEnvironment.getApplication(), controller)
        // 本番では ProcessLifecycleOwner.get().lifecycle.addObserver(coordinator) で登録される。
        owner.lifecycle.addObserver(coordinator)
    }

    /** テスト用 [LifecycleOwner]。handleLifecycleEvent で STARTED/STOPPED 等を駆動する。 */
    private class FakeLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun handleLifecycleEvent(event: Lifecycle.Event) {
            registry.handleLifecycleEvent(event)
        }
    }
}
