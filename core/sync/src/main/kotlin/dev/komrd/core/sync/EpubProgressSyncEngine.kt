package dev.komrd.core.sync

import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.database.dao.EpubProgressQueueDao
import dev.komrd.core.database.entity.EpubProgressQueueEntity
import dev.komrd.core.model.EpubLocator
import dev.komrd.core.model.Server
import dev.komrd.core.network.KomgaClientFactory
import dev.komrd.core.network.KomgaJson
import dev.komrd.core.network.dto.R2DeviceDto
import dev.komrd.core.network.dto.R2LocationDto
import dev.komrd.core.network.dto.R2LocatorDto
import dev.komrd.core.network.dto.R2ProgressionDto
import dev.komrd.core.network.tls.ServerTrustStore
import java.time.ZonedDateTime
import javax.inject.Inject

interface EpubProgressSyncEngine {
    /** 章切替/スクロールsettle時に最新locatorを送る。PUT結果(送信可否)を返す。 */
    suspend fun sync(
        server: Server,
        bookId: String,
        locator: EpubLocator,
    ): KomgaResult<Unit>

    /** 復帰時などにServer単位で蓄積キューを各book最新1回ずつPUT(失敗分は残留)。 */
    suspend fun flushPending(server: Server)
}

/**
 * [EpubProgressSyncEngine]本番実装。[ReadProgressSyncEngineImpl]と同patternの
 * online/offline一本化キュー。
 */
class EpubProgressSyncEngineImpl
    @Inject
    constructor(
        private val clientFactory: KomgaClientFactory,
        private val trustStore: ServerTrustStore,
        private val queueDao: EpubProgressQueueDao,
        private val clock: () -> Long = System::currentTimeMillis,
    ) : EpubProgressSyncEngine {
        override suspend fun sync(
            server: Server,
            bookId: String,
            locator: EpubLocator,
        ): KomgaResult<Unit> {
            queueDao.upsert(
                EpubProgressQueueEntity(
                    serverId = server.id,
                    bookId = bookId,
                    locatorJson = KomgaJson.encodeToString(R2LocatorDto.serializer(), locator.toR2LocatorDto()),
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

        /** キューの当該book最新locatorを1回PUTし、成功時のみ削除。失敗時は残留して再送対象に残す。 */
        private suspend fun pushAndClear(
            server: Server,
            bookId: String,
        ): KomgaResult<Unit> {
            val entry = queueDao.find(server.id, bookId)
            val locator =
                entry?.locatorJson?.let {
                    runCatching { KomgaJson.decodeFromString(R2LocatorDto.serializer(), it) }.getOrNull()
                }
            if (entry == null || locator == null) return KomgaResult.Success(Unit)
            trustStore.load(server.id)
            val progression =
                R2ProgressionDto(
                    modified = ZonedDateTime.now().toString(),
                    device = R2DeviceDto(id = DEVICE_ID, name = DEVICE_NAME),
                    locator = locator,
                )
            val result = clientFactory.clientFor(server).putProgression(bookId, progression)
            if (result is KomgaResult.Success) {
                queueDao.delete(server.id, bookId)
            }
            return result
        }
    }

/**
 * [EpubLocator]→[R2LocatorDto]へ変換。`type`は[EpubLocator]が持たないためEPUB spine既定の
 * `application/xhtml+xml`を詰める(進捗送信では参照されず検証のみ)。`locations`は進捗/位置が
 * いずれか非nullのときのみ生成する(all nullのときは送らない)。
 */
private fun EpubLocator.toR2LocatorDto(): R2LocatorDto {
    val hasLocations =
        progression != null || totalProgression != null || position != null || fragments.isNotEmpty()
    val locations =
        if (hasLocations) {
            R2LocationDto(
                fragments = fragments,
                progression = progression,
                position = position,
                totalProgression = totalProgression,
            )
        } else {
            null
        }
    return R2LocatorDto(
        href = href,
        type = TYPE_XHTML,
        locations = locations,
    )
}

private const val DEVICE_ID = "komrd-android"
private const val DEVICE_NAME = "KomRD on Android"
private const val TYPE_XHTML = "application/xhtml+xml"
