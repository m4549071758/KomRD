package dev.komrd.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.library.ImageLoaderProvider
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.data.search.SearchRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.model.Book
import dev.komrd.core.model.Library
import dev.komrd.core.model.Series
import dev.komrd.core.model.Server
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SearchTab { SERIES, BOOKS }

sealed interface SearchScope {
    /** 選択中サーバ内。`libraryId`が非nullならそのLibraryに絞り込み。 */
    data class SingleServer(
        val server: Server,
        val libraryId: String?,
    ) : SearchScope

    /** 全サーバ横断（各サーバへ並列クエリ→結果をフラットマージ）。 */
    data object GlobalAllServers : SearchScope
}

data class SearchUiState(
    val loading: Boolean = true,
    val servers: List<Server> = emptyList(),
    val activeServerId: String? = null,
    val query: String = "",
    val selectedTab: SearchTab = SearchTab.SERIES,
    val libraries: List<Library> = emptyList(),
    val selectedLibraryId: String? = null,
    val globalAllServers: Boolean = false,
) {
    val activeServer: Server?
        get() = servers.firstOrNull { it.id == activeServerId } ?: servers.firstOrNull()
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
@Suppress("TooManyFunctions") // 検索タブの状態管理+Paging生成+スコープ切替で関数が増えるため許容
class SearchViewModel
    @Inject
    constructor(
        serverRepository: ServerRepository,
        private val activeServerStore: ActiveServerStore,
        private val libraryRepository: LibraryRepository,
        private val searchRepository: SearchRepository,
        private val imageLoaders: ImageLoaderProvider,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = mutableState.asStateFlow()

        /** サーバ一覧 + activeServerId を購読し、状態へ反映。 */
        private val serverList: StateFlow<List<Server>> =
            combine(serverRepository.servers, activeServerStore.activeServerId) { servers, activeId ->
                mutableState.update {
                    it.copy(
                        loading = false,
                        servers = servers,
                        activeServerId = activeId,
                    )
                }
                servers
            }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        /** 検索クエリ（300ms debounce）。空文字は結果なし。 */
        private val debouncedQuery: StateFlow<String> =
            mutableState
                .map { it.query }
                .debounce(QUERY_DEBOUNCE_MS)
                .distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Eagerly, "")

        /** Series検索のPagingData。スコープ+タブ+クエリに追従。 */
        val seriesPaging: StateFlow<PagingData<Series>> =
            combine(mutableState, debouncedQuery) { s, q -> s to q }
                .flatMapLatest { (s, q) ->
                    if (q.isBlank()) {
                        flowOf(PagingData.empty())
                    } else {
                        seriesPagingFor(s, q)
                    }
                }.cachedIn(viewModelScope)
                .stateIn(viewModelScope, SharingStarted.Eagerly, PagingData.empty())

        /** Books検索のPagingData。スコープ+タブ+クエリに追従。 */
        val booksPaging: StateFlow<PagingData<Book>> =
            combine(mutableState, debouncedQuery) { s, q -> s to q }
                .flatMapLatest { (s, q) ->
                    if (q.isBlank()) {
                        flowOf(PagingData.empty())
                    } else {
                        booksPagingFor(s, q)
                    }
                }.cachedIn(viewModelScope)
                .stateIn(viewModelScope, SharingStarted.Eagerly, PagingData.empty())

        init {
            // serverListの購読を起動（stateIn Eagerlyのため起動済みだが明示的に参照してトラガー）。
            serverList.value
            viewModelScope.launch {
                serverList.collect { servers ->
                    val active = mutableState.value.activeServer ?: servers.firstOrNull() ?: return@collect
                    loadLibraries(active)
                }
            }
        }

        private suspend fun loadLibraries(server: Server) {
            when (val result = libraryRepository.libraries(server)) {
                is KomgaResult.Success -> mutableState.update { it.copy(libraries = result.value) }
                is KomgaResult.Failure -> mutableState.update { it.copy(libraries = emptyList()) }
            }
        }

        /**
         * スコープに応じたSeries Pager を生成。
         *
         * GlobalAllServersはKomgaがサーバ間横断検索不可のため、PagingDataの本格マージは今後課題。
         * 現状は「全サーバの先頭サーバ(=active)で全Library横断」として動作し、UIで全サーバ選択を示す。
         * Library絞り込み([scope.libraryId])は完全対応。
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun seriesPagingFor(
            state: SearchUiState,
            query: String,
        ): Flow<PagingData<Series>> {
            val scope = scopeOf(state)
            return when (scope) {
                is SearchScope.SingleServer ->
                    searchRepository.searchSeriesPager(scope.server, query, scope.libraryId)
                SearchScope.GlobalAllServers -> globalSeriesPager(state, query)
            }
        }

        /** GlobalAllServersのSeries Pager。全サーバPagingDataマージは今後拡張し、現状はactiveサーバの全Library横断。 */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun globalSeriesPager(
            state: SearchUiState,
            query: String,
        ): Flow<PagingData<Series>> {
            val server = state.activeServer ?: state.servers.firstOrNull()
            return if (server == null) {
                flowOf(PagingData.empty())
            } else {
                searchRepository.searchSeriesPager(server, query, null)
            }
        }

        /** スコープに応じたBook Pager を生成。Seriesと同様にGlobalは今後拡張。 */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun booksPagingFor(
            state: SearchUiState,
            query: String,
        ): Flow<PagingData<Book>> {
            val scope = scopeOf(state)
            return when (scope) {
                is SearchScope.SingleServer ->
                    searchRepository.searchBooksPager(scope.server, query, scope.libraryId)
                SearchScope.GlobalAllServers -> globalBookPager(state, query)
            }
        }

        /** GlobalAllServersのBook Pager。全サーバPagingDataマージは今後拡張し、現状はactiveサーバの全Library横断。 */
        @OptIn(ExperimentalCoroutinesApi::class)
        private fun globalBookPager(
            state: SearchUiState,
            query: String,
        ): Flow<PagingData<Book>> {
            val server = state.activeServer ?: state.servers.firstOrNull()
            return if (server == null) {
                flowOf(PagingData.empty())
            } else {
                searchRepository.searchBooksPager(server, query, null)
            }
        }

        /** 現状態からSearchScopeを導出。 */
        private fun scopeOf(state: SearchUiState): SearchScope =
            if (state.globalAllServers) {
                SearchScope.GlobalAllServers
            } else {
                val server = state.activeServer ?: state.servers.firstOrNull()
                if (server == null) {
                    SearchScope.GlobalAllServers
                } else {
                    SearchScope.SingleServer(server, state.selectedLibraryId)
                }
            }

        fun onQueryChanged(query: String) {
            mutableState.update { it.copy(query = query) }
        }

        fun onTabSelected(tab: SearchTab) {
            mutableState.update { it.copy(selectedTab = tab) }
        }

        fun onSelectServer(id: String) {
            viewModelScope.launch {
                activeServerStore.setActive(id)
                mutableState.update { it.copy(selectedLibraryId = null) }
            }
        }

        fun onSelectLibrary(libraryId: String?) {
            mutableState.update { it.copy(selectedLibraryId = libraryId) }
        }

        fun onToggleGlobalAllServers(enabled: Boolean) {
            mutableState.update { it.copy(globalAllServers = enabled) }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaders.forServer(server)

        private companion object {
            const val QUERY_DEBOUNCE_MS = 300L
        }
    }
