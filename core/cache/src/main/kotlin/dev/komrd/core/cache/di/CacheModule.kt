package dev.komrd.core.cache.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.cache.BitmapFactoryThumbnailStore
import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.cache.PrefetchStoreImpl
import dev.komrd.core.cache.ThumbnailStore
import dev.komrd.core.database.dao.PrefetchIndexDao
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CacheModule {
    @Provides
    @Singleton
    fun providePrefetchStore(
        @ApplicationContext context: Context,
        dao: PrefetchIndexDao,
    ): PrefetchStore = PrefetchStoreImpl(dao, File(context.filesDir, "prefetch"))

    @Provides
    @Singleton
    fun provideThumbnailStore(
        @ApplicationContext context: Context,
        prefetchStore: PrefetchStore,
    ): ThumbnailStore = BitmapFactoryThumbnailStore(File(context.cacheDir, "thumbnails"), prefetchStore)
}
