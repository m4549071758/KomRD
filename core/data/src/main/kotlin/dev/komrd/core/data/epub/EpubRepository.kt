package dev.komrd.core.data.epub

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.EpubLocator
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore

interface EpubRepository {
    suspend fun loadManifest(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubManifest>

    suspend fun loadResource(
        server: Server,
        bookId: String,
        href: String,
    ): KomgaResult<ByteArray>

    suspend fun loadPositions(
        server: Server,
        bookId: String,
    ): KomgaResult<List<EpubLocator>>

    suspend fun loadProgression(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubLocator?>
}

class EpubRepositoryImpl(
    private val clientFactory: KomgaClientFactory,
    private val trustStore: ServerTrustStore,
) : EpubRepository {
    override suspend fun loadManifest(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubManifest> =
        withClient(server) { client ->
            when (val result = client.getManifestEpub(bookId)) {
                is KomgaResult.Failure -> result
                is KomgaResult.Success -> KomgaResult.Success(result.value.toEpubManifest())
            }
        }

    override suspend fun loadResource(
        server: Server,
        bookId: String,
        href: String,
    ): KomgaResult<ByteArray> =
        withClient(server) { client ->
            when (val result = client.getBookEpubResource(bookId, href)) {
                is KomgaResult.Failure -> result
                is KomgaResult.Success -> KomgaResult.Success(result.value.bytes())
            }
        }

    override suspend fun loadPositions(
        server: Server,
        bookId: String,
    ): KomgaResult<List<EpubLocator>> =
        withClient(server) { client ->
            when (val result = client.getPositions(bookId)) {
                is KomgaResult.Failure -> result
                is KomgaResult.Success -> KomgaResult.Success(result.value.toEpubLocators())
            }
        }

    override suspend fun loadProgression(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubLocator?> =
        withClient(server) { client ->
            when (val result = client.getProgression(bookId)) {
                is KomgaResult.Failure -> result
                is KomgaResult.Success -> {
                    val locator = result.value?.locator
                    KomgaResult.Success(locator?.toEpubLocator())
                }
            }
        }

    private suspend fun <T> withClient(
        server: Server,
        block: suspend (dev.komrd.core.network.KomgaClient) -> KomgaResult<T>,
    ): KomgaResult<T> {
        trustStore.load(server.id)
        val client = clientFactory.clientFor(server)
        return block(client)
    }
}
