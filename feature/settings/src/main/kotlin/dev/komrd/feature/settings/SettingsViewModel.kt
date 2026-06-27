package dev.komrd.feature.settings

import android.Manifest
import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.os.PowerManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.komrd.core.data.prefetch.PrefetchCacheRepository
import dev.komrd.core.datastore.DataStorePrefetchSettingsStore
import dev.komrd.core.datastore.PrefetchSettingsStore
import dev.komrd.core.datastore.ReadingDirectionStore
import dev.komrd.core.datastore.SpreadModeStore
import dev.komrd.core.model.PrefetchCacheSummary
import dev.komrd.core.model.ReadingDirection
import dev.komrd.core.model.SpreadMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class SettingsViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val readingDirectionStore: ReadingDirectionStore,
        private val spreadModeStore: SpreadModeStore,
        private val prefetchSettingsStore: PrefetchSettingsStore,
        private val prefetchCacheRepository: PrefetchCacheRepository,
    ) : ViewModel() {
        val readingDirection: StateFlow<ReadingDirection> =
            readingDirectionStore.readingDirection
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = ReadingDirection.LEFT_TO_RIGHT,
                )

        val spreadMode: StateFlow<SpreadMode> =
            spreadModeStore.spreadMode
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = SpreadMode.LANDSCAPE_ONLY,
                )

        // ── 先読み設定 ──

        val prefetchEnabled: StateFlow<Boolean> =
            prefetchSettingsStore.enabled
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DataStorePrefetchSettingsStore.DEFAULT_ENABLED,
                )

        val prefetchNextBooks: StateFlow<Int> =
            prefetchSettingsStore.nextBooks
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DataStorePrefetchSettingsStore.DEFAULT_NEXT_BOOKS,
                )

        val prefetchParallelism: StateFlow<Int> =
            prefetchSettingsStore.parallelism
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DataStorePrefetchSettingsStore.DEFAULT_PARALLELISM,
                )

        val prefetchRetentionDays: StateFlow<Int> =
            prefetchSettingsStore.retentionDays
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DataStorePrefetchSettingsStore.DEFAULT_RETENTION_DAYS,
                )

        val prefetchMaxBytes: StateFlow<Long> =
            prefetchSettingsStore.maxBytes
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DataStorePrefetchSettingsStore.DEFAULT_MAX_BYTES,
                )

        val prefetchAllowOnMobile: StateFlow<Boolean> =
            prefetchSettingsStore.allowOnMobile
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.Eagerly,
                    initialValue = DataStorePrefetchSettingsStore.DEFAULT_ALLOW_ON_MOBILE,
                )

        // ── キャッシュ ──

        val cacheSummaries: StateFlow<List<PrefetchCacheSummary>> =
            prefetchCacheRepository
                .summaries()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = emptyList(),
                )

        fun purgeCache(
            serverId: String,
            bookId: String,
        ) {
            viewModelScope.launch { prefetchCacheRepository.purge(serverId, bookId) }
        }

        // ── M4: システム状態（DataStore不要・コンテキスト由来） ──

        private val _notificationsGranted = MutableStateFlow(notificationsGrantedNow())
        val notificationsGranted: StateFlow<Boolean> = _notificationsGranted.asStateFlow()

        private val _batteryOptimizationIgnored = MutableStateFlow(batteryOptimizationIgnoredNow())
        val batteryOptimizationIgnored: StateFlow<Boolean> = _batteryOptimizationIgnored.asStateFlow()

        /** 設定画面表示時・権限ダイアログから復帰時に呼び、システム状態を再取得する。 */
        fun refreshSystemState() {
            _notificationsGranted.value = notificationsGrantedNow()
            _batteryOptimizationIgnored.value = batteryOptimizationIgnoredNow()
        }

        private fun notificationsGrantedNow(): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                    PackageManager.PERMISSION_GRANTED
            } else {
                // API32-は実行時許可不要で通知可能。
                true
            }

        private fun batteryOptimizationIgnoredNow(): Boolean {
            val pm = context.getSystemService(PowerManager::class.java) ?: return false
            return pm.isIgnoringBatteryOptimizations(context.packageName)
        }

        // ── 言語 ──

        private val _currentLocale = MutableStateFlow(currentLocaleTag())
        val currentLocale: StateFlow<String> = _currentLocale.asStateFlow()

        fun setAppLocale(tag: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val localeManager = context.getSystemService(LocaleManager::class.java)
                localeManager.applicationLocales =
                    if (tag == LOCALE_SYSTEM) {
                        LocaleList.getEmptyLocaleList()
                    } else {
                        LocaleList.forLanguageTags(tag)
                    }
            } else {
                context
                    .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(LOCALE_KEY, tag)
                    .apply()
            }
            _currentLocale.value = tag
        }

        private fun currentLocaleTag(): String =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
                if (locales.isEmpty) LOCALE_SYSTEM else locales[0]?.language ?: LOCALE_SYSTEM
            } else {
                context
                    .getSharedPreferences(LOCALE_PREFS, Context.MODE_PRIVATE)
                    .getString(LOCALE_KEY, LOCALE_SYSTEM) ?: LOCALE_SYSTEM
            }

        fun setReadingDirection(direction: ReadingDirection) {
            viewModelScope.launch { readingDirectionStore.set(direction) }
        }

        fun setSpreadMode(mode: SpreadMode) {
            viewModelScope.launch { spreadModeStore.set(mode) }
        }

        fun setPrefetchEnabled(enabled: Boolean) {
            viewModelScope.launch { prefetchSettingsStore.setEnabled(enabled) }
        }

        fun setPrefetchNextBooks(count: Int) {
            viewModelScope.launch { prefetchSettingsStore.setNextBooks(count) }
        }

        fun setPrefetchParallelism(count: Int) {
            viewModelScope.launch { prefetchSettingsStore.setParallelism(count) }
        }

        fun setPrefetchRetentionDays(days: Int) {
            viewModelScope.launch { prefetchSettingsStore.setRetentionDays(days) }
        }

        fun setPrefetchMaxBytes(bytes: Long) {
            viewModelScope.launch { prefetchSettingsStore.setMaxBytes(bytes) }
        }

        fun setPrefetchAllowOnMobile(allow: Boolean) {
            viewModelScope.launch { prefetchSettingsStore.setAllowOnMobile(allow) }
        }

        companion object {
            const val LOCALE_PREFS = "locale"
            const val LOCALE_KEY = "app_locale"
            const val LOCALE_SYSTEM = "system"
        }
    }
