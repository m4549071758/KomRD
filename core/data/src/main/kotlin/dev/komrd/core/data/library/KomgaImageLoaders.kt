package dev.komrd.core.data.library

import android.content.Context
import coil3.ImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.komrd.core.cache.PrefetchFetcherFactory
import dev.komrd.core.cache.PrefetchKeyer
import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.cache.ThumbnailFetcherFactory
import dev.komrd.core.cache.ThumbnailKeyer
import dev.komrd.core.cache.ThumbnailStore
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import okhttp3.Call
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface ImageLoaderProvider {
    fun forServer(server: Server): ImageLoader
}

@Singleton
class KomgaImageLoaders
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val clientFactory: KomgaClientFactory,
        private val prefetchStore: PrefetchStore,
        private val thumbnailStore: ThumbnailStore,
    ) : ImageLoaderProvider {
        private val loaders = ConcurrentHashMap<String, ImageLoader>()

        override fun forServer(server: Server): ImageLoader =
            loaders.computeIfAbsent(server.id) {
                val callFactory: Call.Factory = clientFactory.clientFor(server).okHttpClient
                ImageLoader
                    .Builder(context)
                    .components {
                        add(PrefetchKeyer())
                        add(PrefetchFetcherFactory(callFactory, prefetchStore))
                        add(ThumbnailKeyer())
                        add(ThumbnailFetcherFactory(thumbnailStore, callFactory))
                        add(OkHttpNetworkFetcherFactory(callFactory = { callFactory }))
                    }.build()
            }

        fun invalidate(serverId: String) {
            loaders.remove(serverId)?.shutdown()
        }
    }
