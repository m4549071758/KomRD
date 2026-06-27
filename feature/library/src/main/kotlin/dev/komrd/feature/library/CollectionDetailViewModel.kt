package dev.komrd.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.data.library.CollectionRepository
import dev.komrd.core.data.library.KomgaImageLoaders
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.model.Series
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
class CollectionDetailViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val collectionRepository: CollectionRepository,
        private val imageLoaders: KomgaImageLoaders,
    ) : ViewModel() {
        private var collectionId: String? = null
        private val serverFlow = MutableStateFlow<Server?>(null)

        val server: StateFlow<Server?> = serverFlow.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val seriesPaging: Flow<PagingData<Series>> =
            serverFlow
                .filterNotNull()
                .flatMapLatest { srv ->
                    val id = collectionId
                    if (id == null) {
                        flowOf(PagingData.empty())
                    } else {
                        collectionRepository.seriesPager(srv, id)
                    }
                }.cachedIn(viewModelScope)

        /** ナビ引数を一度だけ受け取り、Serverを解決する。 */
        fun bind(
            serverId: String,
            collectionId: String,
        ) {
            if (this.collectionId != null) return
            this.collectionId = collectionId
            viewModelScope.launch { serverFlow.value = serverRepository.byId(serverId) }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaders.forServer(server)
    }
