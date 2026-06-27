package dev.komrd.core.prefetch.background

import android.app.job.JobParameters
import android.app.job.JobService
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.VisibleForTesting
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

open class PrefetchJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // EntryPoint経由の依存取得は初回アクセス(onStartJob)まで遅延し、テストでは
    // resolveXxx を上書きして Hilt 無しで依存を注入可能（本番挙動不変）。
    private val controller: PrefetchController by lazy { resolveController() }
    private val restorer: PrefetchRestorer by lazy { resolveRestorer() }
    private val notifier: PrefetchNotifier by lazy { resolveNotifier() }

    @VisibleForTesting
    protected open fun resolveController(): PrefetchController = entryPoint().controller()

    @VisibleForTesting
    protected open fun resolveRestorer(): PrefetchRestorer = entryPoint().restorer()

    @VisibleForTesting
    protected open fun resolveNotifier(): PrefetchNotifier = entryPoint().notifier()

    private fun entryPoint(): PrefetchJobEntryPoint =
        EntryPointAccessors.fromApplication(applicationContext, PrefetchJobEntryPoint::class.java)

    override fun onStartJob(params: JobParameters): Boolean {
        // true: 非同期で継続し、完了時にjobFinishedを呼ぶ。
        serviceScope.launch {
            if (controller.state.value is PrefetchState.Idle) {
                if (!restorer.restore()) {
                    jobFinished(params, false)
                    return@launch
                }
            }
            val initial = controller.state.value
            if (initial is PrefetchState.Idle) {
                jobFinished(params, false)
                return@launch
            }

            val notification = notifier.buildNotification(initial as PrefetchState.Running)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    PrefetchNotifier.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                @Suppress("DEPRECATION")
                startForeground(PrefetchNotifier.NOTIFICATION_ID, notification)
            }

            // Running中の進捗を通知へ反映しつつ、Idleになるまで待機。Idle到達でprogressJobをcancelし
            // coroutineScope を完了させる（collect は無限のため明示cancelしないと完了しない）。
            coroutineScope {
                val progressJob =
                    launch {
                        controller.state.collect { state ->
                            if (state is PrefetchState.Running) notifier.updateProgress(state)
                        }
                    }
                controller.state.first { it is PrefetchState.Idle }
                progressJob.cancel()
            }

            notifier.cancel()
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        // 前面復帰時のキャンセル等。再スケジュールしない（false）。Coordinatorが背面時に再scheduleする。
        serviceScope.cancel()
        notifier.cancel()
        return false
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface PrefetchJobEntryPoint {
        fun controller(): PrefetchController

        fun restorer(): PrefetchRestorer

        fun notifier(): PrefetchNotifier
    }
}
