package dev.komrd.core.prefetch

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import javax.inject.Inject

interface ResourceFetcher {
    suspend fun fetch(
        server: Server,
        bookId: String,
        resourcePath: String,
    ): KomgaResult<ByteArray>
}

class KomgaResourceFetcher
    @Inject
    constructor(
        private val clientFactory: KomgaClientFactory,
        private val trustStore: ServerTrustStore,
    ) : ResourceFetcher {
        override suspend fun fetch(
            server: Server,
            bookId: String,
            resourcePath: String,
        ): KomgaResult<ByteArray> {
            trustStore.load(server.id)
            return when (
                val result = clientFactory.clientFor(server).getBookEpubResource(bookId, resourcePath)
            ) {
                is KomgaResult.Success -> KomgaResult.Success(result.value.use { it.bytes() })
                is KomgaResult.Failure -> result
            }
        }
    }
