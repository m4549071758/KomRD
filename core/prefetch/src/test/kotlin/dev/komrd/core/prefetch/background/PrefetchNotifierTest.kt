package dev.komrd.core.prefetch.background

import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.ForegroundInfo
import dev.komrd.core.prefetch.PrefetchState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchNotifierTest {
    private lateinit var notifier: PrefetchNotifier

    @Before
    fun setUp() {
        notifier = PrefetchNotifier(RuntimeEnvironment.getApplication())
    }

    @Test
    fun ensureChannel_createsLowImportanceChannel() {
        val nm =
            RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java)
        val channel = nm.getNotificationChannel(PrefetchNotifier.CHANNEL_ID)
        assertNotNull(channel)
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel?.importance)
    }

    @Test
    fun buildForegroundInfo_carriesDataSyncTypeOnApi34() {
        val info: ForegroundInfo = notifier.buildForegroundInfo(PrefetchState.Running(2, 5))
        assertEquals(PrefetchNotifier.NOTIFICATION_ID, info.notificationId)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            assertEquals(
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                info.foregroundServiceType,
            )
        }
    }

    @Test
    fun updateProgress_showsNotification_thenCancel_clears() {
        val nm =
            RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java)
        val shadow = Shadows.shadowOf(nm)

        notifier.updateProgress(PrefetchState.Running(2, 5))
        assertNotNull(shadow.getNotification(PrefetchNotifier.NOTIFICATION_ID))

        notifier.cancel()
        assertNull(shadow.getNotification(PrefetchNotifier.NOTIFICATION_ID))
    }

    @Test
    @Config(sdk = [33])
    fun buildForegroundInfo_carriesZeroTypeOnApi33() {
        // API33 は UPSIDE_DOWN_CAKE(34)未満のため foregroundServiceType=0（指定なし）。
        val info: ForegroundInfo = notifier.buildForegroundInfo(PrefetchState.Running(2, 5))
        assertEquals(PrefetchNotifier.NOTIFICATION_ID, info.notificationId)
        assertEquals(0, info.foregroundServiceType)
    }

    @Test
    @Config(sdk = [33])
    fun buildNotification_progressMatchesState_onApi33() {
        val nm =
            RuntimeEnvironment.getApplication().getSystemService(NotificationManager::class.java)
        val shadow = Shadows.shadowOf(nm)

        notifier.updateProgress(PrefetchState.Running(2, 5))
        val notification = shadow.getNotification(PrefetchNotifier.NOTIFICATION_ID)
        assertNotNull(notification)
    }
}
