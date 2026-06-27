package dev.komrd.core.prefetch

interface PrefetchEvictor {
    suspend fun evict(window: ProtectedWindow)
}

/** 何も破棄しない既定実装。Controller単体テスト等で破棄未関与を維持。 */
object NoOpPrefetchEvictor : PrefetchEvictor {
    override suspend fun evict(window: ProtectedWindow) = Unit
}
