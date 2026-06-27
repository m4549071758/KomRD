package dev.komrd.core.data.startup

import dev.komrd.core.network.tls.ServerTrustStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrustCacheWarmer
    @Inject
    constructor(
        private val trustStore: ServerTrustStore,
    ) {
        suspend fun warm() {
            trustStore.loadAll()
        }
    }
