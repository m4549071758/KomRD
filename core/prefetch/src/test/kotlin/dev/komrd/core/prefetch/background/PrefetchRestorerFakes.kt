package dev.komrd.core.prefetch.background

import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.prefetch.NextBookResolver
import dev.komrd.core.data.reader.ReaderRepository
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.ReadingContext
import dev.komrd.core.model.Server
import dev.komrd.core.prefetch.PrefetchContext
import dev.komrd.core.prefetch.PrefetchContextStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.cert.X509Certificate

/**
 * [PrefetchContextStore]のin-memory Fake（M4テスト用）。
 * [prefetchContext]は[MutableStateFlow]で保持し、save/clear で直接更新する。
 */
class FakePrefetchContextStore(
    initial: PrefetchContext? = null,
) : PrefetchContextStore {
    private val _prefetchContext = MutableStateFlow(initial)
    override val prefetchContext: Flow<PrefetchContext?> = _prefetchContext.asStateFlow()

    override suspend fun save(ctx: PrefetchContext) {
        _prefetchContext.value = ctx
    }

    override suspend fun clear() {
        _prefetchContext.value = null
    }

    /** テストから文脈を直接設定。 */
    fun set(ctx: PrefetchContext?) {
        _prefetchContext.value = ctx
    }
}

/**
 * [ServerRepository]のin-memory Fake（M4テスト用）。byId のみ実装し、他は未使用のため TODO で落とす。
 */
class FakeServerRepository(
    private val serverMap: Map<String, Server> = emptyMap(),
) : ServerRepository {
    override val servers: Flow<List<Server>> = kotlinx.coroutines.flow.flowOf(serverMap.values.toList())

    override suspend fun byId(id: String): Server? = serverMap[id]

    override suspend fun add(server: Server) = throw NotImplementedError()

    override suspend fun update(server: Server) = throw NotImplementedError()

    override suspend fun delete(id: String) = throw NotImplementedError()

    override suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult> = throw NotImplementedError()

    override suspend fun pinCertificate(
        serverId: String,
        certificate: CertificateInfo,
    ): KomgaResult<Unit> = throw NotImplementedError()

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<X509Certificate>,
    ): KomgaResult<Unit> = throw NotImplementedError()

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean = false

    override fun certificateInfoOf(error: KomgaError): CertificateInfo? = null
}

/**
 * [ReaderRepository]のin-memory Fake（M4テスト用）。[loadBook] で固定結果を返す。
 */
class FakeReaderRepository(
    private val result: KomgaResult<BookDetail>,
) : ReaderRepository {
    override suspend fun loadBook(
        server: Server,
        bookId: String,
    ): KomgaResult<BookDetail> = result
}

/**
 * [NextBookResolver]のin-memory Fake（M4テスト用）。[resolve] で固定結果を返し、呼出を記録する。
 */
class FakeNextBookResolver(
    private val result: KomgaResult<NextBook?>,
) : NextBookResolver {
    var resolveCalls: Int = 0
        private set

    override suspend fun resolve(
        server: Server,
        currentBookId: String,
        context: ReadingContext,
    ): KomgaResult<NextBook?> {
        resolveCalls++
        return result
    }
}

/**
 * [PrefetchContextStore]の hang 版 Fake（M4テスト用）。[prefetchContext] は一切放出せず、
 * [first] が永遠に suspended する。PrefetchJobService の軽量検証で restore() を止める用途。
 */
class HangingPrefetchContextStore : PrefetchContextStore {
    override val prefetchContext: Flow<PrefetchContext?> =
        kotlinx.coroutines.flow.flow {
            kotlinx.coroutines.delay(Long.MAX_VALUE)
        }

    override suspend fun save(ctx: PrefetchContext) = Unit

    override suspend fun clear() = Unit
}
