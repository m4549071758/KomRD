package dev.komrd.core.prefetch

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.prefetchContextDataStore by preferencesDataStore(name = "komrd_prefetch_context")

data class PrefetchContext(
    val serverId: String,
    val bookId: String,
    val currentPage: Int,
    val nextBookId: String?,
    val nextBookPagesCount: Int?,
)

/**
 * [PrefetchContext]の永続化抽象。実装=[DataStorePrefetchContextStore]（Preferences DataStore）。
 * DIは [di.PrefetchModule] で @Singleton 提供予定。
 */
interface PrefetchContextStore {
    /** 永続化された実行文脈。未保存時は null。 */
    val prefetchContext: Flow<PrefetchContext?>

    suspend fun save(ctx: PrefetchContext)

    suspend fun clear()
}

/** Preferences DataStoreによる実装。既存DataStoreパターン（[PrefetchSettingsStore]等）に準拠。 */
class DataStorePrefetchContextStore(
    private val context: Context,
) : PrefetchContextStore {
    override val prefetchContext: Flow<PrefetchContext?> =
        context.prefetchContextDataStore.data.map { prefs ->
            val serverId = prefs[KEY_SERVER_ID] ?: return@map null
            val bookId = prefs[KEY_BOOK_ID] ?: return@map null
            PrefetchContext(
                serverId = serverId,
                bookId = bookId,
                currentPage = prefs[KEY_CURRENT_PAGE] ?: 0,
                nextBookId = prefs[KEY_NEXT_BOOK_ID],
                nextBookPagesCount = prefs[KEY_NEXT_BOOK_PAGES_COUNT],
            )
        }

    override suspend fun save(ctx: PrefetchContext) {
        context.prefetchContextDataStore.edit { prefs ->
            prefs[KEY_SERVER_ID] = ctx.serverId
            prefs[KEY_BOOK_ID] = ctx.bookId
            prefs[KEY_CURRENT_PAGE] = ctx.currentPage
            if (ctx.nextBookId != null) {
                prefs[KEY_NEXT_BOOK_ID] = ctx.nextBookId
            } else {
                prefs.remove(KEY_NEXT_BOOK_ID)
            }
            if (ctx.nextBookPagesCount != null) {
                prefs[KEY_NEXT_BOOK_PAGES_COUNT] = ctx.nextBookPagesCount
            } else {
                prefs.remove(KEY_NEXT_BOOK_PAGES_COUNT)
            }
        }
    }

    override suspend fun clear() {
        context.prefetchContextDataStore.edit { it.clear() }
    }

    companion object {
        private val KEY_SERVER_ID = stringPreferencesKey("server_id")
        private val KEY_BOOK_ID = stringPreferencesKey("book_id")
        private val KEY_CURRENT_PAGE = intPreferencesKey("current_page")
        private val KEY_NEXT_BOOK_ID = stringPreferencesKey("next_book_id")
        private val KEY_NEXT_BOOK_PAGES_COUNT = intPreferencesKey("next_book_pages_count")
    }
}
