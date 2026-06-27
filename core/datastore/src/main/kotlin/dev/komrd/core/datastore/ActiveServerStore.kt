package dev.komrd.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ActiveServerStore {
    val activeServerId: Flow<String?>

    suspend fun setActive(id: String)

    suspend fun clear()
}

/** Preferences DataStoreによる実装。 */
class DataStoreActiveServerStore(
    private val context: Context,
    private val dataStoreName: String = DEFAULT_NAME,
) : ActiveServerStore {
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(dataStoreName) })

    override val activeServerId: Flow<String?> =
        dataStore.data
            .map { prefs -> prefs[KEY_ACTIVE_SERVER_ID] }
            .catchDataStoreError(default = null)

    override suspend fun setActive(id: String) {
        dataStore.edit { prefs -> prefs[KEY_ACTIVE_SERVER_ID] = id }
    }

    override suspend fun clear() {
        dataStore.edit { prefs -> prefs.remove(KEY_ACTIVE_SERVER_ID) }
    }

    private companion object {
        const val DEFAULT_NAME = "komrd_active_server"
        val KEY_ACTIVE_SERVER_ID = stringPreferencesKey("active_server_id")
    }
}
