package dev.komrd.core.database

import androidx.room.migration.AutoMigrationSpec
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1→v2移行を検証する。手書きMIGRATION_1_2のCREATE TABLEがRoom生成SQLと一致しないと
 * runMigrationsAndValidateでHash不一致例外が発生する（計画リスク2）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerTrustMigrationTest {
    @get:Rule
    val helper =
        MigrationTestHelper(
            instrumentation = InstrumentationRegistry.getInstrumentation(),
            databaseClass = KomrdDatabase::class.java,
            specs = emptyList<AutoMigrationSpec>(),
            openFactory = FrameworkSQLiteOpenHelperFactory(),
        )

    @Test
    fun migrate1To2_preservesServersAndCreatesServerTrustTable() {
        val dbName = "migration_test"
        val oldDb = helper.createDatabase(dbName, 1)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 2, true, MIGRATION_1_2)
        migratedDb.query("SELECT id FROM servers").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM server_trust").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun migrate2To3_preservesServersAndCreatesReadProgressQueueTable() {
        val dbName = "migration_test_2_3"
        // v2状態を構築(サーバ1件)
        val oldDb = helper.createDatabase(dbName, 2)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)
        migratedDb.query("SELECT id FROM servers").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM read_progress_queue").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun migrate3To4_preservesServersAndCreatesPrefetchIndexTable() {
        val dbName = "migration_test_3_4"
        // v3状態を構築(サーバ1件 + 進捗キュー1件)
        val oldDb = helper.createDatabase(dbName, 3)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.execSQL(
            """
            INSERT INTO read_progress_queue (serverId, bookId, page, completed, updatedAt)
            VALUES ('s1', 'b1', 5, 0, 100)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)
        migratedDb.query("SELECT id FROM servers").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
        migratedDb.query("SELECT page FROM read_progress_queue WHERE serverId='s1' AND bookId='b1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(5, cursor.getInt(0))
        }
        migratedDb.query("SELECT COUNT(*) FROM prefetch_index").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun migrate4To5_createsServersCreatedAtIndex() {
        val dbName = "migration_test_4_5"
        // v4状態を構築(サーバ1件 + prefetch_index 1件)
        val oldDb = helper.createDatabase(dbName, 4)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.execSQL(
            """
            INSERT INTO prefetch_index (serverId, bookId, pageNumber, variant, filePath, sizeBytes, fetchedAt, lastAccessedAt, etag)
            VALUES ('s1', 'b1', 1, 'full', '/tmp/p', 1024, 100, 200, NULL)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 5, true, MIGRATION_4_5)
        migratedDb
            .query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_servers_createdAt'")
            .use { cursor -> assertTrue("index_servers_createdAt should exist", cursor.moveToFirst()) }
        migratedDb.close()
    }

    @Test
    fun migrate5To6_createsPrefetchIndexLastAccessedAtIndex() {
        val dbName = "migration_test_5_6"
        // v5状態を構築(サーバ1件 + prefetch_index 1件)
        val oldDb = helper.createDatabase(dbName, 5)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.execSQL(
            """
            INSERT INTO prefetch_index (serverId, bookId, pageNumber, variant, filePath, sizeBytes, fetchedAt, lastAccessedAt, etag)
            VALUES ('s1', 'b1', 1, 'full', '/tmp/p', 1024, 100, 200, NULL)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 6, true, MIGRATION_5_6)
        migratedDb
            .query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_prefetch_index_lastAccessedAt'")
            .use { cursor -> assertTrue("index_prefetch_index_lastAccessedAt should exist", cursor.moveToFirst()) }
        // データが保全されていること。
        migratedDb
            .query(
                "SELECT lastAccessedAt FROM prefetch_index" +
                    " WHERE serverId='s1' AND bookId='b1' AND pageNumber=1 AND variant='full'",
            ).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(200, cursor.getLong(0))
            }
        migratedDb.close()
    }

    @Test
    fun migrate6To7_preservesPrefetchIndexAndMigratesToGenericKeySchema() {
        val dbName = "migration_test_6_7"
        // v6状態を構築(サーバ1件 + prefetch_index 1件・pageNumberベース)
        val oldDb = helper.createDatabase(dbName, 6)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.execSQL(
            """
            INSERT INTO prefetch_index (serverId, bookId, pageNumber, variant, filePath, sizeBytes, fetchedAt, lastAccessedAt, etag)
            VALUES ('s1', 'b1', 1, 'full', '/tmp/p', 1024, 100, 200, NULL)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 7, true, MIGRATION_6_7)
        // 画像系既存データが resourcePath=pageNumber文字列・resourceKind=PAGE でコピーされていること。
        migratedDb
            .query(
                "SELECT resourcePath, resourceKind, pageNumber, sizeBytes FROM prefetch_index" +
                    " WHERE serverId='s1' AND bookId='b1' AND resourcePath='1' AND variant='full'",
            ).use { cursor ->
                assertTrue("migrated row should exist", cursor.moveToFirst())
                assertEquals("1", cursor.getString(0))
                assertEquals("PAGE", cursor.getString(1))
                assertEquals(1, cursor.getInt(2))
                assertEquals(1024L, cursor.getLong(3))
            }
        // インデックスが再構築されていること(Room生成SQLと一致・Hash検証済)。
        migratedDb
            .query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_prefetch_index_lastAccessedAt'")
            .use { cursor -> assertTrue("index_prefetch_index_lastAccessedAt should exist", cursor.moveToFirst()) }
        migratedDb
            .query("SELECT name FROM sqlite_master WHERE type='index' AND name='index_prefetch_index_resourceKind'")
            .use { cursor -> assertTrue("index_prefetch_index_resourceKind should exist", cursor.moveToFirst()) }
        migratedDb.close()
    }

    @Test
    fun migrate7To8_preservesServersAndCreatesEpubProgressQueueTable() {
        val dbName = "migration_test_7_8"
        val oldDb = helper.createDatabase(dbName, 7)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.execSQL(
            """
            INSERT INTO prefetch_index (serverId, bookId, pageNumber, variant, resourcePath, resourceKind, filePath, sizeBytes, fetchedAt, lastAccessedAt, etag)
            VALUES ('s1', 'b1', 1, 'full', '1', 'PAGE', '/tmp/p', 1024, 100, 200, NULL)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 8, true, MIGRATION_7_8)
        // serversは保全
        migratedDb.query("SELECT id FROM servers").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
        // prefetch_indexも保全(本移行では新テーブルCREATEのみ・既存テーブル無変更)
        migratedDb
            .query("SELECT resourcePath FROM prefetch_index WHERE serverId='s1' AND bookId='b1' AND resourcePath='1'")
            .use { cursor -> assertTrue(cursor.moveToFirst()) }
        // epub_progress_queueが生成されていること(空)
        migratedDb.query("SELECT COUNT(*) FROM epub_progress_queue").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }

    @Test
    fun migrate8To9_preservesServersAndCreatesBookmarksTable() {
        val dbName = "migration_test_8_9"
        // v8状態を構築(サーバ1件)
        val oldDb = helper.createDatabase(dbName, 8)
        oldDb.execSQL(
            """
            INSERT INTO servers (id, name, baseUrl, authType, username, secretCiphertext, secretIv, createdAt)
            VALUES ('s1', 'Home', 'https://k.example', 'API_KEY', NULL, X'010203', X'09', 1)
            """.trimIndent(),
        )
        oldDb.close()

        val migratedDb = helper.runMigrationsAndValidate(dbName, 9, true, MIGRATION_8_9)
        // serversは保全
        migratedDb.query("SELECT id FROM servers").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("s1", cursor.getString(0))
        }
        // bookmarksが生成されていること(空)
        migratedDb.query("SELECT COUNT(*) FROM bookmarks").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        migratedDb.close()
    }
}
