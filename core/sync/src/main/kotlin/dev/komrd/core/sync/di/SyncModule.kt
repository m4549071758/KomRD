package dev.komrd.core.sync.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.database.dao.EpubProgressQueueDao
import dev.komrd.core.database.dao.ReadProgressQueueDao
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import dev.komrd.core.sync.EpubProgressSyncEngine
import dev.komrd.core.sync.EpubProgressSyncEngineImpl
import dev.komrd.core.sync.ReadProgressSyncEngine
import dev.komrd.core.sync.ReadProgressSyncEngineImpl
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SyncModule {
    @Provides
    @Singleton
    fun provideReadProgressSyncEngine(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
        queueDao: ReadProgressQueueDao,
    ): ReadProgressSyncEngine = ReadProgressSyncEngineImpl(clientFactory, trustStore, queueDao)

    @Provides
    @Singleton
    fun provideEpubProgressSyncEngine(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
        queueDao: EpubProgressQueueDao,
    ): EpubProgressSyncEngine = EpubProgressSyncEngineImpl(clientFactory, trustStore, queueDao)
}
