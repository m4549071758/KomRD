package dev.komrd.core.prefetch.background

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import dev.komrd.core.prefetch.PrefetchController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PrefetchWorker]を生成する [WorkerFactory]（M4）。
 *
 * hilt-work 1.2.0 が Kotlin 2.4 メタデータを読めないため、@HiltWorker を使わず本ファクトリで
 * Hilt注入済みの依存を渡して [PrefetchWorker] を構築する。KomrdApplication の Configuration へ設定。
 */
@Singleton
class PrefetchWorkerFactory
    @Inject
    constructor(
        private val controller: PrefetchController,
        private val restorer: PrefetchRestorer,
        private val notifier: PrefetchNotifier,
    ) : WorkerFactory() {
        override fun createWorker(
            appContext: Context,
            workerClassName: String,
            workerParameters: WorkerParameters,
        ): ListenableWorker? =
            if (workerClassName == PrefetchWorker::class.java.name) {
                PrefetchWorker(appContext, workerParameters, controller, restorer, notifier)
            } else {
                null
            }
    }
