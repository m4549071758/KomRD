package dev.komrd.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.data.library.ImageLoaderProvider
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.model.Book
import dev.komrd.core.model.Server
import dev.komrd.feature.library.GetServerLibraryGroupsUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal object ReadStatus {
    const val IN_PROGRESS = "IN_PROGRESS"
    const val READ = "READ"
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val libraryRepository: LibraryRepository,
        private val activeServerStore: ActiveServerStore,
        private val imageLoaderProvider: ImageLoaderProvider,
        private val getServerLibraryGroups: GetServerLibraryGroupsUseCase,
    ) : ViewModel() {
        private val selection = MutableStateFlow<Server?>(null)

        private val mutableState = MutableStateFlow(HomeUiState())
        val uiState: StateFlow<HomeUiState> = mutableState.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val inProgressPaging: Flow<PagingData<Book>> =
            selection
                .flatMapLatest { server ->
                    if (server == null) {
                        flowOf(PagingData.empty())
                    } else {
                        libraryRepository.readStatusBooksPager(server, ReadStatus.IN_PROGRESS)
                    }
                }.cachedIn(viewModelScope)

        @OptIn(ExperimentalCoroutinesApi::class)
        val readPaging: Flow<PagingData<Book>> =
            selection
                .flatMapLatest { server ->
                    if (server == null) {
                        flowOf(PagingData.empty())
                    } else {
                        libraryRepository.readStatusBooksPager(server, ReadStatus.READ)
                    }
                }.cachedIn(viewModelScope)

        init {
            viewModelScope.launch {
                combine(serverRepository.servers, activeServerStore.activeServerId) { servers, activeId ->
                    servers to activeId
                }.collectLatest { (servers, activeId) -> refresh(servers, activeId) }
            }
            // refresh とは独立に更新し、serverGroups は mutableState.copy で保持する。
            viewModelScope.launch {
                getServerLibraryGroups().collectLatest { groups ->
                    mutableState.update { it.copy(serverGroups = groups) }
                }
            }
        }

        private suspend fun refresh(
            servers: List<Server>,
            activeId: String?,
        ) {
            if (servers.isEmpty()) {
                selection.value = null
                mutableState.update {
                    it.copy(loading = false, noServer = true, servers = emptyList(), selectedServer = null)
                }
                return
            }
            val current = mutableState.value
            val selected =
                servers.firstOrNull { it.id == current.selectedServer?.id }
                    ?: servers.firstOrNull { it.id == activeId }
                    ?: servers.first()
            selection.value = selected
            mutableState.update {
                it.copy(loading = false, noServer = false, servers = servers, selectedServer = selected)
            }
        }

        /** サーバセレクタからの切替。選択サーバを有効化する。 */
        fun onSelectServer(serverId: String) {
            val state = mutableState.value
            val server = state.servers.firstOrNull { it.id == serverId } ?: return
            selection.value = server
            mutableState.update { it.copy(selectedServer = server) }
            if (serverId != state.selectedServer?.id) {
                viewModelScope.launch { activeServerStore.setActive(serverId) }
            }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaderProvider.forServer(server)
    }
