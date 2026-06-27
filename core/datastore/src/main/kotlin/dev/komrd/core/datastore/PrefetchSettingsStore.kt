package dev.komrd.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefetchSettingsDataStore by preferencesDataStore(name = "komrd_prefetch_settings")

interface PrefetchSettingsStore {
    /** 先読みON/OFF（既定: ON）。OFFならPrefetch Controllerを起動しない。 */
    val enabled: Flow<Boolean>

    /** 先読みブック数（既定: 1冊）。0=現在Bookのみ、1以上=次N冊も含む（現状は0 or 1）。 */
    val nextBooks: Flow<Int>
    val parallelism: Flow<Int>
    val retentionDays: Flow<Int>
    val maxBytes: Flow<Long>
    val allowOnMobile: Flow<Boolean>

    suspend fun setEnabled(enabled: Boolean)

    suspend fun setNextBooks(count: Int)

    suspend fun setParallelism(count: Int)

    suspend fun setRetentionDays(days: Int)

    suspend fun setMaxBytes(bytes: Long)

    suspend fun setAllowOnMobile(allow: Boolean)
}

class DataStorePrefetchSettingsStore(
    private val context: Context,
) : PrefetchSettingsStore {
    override val enabled: Flow<Boolean> =
        context.prefetchSettingsDataStore.data
            .map { it[KEY_ENABLED] ?: DEFAULT_ENABLED }
            .catchDataStoreError(default = DEFAULT_ENABLED)

    override val nextBooks: Flow<Int> =
        context.prefetchSettingsDataStore.data
            .map { it[KEY_NEXT_BOOKS] ?: DEFAULT_NEXT_BOOKS }
            .catchDataStoreError(default = DEFAULT_NEXT_BOOKS)

    override val parallelism: Flow<Int> =
        context.prefetchSettingsDataStore.data
            .map { it[KEY_PARALLELISM] ?: DEFAULT_PARALLELISM }
            .catchDataStoreError(default = DEFAULT_PARALLELISM)

    override val retentionDays: Flow<Int> =
        context.prefetchSettingsDataStore.data
            .map { it[KEY_RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS }
            .catchDataStoreError(default = DEFAULT_RETENTION_DAYS)

    override val maxBytes: Flow<Long> =
        context.prefetchSettingsDataStore.data
            .map { it[KEY_MAX_BYTES] ?: DEFAULT_MAX_BYTES }
            .catchDataStoreError(default = DEFAULT_MAX_BYTES)

    override val allowOnMobile: Flow<Boolean> =
        context.prefetchSettingsDataStore.data
            .map { it[KEY_ALLOW_ON_MOBILE] ?: DEFAULT_ALLOW_ON_MOBILE }
            .catchDataStoreError(default = DEFAULT_ALLOW_ON_MOBILE)

    override suspend fun setEnabled(enabled: Boolean) {
        context.prefetchSettingsDataStore.edit { it[KEY_ENABLED] = enabled }
    }

    override suspend fun setNextBooks(count: Int) {
        context.prefetchSettingsDataStore.edit {
            it[KEY_NEXT_BOOKS] = sanitize("nextBooks", count, MIN_NEXT_BOOKS, MAX_NEXT_BOOKS)
        }
    }

    override suspend fun setParallelism(count: Int) {
        context.prefetchSettingsDataStore.edit {
            it[KEY_PARALLELISM] = sanitize("parallelism", count, MIN_PARALLELISM, MAX_PARALLELISM)
        }
    }

    override suspend fun setRetentionDays(days: Int) {
        context.prefetchSettingsDataStore.edit {
            it[KEY_RETENTION_DAYS] = sanitize("retentionDays", days, MIN_RETENTION_DAYS, MAX_RETENTION_DAYS)
        }
    }

    override suspend fun setMaxBytes(bytes: Long) {
        context.prefetchSettingsDataStore.edit {
            it[KEY_MAX_BYTES] = sanitize("maxBytes", bytes, MIN_MAX_BYTES, Long.MAX_VALUE)
        }
    }

    override suspend fun setAllowOnMobile(allow: Boolean) {
        context.prefetchSettingsDataStore.edit { it[KEY_ALLOW_ON_MOBILE] = allow }
    }

    companion object {
        const val DEFAULT_ENABLED: Boolean = true
        const val DEFAULT_NEXT_BOOKS: Int = 1
        const val DEFAULT_PARALLELISM: Int = 2
        const val DEFAULT_RETENTION_DAYS: Int = 3
        const val DEFAULT_MAX_BYTES: Long = 2L * 1024 * 1024 * 1024 // 2GB
        const val DEFAULT_ALLOW_ON_MOBILE: Boolean = true

        // UI選択肢(nextBooks 0..3 / parallelism 1..4 / retentionDays 1/3/7/14 / maxBytes 512MB..8GB)
        const val MIN_NEXT_BOOKS: Int = 0
        const val MAX_NEXT_BOOKS: Int = 10
        const val MIN_PARALLELISM: Int = 1
        const val MAX_PARALLELISM: Int = 16
        const val MIN_RETENTION_DAYS: Int = 1
        const val MAX_RETENTION_DAYS: Int = 365
        const val MIN_MAX_BYTES: Long = 256L * 1024 * 1024 // 256MB

        private val KEY_ENABLED = booleanPreferencesKey("prefetch_enabled")
        private val KEY_NEXT_BOOKS = intPreferencesKey("prefetch_next_books")
        private val KEY_PARALLELISM = intPreferencesKey("prefetch_parallelism")
        private val KEY_RETENTION_DAYS = intPreferencesKey("prefetch_retention_days")
        private val KEY_MAX_BYTES = longPreferencesKey("prefetch_max_bytes")
        private val KEY_ALLOW_ON_MOBILE = booleanPreferencesKey("prefetch_allow_on_mobile")
    }
}
