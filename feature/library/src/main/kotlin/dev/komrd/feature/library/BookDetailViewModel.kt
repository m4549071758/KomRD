package dev.komrd.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.ImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.library.KomgaImageLoaders
import dev.komrd.core.data.reader.BookOverviewRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.model.BookOverview
import dev.komrd.core.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface BookDetailUiState {
    data object Loading : BookDetailUiState

    data class Success(
        val overview: BookOverview,
        val server: Server,
    ) : BookDetailUiState

    data class Error(
        val message: String,
    ) : BookDetailUiState
}

@HiltViewModel
class BookDetailViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val bookOverviewRepository: BookOverviewRepository,
        private val imageLoaders: KomgaImageLoaders,
    ) : ViewModel() {
        private val _state = MutableStateFlow<BookDetailUiState>(BookDetailUiState.Loading)
        val state: StateFlow<BookDetailUiState> = _state.asStateFlow()

        private var bound = false

        fun bind(
            serverId: String,
            bookId: String,
        ) {
            if (bound) return
            bound = true
            viewModelScope.launch {
                val server = serverRepository.byId(serverId)
                if (server == null) {
                    _state.value = BookDetailUiState.Error("サーバが見つかりません")
                    return@launch
                }
                when (val result = bookOverviewRepository.loadOverview(server, bookId)) {
                    is KomgaResult.Success -> _state.value = BookDetailUiState.Success(result.value, server)
                    is KomgaResult.Failure -> _state.value = BookDetailUiState.Error("ブック情報の取得に失敗しました")
                }
            }
        }

        fun imageLoaderFor(server: Server): ImageLoader = imageLoaders.forServer(server)
    }
