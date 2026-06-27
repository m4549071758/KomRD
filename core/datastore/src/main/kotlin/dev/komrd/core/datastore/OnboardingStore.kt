package dev.komrd.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface OnboardingStore {
    /** 読書方向グローバル既定の初回選択が完了していればtrue。既定はfalse。 */
    val readingDirectionFirstLaunchDone: Flow<Boolean>

    suspend fun markReadingDirectionFirstLaunchDone()
}

/** Preferences DataStoreによる実装。 */
class DataStoreOnboardingStore(
    private val context: Context,
    private val dataStoreName: String = DEFAULT_NAME,
) : OnboardingStore {
    // 本番はHilt Singletonで1インスタンスのみ生成されるため、ファイル重複は起きない。
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(dataStoreName) })

    override val readingDirectionFirstLaunchDone: Flow<Boolean> =
        dataStore.data
            .map { prefs -> prefs[KEY_FIRST_LAUNCH_DONE] ?: false }
            .catchDataStoreError(default = false)

    override suspend fun markReadingDirectionFirstLaunchDone() {
        dataStore.edit { prefs -> prefs[KEY_FIRST_LAUNCH_DONE] = true }
    }

    private companion object {
        const val DEFAULT_NAME = "komrd_onboarding"
        val KEY_FIRST_LAUNCH_DONE = booleanPreferencesKey("reading_direction_first_launch_done")
    }
}
