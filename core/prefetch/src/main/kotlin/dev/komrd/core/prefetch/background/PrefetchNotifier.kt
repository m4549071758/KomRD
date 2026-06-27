package dev.komrd.core.prefetch.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.komrd.core.prefetch.PrefetchState
import dev.komrd.core.prefetch.R
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PrefetchNotifier
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        init {
            ensureChannel()
        }

        private fun ensureChannel() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel =
                    NotificationChannel(
                        CHANNEL_ID,
                        context.getString(R.string.prefetch_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        description = context.getString(R.string.prefetch_channel_desc)
                    }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }

        /** WorkManager CoroutineWorker 用の [ForegroundInfo]（dataSync FGSタイプはAPI34+で指定）。 */
        fun buildForegroundInfo(state: PrefetchState.Running): ForegroundInfo =
            ForegroundInfo(
                NOTIFICATION_ID,
                buildNotification(state),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )

        /** JobService の startForeground 用 [Notification]。 */
        fun buildNotification(state: PrefetchState.Running): Notification {
            val text = context.getString(R.string.prefetch_notif_progress, state.remaining, state.total)
            val done = (state.total - state.remaining).coerceAtLeast(0)
            return NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_prefetch_notif)
                .setContentTitle(context.getString(R.string.prefetch_notif_title))
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setProgress(state.total.coerceAtLeast(1), done, false)
                .build()
        }

        /** 進捗更新（Running中の逐一更新）。 */
        fun updateProgress(state: PrefetchState.Running) {
            context
                .getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(state))
        }

        /** 通知キャンセル（前面復帰・Window完了時）。 */
        fun cancel() {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        companion object {
            const val CHANNEL_ID = "prefetch_progress"
            const val NOTIFICATION_ID = 4001
        }
    }
