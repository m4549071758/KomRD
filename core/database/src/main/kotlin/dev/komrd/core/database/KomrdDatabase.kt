package dev.komrd.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.komrd.core.database.dao.BookmarkDao
import dev.komrd.core.database.dao.EpubProgressQueueDao
import dev.komrd.core.database.dao.PrefetchIndexDao
import dev.komrd.core.database.dao.ReadProgressQueueDao
import dev.komrd.core.database.dao.ServerTrustDao
import dev.komrd.core.database.dao.ServersDao
import dev.komrd.core.database.entity.BookmarkEntity
import dev.komrd.core.database.entity.EpubProgressQueueEntity
import dev.komrd.core.database.entity.PrefetchIndexEntity
import dev.komrd.core.database.entity.ReadProgressQueueEntity
import dev.komrd.core.database.entity.ServerEntity
import dev.komrd.core.database.entity.ServerTrustEntity

@Database(
    entities = [
        ServerEntity::class,
        ServerTrustEntity::class,
        ReadProgressQueueEntity::class,
        PrefetchIndexEntity::class,
        EpubProgressQueueEntity::class,
        BookmarkEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class KomrdDatabase : RoomDatabase() {
    abstract fun serversDao(): ServersDao

    abstract fun serverTrustDao(): ServerTrustDao

    abstract fun readProgressQueueDao(): ReadProgressQueueDao

    abstract fun prefetchIndexDao(): PrefetchIndexDao

    abstract fun epubProgressQueueDao(): EpubProgressQueueDao

    abstract fun bookmarkDao(): BookmarkDao
}

/**
 * v1→v2移行: server_trustテーブルを生成。Room生成SQLと完全一致させること（MigrationTestHelperでHash検証）。
 */
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `server_trust` (
                    `serverId` TEXT NOT NULL,
                    `pinnedFingerprintsJson` TEXT NOT NULL,
                    `customCaCertsPem` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`),
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
val MIGRATION_2_3: Migration =
    object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `read_progress_queue` (
                    `serverId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `page` INTEGER NOT NULL,
                    `completed` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `bookId`),
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
val MIGRATION_3_4: Migration =
    object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `prefetch_index` (
                    `serverId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `pageNumber` INTEGER NOT NULL,
                    `variant` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    `lastAccessedAt` INTEGER NOT NULL,
                    `etag` TEXT,
                    PRIMARY KEY(`serverId`, `bookId`, `pageNumber`, `variant`),
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
val MIGRATION_4_5: Migration =
    object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_servers_createdAt` ON `servers` (`createdAt`)",
            )
        }
    }
val MIGRATION_5_6: Migration =
    object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_prefetch_index_lastAccessedAt`" +
                    " ON `prefetch_index` (`lastAccessedAt`)",
            )
        }
    }
val MIGRATION_6_7: Migration =
    object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `prefetch_index_new` (
                    `serverId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `pageNumber` INTEGER,
                    `variant` TEXT NOT NULL,
                    `resourcePath` TEXT NOT NULL,
                    `resourceKind` TEXT NOT NULL,
                    `filePath` TEXT NOT NULL,
                    `sizeBytes` INTEGER NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    `lastAccessedAt` INTEGER NOT NULL,
                    `etag` TEXT,
                    PRIMARY KEY(`serverId`, `bookId`, `resourcePath`, `variant`),
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO prefetch_index_new(
                    serverId, bookId, pageNumber, variant, resourcePath, resourceKind,
                    filePath, sizeBytes, fetchedAt, lastAccessedAt, etag
                )
                SELECT
                    serverId, bookId, pageNumber, variant,
                    CAST(pageNumber AS TEXT), 'PAGE',
                    filePath, sizeBytes, fetchedAt, lastAccessedAt, etag
                FROM prefetch_index
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE IF EXISTS `prefetch_index`")
            db.execSQL("ALTER TABLE `prefetch_index_new` RENAME TO `prefetch_index`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_prefetch_index_lastAccessedAt`" +
                    " ON `prefetch_index` (`lastAccessedAt`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_prefetch_index_resourceKind`" +
                    " ON `prefetch_index` (`resourceKind`)",
            )
        }
    }
val MIGRATION_7_8: Migration =
    object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `epub_progress_queue` (
                    `serverId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `locatorJson` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `bookId`),
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
val MIGRATION_8_9: Migration =
    object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `bookmarks` (
                    `serverId` TEXT NOT NULL,
                    `bookId` TEXT NOT NULL,
                    `pageNumber` INTEGER NOT NULL,
                    `note` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `bookId`, `pageNumber`),
                    FOREIGN KEY(`serverId`) REFERENCES `servers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
        }
    }
