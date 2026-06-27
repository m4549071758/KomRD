package dev.komrd.core.prefetch.background

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

open class PrefetchWorker(
    appContext: Context,
    params: WorkerParameters,
    private val controller: PrefetchController,
    private val restorer: PrefetchRestorer,
    private val notifier: PrefetchNotifier,
) : CoroutineWorker(appContext, params) {
    @Suppress("ReturnCount") // 復元不要/即完了/Idle到達 の早期returnが明示的なため許容
    override suspend fun doWork(): Result {
        // プロセス再起等でController未startなら文脈復元。復元対象なし＝何もしないで完了。
        if (controller.state.value is PrefetchState.Idle) {
            if (!restorer.restore()) return Result.success()
        }
        val initial = controller.state.value
        if (initial is PrefetchState.Idle) return Result.success()

        // dataSync FGS昇格＋通知表示（ForegroundInfoにtype指定）。テストで上書き可能。
        enterForeground(initial as PrefetchState.Running)

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
        return Result.success()
    }

    /**
     * FGSへ昇格し進捗通知を表示。本番は [setForeground] へ委譲するが、テストでは WorkManager
     * のFGS連携が Dispatchers.Main に依存し難しいため、本メソッドを上書きして検証する（本番挙動不変）。
     */
    @VisibleForTesting
    protected open suspend fun enterForeground(state: PrefetchState.Running) {
        setForeground(notifier.buildForegroundInfo(state))
    }
}
