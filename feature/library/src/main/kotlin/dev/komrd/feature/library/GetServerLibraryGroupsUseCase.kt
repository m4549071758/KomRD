package dev.komrd.feature.library

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.library.CollectionRepository
import dev.komrd.core.data.library.LibraryRepository
import dev.komrd.core.data.library.ReadListRepository
import dev.komrd.core.data.server.ServerRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

class GetServerLibraryGroupsUseCase
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val libraryRepository: LibraryRepository,
        private val collectionRepository: CollectionRepository,
        private val readListRepository: ReadListRepository,
    ) {
        @OptIn(ExperimentalCoroutinesApi::class)
        operator fun invoke(): Flow<List<ServerLibraries>> =
            serverRepository.servers.mapLatest { servers ->
                servers.map { server ->
                    val libraries = libraryRepository.libraries(server)
                    val collections = collectionRepository.collections(server)
                    val readLists = readListRepository.readLists(server)
                    ServerLibraries(
                        server = server,
                        libraries = (libraries as? KomgaResult.Success)?.value ?: emptyList(),
                        collections = (collections as? KomgaResult.Success)?.value ?: emptyList(),
                        readLists = (readLists as? KomgaResult.Success)?.value ?: emptyList(),
                        error = (libraries as? KomgaResult.Failure)?.error,
                    )
                }
            }
    }
