package dev.komrd.core.prefetch.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.data.epub.EpubRepository
import dev.komrd.core.datastore.PrefetchSettingsStore
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import dev.komrd.core.prefetch.BackoffConfig
import dev.komrd.core.prefetch.ConnectivityManagerNetworkPolicy
import dev.komrd.core.prefetch.DataStorePrefetchContextStore
import dev.komrd.core.prefetch.KomgaPageFetcher
import dev.komrd.core.prefetch.KomgaResourceFetcher
import dev.komrd.core.prefetch.NetworkPolicy
import dev.komrd.core.prefetch.PageFetcher
import dev.komrd.core.prefetch.PrefetchContextStore
import dev.komrd.core.prefetch.PrefetchController
import dev.komrd.core.prefetch.PrefetchControllerImpl
import dev.komrd.core.prefetch.PrefetchEvictionConfig
import dev.komrd.core.prefetch.PrefetchEvictor
import dev.komrd.core.prefetch.PrefetchEvictorImpl
import dev.komrd.core.prefetch.PrefetchNetworkConfig
import dev.komrd.core.prefetch.ResourceFetcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PrefetchModule {
    @Provides
    @Singleton
    fun providePageFetcher(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): PageFetcher = KomgaPageFetcher(clientFactory, trustStore)

    @Provides
    @Singleton
    fun provideResourceFetcher(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): ResourceFetcher = KomgaResourceFetcher(clientFactory, trustStore)

    @Provides
    @Singleton
    fun provideNetworkPolicy(
        @ApplicationContext context: Context,
        settingsStore: PrefetchSettingsStore,
    ): NetworkPolicy =
        ConnectivityManagerNetworkPolicy(
            context,
            settingsStore.allowOnMobile.map { PrefetchNetworkConfig(allowOnMobile = it) },
        )

    @Provides
    @Singleton
    fun provideEvictionConfigFlow(settingsStore: PrefetchSettingsStore): Flow<PrefetchEvictionConfig> =
        combine(settingsStore.retentionDays, settingsStore.maxBytes) { days, bytes ->
            PrefetchEvictionConfig(retentionDays = days, maxBytes = bytes)
        }

    @Provides
    @Singleton
    fun providePrefetchEvictor(
        store: PrefetchStore,
        configFlow: Flow<PrefetchEvictionConfig>,
    ): PrefetchEvictor = PrefetchEvictorImpl(store, configFlow)

    @Provides
    @Singleton
    fun provideBackoffConfig(): BackoffConfig = BackoffConfig()

    @Provides
    @Singleton
    fun providePrefetchContextStore(
        @ApplicationContext context: Context,
    ): PrefetchContextStore = DataStorePrefetchContextStore(context)

    @Provides
    @Singleton
    @Suppress("LongParameterList") // DI提供関数のため閾値超は許容（DIコンテナが呼ぶのみ）
    fun providePrefetchController(
        @ApplicationScope scope: CoroutineScope,
        pageFetcher: PageFetcher,
        store: PrefetchStore,
        networkPolicy: NetworkPolicy,
        evictor: PrefetchEvictor,
        evictionConfigFlow: Flow<PrefetchEvictionConfig>,
        settingsStore: PrefetchSettingsStore,
        backoff: BackoffConfig,
        @ApplicationScope dispatcher: CoroutineDispatcher,
        epubRepository: EpubRepository,
        resourceFetcher: ResourceFetcher,
    ): PrefetchController =
        PrefetchControllerImpl(
            scope = scope,
            pageFetcher = pageFetcher,
            store = store,
            networkPolicy = networkPolicy,
            evictor = evictor,
            evictionConfigFlow = evictionConfigFlow,
            parallelismFlow = settingsStore.parallelism,
            backoff = backoff,
            dispatcher = dispatcher,
            epubRepository = epubRepository,
            resourceFetcher = resourceFetcher,
        )
}
