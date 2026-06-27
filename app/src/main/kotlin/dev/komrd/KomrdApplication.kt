package dev.komrd

import android.app.Application
import android.util.Log
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import dev.komrd.core.data.startup.TrustCacheWarmer
import dev.komrd.core.prefetch.background.PrefetchBackgroundCoordinator
import dev.komrd.core.prefetch.background.PrefetchWorkerFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class KomrdApplication :
    Application(),
    Configuration.Provider {
    private companion object {
        private const val TAG = "KomrdApplication"
    }

    @Inject
    lateinit var trustCacheWarmer: TrustCacheWarmer

    @Inject
    lateinit var prefetchBackgroundCoordinator: PrefetchBackgroundCoordinator

    @Inject
    lateinit var prefetchWorkerFactory: PrefetchWorkerFactory

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            runCatching { trustCacheWarmer.warm() }
                .onFailure { Log.w(TAG, "TLS trust cache warming failed.", it) }
        }
        // M4: アプリ前面/背面を監視し、背面かつPrefetch稼働中にFGS/UIDTへ昇格して継続。
        ProcessLifecycleOwner.get().lifecycle.addObserver(prefetchBackgroundCoordinator)
    }

    // M4: WorkManagerへPrefetchWorkerFactoryを渡し PrefetchWorker を構築。自動初期化はManifestで抑制。
    override val workManagerConfiguration: Configuration
        get() =
            Configuration
                .Builder()
                .setWorkerFactory(prefetchWorkerFactory)
                .build()
}
