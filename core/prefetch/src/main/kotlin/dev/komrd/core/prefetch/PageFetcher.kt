package dev.komrd.core.prefetch

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.tls.ServerTrustStore
import javax.inject.Inject

interface PageFetcher {
    suspend fun fetch(
        server: Server,
        bookId: String,
        pageNumber: Int,
        mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE,
    ): KomgaResult<ByteArray>
}

class KomgaPageFetcher
    @Inject
    constructor(
        private val clientFactory: KomgaClientFactory,
        private val trustStore: ServerTrustStore,
    ) : PageFetcher {
        override suspend fun fetch(
            server: Server,
            bookId: String,
            pageNumber: Int,
            mediaProfile: BookMediaProfile,
        ): KomgaResult<ByteArray> {
            trustStore.load(server.id)
            val convert = convertFor(mediaProfile)
            return when (
                val result =
                    clientFactory.clientFor(server).getBookPage(bookId, pageNumber, convert = convert)
            ) {
                is KomgaResult.Success -> KomgaResult.Success(result.value.use { it.bytes() })
                is KomgaResult.Failure -> result
            }
        }

        private fun convertFor(mediaProfile: BookMediaProfile): String? =
            when (mediaProfile) {
                BookMediaProfile.PDF -> "jpeg"
                else -> null
            }
    }
