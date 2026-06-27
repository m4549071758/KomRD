package dev.komrd.core.prefetch.background

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchState
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefetchBackgroundCoordinator
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val controller: PrefetchController,
    ) : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) {
            if (controller.state.value !is PrefetchState.Running) return
            schedule()
        }

        override fun onStart(owner: LifecycleOwner) {
            cancelScheduled()
        }

        private fun schedule() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                scheduleUserInitiatedJob()
            } else {
                enqueueWorkManager()
            }
        }

        private fun cancelScheduled() {
            // SDK切替時の残留を防ぐため両方キャンセル。前面復帰時は稼働を止め通知を消す。
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
            }
        }

        private fun enqueueWorkManager() {
            val request = OneTimeWorkRequestBuilder<PrefetchWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }

        private fun scheduleUserInitiatedJob() {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val info =
                JobInfo
                    .Builder(JOB_ID, ComponentName(context, PrefetchJobService::class.java))
                    .setUserInitiated(true)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            scheduler.schedule(info)
        }

        companion object {
            const val UNIQUE_WORK_NAME = "prefetch_background"
            const val JOB_ID = 4001
        }
    }
