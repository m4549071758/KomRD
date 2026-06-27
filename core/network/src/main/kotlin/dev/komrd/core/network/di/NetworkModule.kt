package dev.komrd.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.database.dao.ServerTrustDao
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.auth.InMemorySessionStore
import dev.komrd.core.network.auth.SessionStore
import dev.komrd.core.network.tls.DatabaseServerTrustStore
import dev.komrd.core.network.tls.ServerTrustStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideSessionStore(): SessionStore = InMemorySessionStore()

    @Provides
    @Singleton
    fun provideServerTrustStore(dao: ServerTrustDao): ServerTrustStore = DatabaseServerTrustStore(dao)

    @Provides
    @Singleton
    fun provideKomgaClientFactory(
        sessionStore: SessionStore,
        trustStore: ServerTrustStore,
    ): KomgaClientFactory = KomgaClientFactory(sessionStore = sessionStore, trustStore = trustStore)
}
