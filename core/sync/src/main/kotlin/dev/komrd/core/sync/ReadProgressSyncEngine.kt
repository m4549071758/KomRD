package dev.komrd.core.sync

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.database.dao.ReadProgressQueueDao
import dev.komrd.core.database.entity.ReadProgressQueueEntity
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.dto.ReadProgressUpdateDto
import dev.komrd.core.network.tls.ServerTrustStore
import javax.inject.Inject

interface ReadProgressSyncEngine {
    /** ページsettle時に最新ページ(1-based)とcompletedを送る。PATCH結果(送信可否)を返す。 */
    suspend fun sync(
        server: Server,
        bookId: String,
        page: Int,
        completed: Boolean,
    ): KomgaResult<Unit>

    /** 復帰時などにServer単位で蓄積キューを各book最新1回ずつPATCH(失敗分は残留)。 */
    suspend fun flushPending(server: Server)
}

class ReadProgressSyncEngineImpl
    @Inject
    constructor(
        private val clientFactory: KomgaClientFactory,
        private val trustStore: ServerTrustStore,
        private val queueDao: ReadProgressQueueDao,
        private val clock: () -> Long = System::currentTimeMillis,
    ) : ReadProgressSyncEngine {
        override suspend fun sync(
            server: Server,
            bookId: String,
            page: Int,
            completed: Boolean,
        ): KomgaResult<Unit> {
            queueDao.upsert(
                ReadProgressQueueEntity(
                    serverId = server.id,
                    bookId = bookId,
                    page = page,
                    completed = completed,
                    updatedAt = clock(),
                ),
            )
            return pushAndClear(server, bookId)
        }

        override suspend fun flushPending(server: Server) {
            for (entry in queueDao.findByServer(server.id)) {
                pushAndClear(server, entry.bookId)
            }
        }

        /** キューの当該book最新を1回PATCHし、成功時のみ削除。失敗時は残留して再送対象に残す。 */
        private suspend fun pushAndClear(
            server: Server,
            bookId: String,
        ): KomgaResult<Unit> {
            val entry = queueDao.find(server.id, bookId) ?: return KomgaResult.Success(Unit)
            trustStore.load(server.id)
            val result =
                clientFactory
                    .clientFor(server)
                    .updateReadProgress(bookId, ReadProgressUpdateDto(page = entry.page, completed = entry.completed))
            if (result is KomgaResult.Success) {
                queueDao.delete(server.id, bookId)
            }
            return result
        }
    }
