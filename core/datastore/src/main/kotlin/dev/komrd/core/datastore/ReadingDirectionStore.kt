package dev.komrd.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.komrd.core.model.ReadingDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ReadingDirectionStore {
    val readingDirection: Flow<ReadingDirection>

    suspend fun set(direction: ReadingDirection)
}

/** Preferences DataStoreによる実装。未設定時は[ReadingDirection.LEFT_TO_RIGHT]。 */
class DataStoreReadingDirectionStore(
    private val context: Context,
    private val dataStoreName: String = DEFAULT_NAME,
) : ReadingDirectionStore {
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(dataStoreName) })

    override val readingDirection: Flow<ReadingDirection> =
        dataStore.data
            .map { prefs ->
                prefs[KEY_READING_DIRECTION]?.let { name ->
                    ReadingDirection.entries.firstOrNull { it.name == name }
                } ?: ReadingDirection.LEFT_TO_RIGHT
            }.catchDataStoreError(default = ReadingDirection.LEFT_TO_RIGHT)

    override suspend fun set(direction: ReadingDirection) {
        dataStore.edit { prefs ->
            prefs[KEY_READING_DIRECTION] = direction.name
        }
    }

    private companion object {
        const val DEFAULT_NAME = "komrd_reading_direction"
        val KEY_READING_DIRECTION = stringPreferencesKey("reading_direction_default")
    }
}
