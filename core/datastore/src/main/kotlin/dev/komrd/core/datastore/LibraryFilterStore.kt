package dev.komrd.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.SeriesSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface LibraryFilterStore {
    /** Library ID → (sort, readStatusFilter) の Flow。Library 未登録時は既定値を返す。 */
    fun filters(libraryId: String): Flow<LibraryFilters>

    suspend fun setSort(
        libraryId: String,
        sort: SeriesSort,
    )

    suspend fun setReadStatusFilter(
        libraryId: String,
        filter: ReadStatusFilter,
    )
}

/** Library ごとのソート/フィルタ。 */
data class LibraryFilters(
    val sort: SeriesSort,
    val readStatusFilter: ReadStatusFilter,
) {
    companion object {
        val DEFAULT = LibraryFilters(SeriesSort.TITLE_ASC, ReadStatusFilter.ALL)
    }
}

/** Preferences DataStore による実装。Library ID ごとにキーを分けて保存する。 */
class DataStoreLibraryFilterStore(
    private val context: Context,
    private val dataStoreName: String = DEFAULT_NAME,
) : LibraryFilterStore {
    private val dataStore: DataStore<Preferences> =
        PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(dataStoreName) })

    override fun filters(libraryId: String): Flow<LibraryFilters> =
        dataStore.data
            .map { prefs ->
                val sort =
                    prefs[sortKey(libraryId)]?.let { name ->
                        SeriesSort.entries.firstOrNull { it.name == name }
                    } ?: SeriesSort.TITLE_ASC
                val filter =
                    prefs[filterKey(libraryId)]?.let { name ->
                        ReadStatusFilter.entries.firstOrNull { it.name == name }
                    } ?: ReadStatusFilter.ALL
                LibraryFilters(sort, filter)
            }.catchDataStoreError(default = LibraryFilters.DEFAULT)

    override suspend fun setSort(
        libraryId: String,
        sort: SeriesSort,
    ) {
        dataStore.edit { prefs -> prefs[sortKey(libraryId)] = sort.name }
    }

    override suspend fun setReadStatusFilter(
        libraryId: String,
        filter: ReadStatusFilter,
    ) {
        dataStore.edit { prefs -> prefs[filterKey(libraryId)] = filter.name }
    }

    private fun sortKey(libraryId: String) = stringPreferencesKey("sort_$libraryId")

    private fun filterKey(libraryId: String) = stringPreferencesKey("filter_$libraryId")

    private companion object {
        const val DEFAULT_NAME = "komrd_library_filter"
    }
}
