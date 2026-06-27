package dev.komrd.core.data.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.data.bookmark.BookmarkRepository
import dev.komrd.core.data.bookmark.BookmarkRepositoryImpl
import dev.komrd.core.data.epub.EpubRepository
import dev.komrd.core.data.epub.EpubRepositoryImpl
import dev.komrd.core.data.library.CollectionRepository
import dev.komrd.core.data.library.CollectionRepositoryImpl
import dev.komrd.core.data.library.ImageLoaderProvider
import dev.komrd.core.data.library.KomgaImageLoaders
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.data.library.LibraryRepositoryImpl
import dev.komrd.core.data.library.ReadListRepository
import dev.komrd.core.data.library.ReadListRepositoryImpl
import dev.komrd.core.data.prefetch.NextBookResolver
import dev.komrd.core.data.prefetch.NextBookResolverImpl
import dev.komrd.core.data.prefetch.PrefetchCacheRepository
import dev.komrd.core.data.prefetch.PrefetchCacheRepositoryImpl
import dev.komrd.core.data.reader.BitmapFactoryPageDimensionResolver
import dev.komrd.core.data.reader.BookOverviewRepository
import dev.komrd.core.data.reader.BookOverviewRepositoryImpl
import dev.komrd.core.data.reader.PageDimensionResolver
import dev.komrd.core.data.reader.ReaderRepository
import dev.komrd.core.data.reader.ReaderRepositoryImpl
import dev.komrd.core.data.search.SearchRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.data.server.ServerRepositoryImpl
import dev.komrd.core.data.server.ServerSettingsRepository
import dev.komrd.core.data.server.ServerSettingsRepositoryImpl
import dev.komrd.core.data.server.UserRepository
import dev.komrd.core.data.server.UserRepositoryImpl
import dev.komrd.core.database.crypto.SecretCipher
import dev.komrd.core.database.dao.BookmarkDao
import dev.komrd.core.database.dao.ServersDao
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.datastore.DataStoreActiveServerStore
import dev.komrd.core.datastore.DataStoreLibraryFilterStore
import dev.komrd.core.datastore.DataStoreOnboardingStore
import dev.komrd.core.datastore.DataStorePrefetchSettingsStore
import dev.komrd.core.datastore.DataStoreReadingDirectionStore
import dev.komrd.core.datastore.DataStoreSpreadModeStore
import dev.komrd.core.datastore.LibraryFilterStore
import dev.komrd.core.datastore.OnboardingStore
import dev.komrd.core.datastore.PrefetchSettingsStore
import dev.komrd.core.datastore.ReadingDirectionStore
import dev.komrd.core.datastore.SpreadModeStore
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
@Suppress("TooManyFunctions")
object DataModule {
    @Provides
    @Singleton
    fun provideServerRepository(
        serversDao: ServersDao,
        cipher: SecretCipher,
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
        imageLoaders: KomgaImageLoaders,
    ): ServerRepository =
        ServerRepositoryImpl(
            serversDao = serversDao,
            cipher = cipher,
            clientFactory = clientFactory,
            trustStore = trustStore,
            invalidateImageLoaders = imageLoaders::invalidate,
        )

    @Provides
    @Singleton
    fun provideServerSettingsRepository(
        serverRepository: ServerRepository,
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): ServerSettingsRepository =
        ServerSettingsRepositoryImpl(
            serverRepository = serverRepository,
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideUserRepository(
        serverRepository: ServerRepository,
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): UserRepository =
        UserRepositoryImpl(
            serverRepository = serverRepository,
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideActiveServerStore(
        @ApplicationContext context: Context,
    ): ActiveServerStore = DataStoreActiveServerStore(context)

    @Provides
    @Singleton
    fun provideLibraryRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): LibraryRepository =
        LibraryRepositoryImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideReaderRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
        dimensionResolver: PageDimensionResolver,
    ): ReaderRepository =
        ReaderRepositoryImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
            dimensionResolver = dimensionResolver,
        )

    @Provides
    @Singleton
    fun provideBookOverviewRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): BookOverviewRepository =
        BookOverviewRepositoryImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun providePageDimensionResolver(): PageDimensionResolver = BitmapFactoryPageDimensionResolver()

    @Provides
    @Singleton
    fun provideEpubRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): EpubRepository =
        EpubRepositoryImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideImageLoaderProvider(loaders: KomgaImageLoaders): ImageLoaderProvider = loaders

    @Provides
    @Singleton
    fun provideNextBookResolver(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): NextBookResolver =
        NextBookResolverImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideCollectionRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): CollectionRepository =
        CollectionRepositoryImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideReadListRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): ReadListRepository =
        ReadListRepositoryImpl(
            clientFactory = clientFactory,
            trustStore = trustStore,
        )

    @Provides
    @Singleton
    fun provideSearchRepository(
        clientFactory: KomgaClientFactory,
        trustStore: ServerTrustStore,
    ): SearchRepository = SearchRepository(clientFactory = clientFactory, trustStore = trustStore)

    @Provides
    @Singleton
    fun provideReadingDirectionStore(
        @ApplicationContext context: Context,
    ): ReadingDirectionStore = DataStoreReadingDirectionStore(context)

    @Provides
    @Singleton
    fun provideOnboardingStore(
        @ApplicationContext context: Context,
    ): OnboardingStore = DataStoreOnboardingStore(context)

    @Provides
    @Singleton
    fun provideSpreadModeStore(
        @ApplicationContext context: Context,
    ): SpreadModeStore = DataStoreSpreadModeStore(context)

    @Provides
    @Singleton
    fun providePrefetchSettingsStore(
        @ApplicationContext context: Context,
    ): PrefetchSettingsStore = DataStorePrefetchSettingsStore(context)

    @Provides
    @Singleton
    @Suppress("MaxLineLength")
    fun providePrefetchCacheRepository(prefetchStore: PrefetchStore): PrefetchCacheRepository = PrefetchCacheRepositoryImpl(prefetchStore)

    @Provides
    @Singleton
    fun provideLibraryFilterStore(
        @ApplicationContext context: Context,
    ): LibraryFilterStore = DataStoreLibraryFilterStore(context)

    @Provides
    @Singleton
    fun provideBookmarkRepository(bookmarkDao: BookmarkDao): BookmarkRepository = BookmarkRepositoryImpl(bookmarkDao)
}
