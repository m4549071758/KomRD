package dev.komrd.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.komrd.core.model.SpreadMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface SpreadModeStore {
    val spreadMode: Flow<SpreadMode>

    suspend fun set(mode: SpreadMode)
}

/** Preferences DataStoreによる実装。未設定時は[SpreadMode.LANDSCAPE_ONLY]。 */
class DataStoreSpreadModeStore(
    private val context: Context,
    private val dataStoreName: String = DEFAULT_NAME,
) : SpreadModeStore {
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(dataStoreName) })

    override val spreadMode: Flow<SpreadMode> =
        dataStore.data
            .map { prefs ->
                prefs[KEY_SPREAD_MODE]?.let { name ->
                    SpreadMode.entries.firstOrNull { it.name == name }
                } ?: SpreadMode.LANDSCAPE_ONLY
            }.catchDataStoreError(default = SpreadMode.LANDSCAPE_ONLY)

    override suspend fun set(mode: SpreadMode) {
        dataStore.edit { prefs ->
            prefs[KEY_SPREAD_MODE] = mode.name
        }
    }

    private companion object {
        const val DEFAULT_NAME = "komrd_spread_mode"
        val KEY_SPREAD_MODE = stringPreferencesKey("spread_mode_default")
    }
}
