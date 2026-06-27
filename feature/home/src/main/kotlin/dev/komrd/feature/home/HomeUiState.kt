package dev.komrd.feature.home

import dev.komrd.core.model.Server
import dev.komrd.feature.library.ServerLibraries

data class HomeUiState(
    val loading: Boolean = true,
    val noServer: Boolean = false,
    val servers: List<Server> = emptyList(),
    val selectedServer: Server? = null,
    val serverGroups: List<ServerLibraries> = emptyList(),
)
