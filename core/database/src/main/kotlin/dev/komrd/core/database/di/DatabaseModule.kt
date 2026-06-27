package dev.komrd.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.komrd.core.database.KomrdDatabase
import dev.komrd.core.database.MIGRATION_1_2
import dev.komrd.core.database.MIGRATION_2_3
import dev.komrd.core.database.MIGRATION_3_4
import dev.komrd.core.database.MIGRATION_4_5
import dev.komrd.core.database.MIGRATION_5_6
import dev.komrd.core.database.MIGRATION_6_7
import dev.komrd.core.database.MIGRATION_7_8
import dev.komrd.core.database.MIGRATION_8_9
import dev.komrd.core.database.crypto.KeystoreSecretCipher
import dev.komrd.core.database.crypto.SecretCipher
import dev.komrd.core.database.dao.BookmarkDao
import dev.komrd.core.database.dao.EpubProgressQueueDao
import dev.komrd.core.database.dao.PrefetchIndexDao
import dev.komrd.core.database.dao.ReadProgressQueueDao
import dev.komrd.core.database.dao.ServerTrustDao
import dev.komrd.core.database.dao.ServersDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideKomrdDatabase(
        @ApplicationContext context: Context,
    ): KomrdDatabase =
        Room
            .databaseBuilder(context, KomrdDatabase::class.java, "komrd.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
            ).build()

    @Provides
    @Singleton
    fun provideServersDao(db: KomrdDatabase): ServersDao = db.serversDao()

    @Provides
    @Singleton
    fun provideServerTrustDao(db: KomrdDatabase): ServerTrustDao = db.serverTrustDao()

    @Provides
    @Singleton
    fun provideReadProgressQueueDao(db: KomrdDatabase): ReadProgressQueueDao = db.readProgressQueueDao()

    @Provides
    @Singleton
    fun providePrefetchIndexDao(db: KomrdDatabase): PrefetchIndexDao = db.prefetchIndexDao()

    @Provides
    @Singleton
    fun provideEpubProgressQueueDao(db: KomrdDatabase): EpubProgressQueueDao = db.epubProgressQueueDao()

    @Provides
    @Singleton
    fun provideBookmarkDao(db: KomrdDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    @Singleton
    fun provideSecretCipher(): SecretCipher = KeystoreSecretCipher()
}
