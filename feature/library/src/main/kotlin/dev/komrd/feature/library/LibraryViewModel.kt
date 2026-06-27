package dev.komrd.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.data.library.ImageLoaderProvider
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.datastore.LibraryFilterStore
import dev.komrd.core.datastore.LibraryFilters
import dev.komrd.core.model.Library
import dev.komrd.core.model.ReadStatusFilter
import dev.komrd.core.model.Series
import dev.komrd.core.model.SeriesSort
import dev.komrd.core.model.Server
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor(
        private val getServerLibraryGroups: GetServerLibraryGroupsUseCase,
        private val libraryRepository: LibraryRepository,
        private val activeServerStore: ActiveServerStore,
        private val libraryFilterStore: LibraryFilterStore,
        private val imageLoaders: ImageLoaderProvider,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val initialServerId: String? = savedStateHandle.get<String>(KEY_SERVER_ID)
        private val initialLibraryId: String? = savedStateHandle.get<String>(KEY_LIBRARY_ID)
        private var initialArgsApplied = false

        private val selection = MutableStateFlow<LibrarySelection?>(null)

        private val mutableState = MutableStateFlow(LibraryUiState())
        val uiState: StateFlow<LibraryUiState> = mutableState.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val seriesPaging: Flow<PagingData<Series>> =
            selection
                .flatMapLatest { sel ->
                    if (sel == null) {
                        flowOf(PagingData.empty())
                    } else {
                        libraryRepository.seriesPager(
                            server = sel.server,
                            libraryId = sel.libraryId,
                            sort = sel.sort.toApiSort(),
                            readStatusFilter = sel.readStatusFilter,
                        )
                    }
                }.cachedIn(viewModelScope)

        init {
            viewModelScope.launch {
                combine(
                    getServerLibraryGroups(),
                    activeServerStore.activeServerId,
                ) { groups, activeId -> groups to activeId }
                    .collectLatest { (groups, activeId) -> refresh(groups, activeId) }
            }
        }

        private suspend fun refresh(
            groups: List<ServerLibraries>,
            activeId: String?,
        ) {
            if (groups.isEmpty()) {
                selection.value = null
                mutableState.value = LibraryUiState(loading = false, noServer = true)
                return
            }
            val current = mutableState.value
            // 初回のみ遷移引数を優先適用し、適用後は消費してユーザ選択(current)を維持する。
            val effectiveInitialServerId = if (!initialArgsApplied) initialServerId else null
            val effectiveInitialLibraryId = if (!initialArgsApplied) initialLibraryId else null
            val selectedServer =
                groups.firstOrNull { it.server.id == effectiveInitialServerId }?.server
                    ?: groups.firstOrNull { it.server.id == current.selectedServer?.id }?.server
                    ?: groups.firstOrNull { it.server.id == activeId }?.server
                    ?: groups.first().server
            val selectedGroup = groups.first { it.server.id == selectedServer.id }
            val selectedLibrary =
                selectedGroup.libraries.firstOrNull { it.id == effectiveInitialLibraryId }
                    ?: selectedGroup.libraries.firstOrNull { it.id == current.selectedLibrary?.id }
                    ?: selectedGroup.libraries.firstOrNull()

            val restoredFilters =
                if (selectedLibrary != null) {
                    libraryFilterStore.filters(selectedLibrary.id).first()
                } else {
                    LibraryFilters.DEFAULT
                }
            if (selectedLibrary != null) {
                updateSelection(selectedServer, selectedLibrary, restoredFilters.sort, restoredFilters.readStatusFilter)
            } else {
                updateSelection(null, null, SeriesSort.TITLE_ASC, ReadStatusFilter.ALL)
            }
            initialArgsApplied = true
            // 選択Library切替時はユーザが変更したcurrentSort/readStatusFilterを保持し、
            val persistedSort =
                if (current.selectedLibrary?.id == selectedLibrary?.id) {
                    current.currentSort
                } else {
                    restoredFilters.sort
                }
            val persistedFilter =
                if (current.selectedLibrary?.id == selectedLibrary?.id) {
                    current.readStatusFilter
                } else {
                    restoredFilters.readStatusFilter
                }
            mutableState.value =
                LibraryUiState(
                    loading = false,
                    noServer = false,
                    serverGroups = groups,
                    selectedServer = selectedServer,
                    selectedLibrary = selectedLibrary,
                    selectedServerError = selectedGroup.error,
                    currentSort = persistedSort,
                    readStatusFilter = persistedFilter,
                )
        }

        fun onSelectLibrary(
            serverId: String,
            libraryId: String,
        ) {
            val state = mutableState.value
            val group = state.serverGroups.firstOrNull { it.server.id == serverId } ?: return
            val library = group.libraries.firstOrNull { it.id == libraryId } ?: return
            viewModelScope.launch {
                val filters = libraryFilterStore.filters(libraryId).first()
                updateSelection(group.server, library, filters.sort, filters.readStatusFilter)
                mutableState.update {
                    it.copy(
                        selectedServer = group.server,
                        selectedLibrary = library,
                        selectedServerError = group.error,
                        currentSort = filters.sort,
                        readStatusFilter = filters.readStatusFilter,
                    )
                }
                if (serverId != state.selectedServer?.id) {
                    activeServerStore.setActive(serverId)
                }
            }
        }

        fun onSortChanged(sort: SeriesSort) {
            val library = mutableState.value.selectedLibrary ?: return
            viewModelScope.launch {
                libraryFilterStore.setSort(library.id, sort)
                val filters = libraryFilterStore.filters(library.id).first()
                updateSelectionWith(filters)
            }
        }

        fun onFilterChanged(filter: ReadStatusFilter) {
            val library = mutableState.value.selectedLibrary ?: return
            viewModelScope.launch {
                libraryFilterStore.setReadStatusFilter(library.id, filter)
                val filters = libraryFilterStore.filters(library.id).first()
                updateSelectionWith(filters)
            }
        }

        fun onRetry() {
            viewModelScope.launch {
                mutableState.update { it.copy(loading = true) }
                val groups = getServerLibraryGroups().first()
                val activeId = activeServerStore.activeServerId.first()
                refresh(groups, activeId)
            }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaders.forServer(server)

        private fun updateSelectionWith(filters: LibraryFilters) {
            val state = mutableState.value
            val server = state.selectedServer ?: return
            val library = state.selectedLibrary ?: return
            updateSelection(server, library, filters.sort, filters.readStatusFilter)
            mutableState.update {
                it.copy(currentSort = filters.sort, readStatusFilter = filters.readStatusFilter)
            }
        }

        private fun updateSelection(
            server: Server?,
            library: Library?,
            sort: SeriesSort,
            readStatusFilter: ReadStatusFilter,
        ) {
            selection.value =
                if (server != null && library != null) {
                    LibrarySelection(server, library.id, sort, readStatusFilter)
                } else {
                    null
                }
        }

        private companion object {
            const val KEY_SERVER_ID = "serverId"
            const val KEY_LIBRARY_ID = "libraryId"
        }
    }

private data class LibrarySelection(
    val server: Server,
    val libraryId: String,
    val sort: SeriesSort,
    val readStatusFilter: ReadStatusFilter,
)
