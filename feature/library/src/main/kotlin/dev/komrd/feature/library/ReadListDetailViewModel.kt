package dev.komrd.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.data.library.KomgaImageLoaders
import dev.komrd.core.data.library.ReadListRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.model.Book
import dev.komrd.core.model.Server
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ReadListDetailViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val readListRepository: ReadListRepository,
        private val imageLoaders: KomgaImageLoaders,
    ) : ViewModel() {
        private var readListId: String? = null
        private val serverFlow = MutableStateFlow<Server?>(null)

        val server: StateFlow<Server?> = serverFlow.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val booksPaging: Flow<PagingData<Book>> =
            serverFlow
                .filterNotNull()
                .flatMapLatest { srv ->
                    val id = readListId
                    if (id == null) {
                        flowOf(PagingData.empty())
                    } else {
                        readListRepository.booksPager(srv, id)
                    }
                }.cachedIn(viewModelScope)

        /** ナビ引数を一度だけ受け取り、Serverを解決する。 */
        fun bind(
            serverId: String,
            readListId: String,
        ) {
            if (this.readListId != null) return
            this.readListId = readListId
            viewModelScope.launch { serverFlow.value = serverRepository.byId(serverId) }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaders.forServer(server)
    }
