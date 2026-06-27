package dev.komrd.core.data.prefetch

import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.model.PrefetchCacheSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

interface PrefetchCacheRepository {
    /** Prefetch Storeの内容をbook単位で集約したFlow。更新時は[refresh]を呼ぶ。 */
    fun summaries(): Flow<List<PrefetchCacheSummary>>

    /** 指定bookのキャッシュを削除し、削除件数を返す。 */
    suspend fun purge(
        serverId: String,
        bookId: String,
    ): Int

    /** 一覧を再読み込みする。 */
    suspend fun refresh()
}

class PrefetchCacheRepositoryImpl
    @Inject
    constructor(
        private val prefetchStore: PrefetchStore,
    ) : PrefetchCacheRepository {
        private val refreshTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }

        override fun summaries(): Flow<List<PrefetchCacheSummary>> =
            refreshTrigger
                .map { prefetchStore.listAll() }
                .map { entries ->
                    entries
                        .groupBy { it.serverId to it.bookId }
                        .map { (key, bookEntries) ->
                            val pageNumbers = bookEntries.mapNotNull { it.pageNumber }.sorted()
                            PrefetchCacheSummary(
                                serverId = key.first,
                                bookId = key.second,
                                entryCount = bookEntries.size,
                                totalBytes = bookEntries.sumOf { it.sizeBytes },
                                pageRanges = mergeContiguous(pageNumbers),
                            )
                        }.sortedByDescending { it.totalBytes }
                }.flowOn(Dispatchers.IO)

        override suspend fun purge(
            serverId: String,
            bookId: String,
        ): Int =
            withContext(Dispatchers.IO) {
                prefetchStore.deleteByBook(serverId, bookId)
            }.also { refresh() }

        override suspend fun refresh() {
            refreshTrigger.emit(Unit)
        }
    }

internal fun mergeContiguous(numbers: List<Int>): List<IntRange> {
    if (numbers.isEmpty()) return emptyList()
    val result = mutableListOf<IntRange>()
    var start = numbers[0]
    var end = numbers[0]
    for (i in 1 until numbers.size) {
        val current = numbers[i]
        if (current == end + 1) {
            end = current
        } else {
            result.add(start..end)
            start = current
            end = current
        }
    }
    result.add(start..end)
    return result
}
