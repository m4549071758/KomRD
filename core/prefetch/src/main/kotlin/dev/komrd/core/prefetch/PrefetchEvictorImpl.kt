package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchEntry
import dev.komrd.core.cache.PrefetchStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class PrefetchEvictorImpl
    @Inject
    constructor(
        private val store: PrefetchStore,
        private val configFlow: Flow<PrefetchEvictionConfig>,
        private val clock: () -> Long = System::currentTimeMillis,
    ) : PrefetchEvictor {
        override suspend fun evict(window: ProtectedWindow) {
            val config = configFlow.first()
            val now = clock()
            val ageThreshold = now - config.retentionDays * PrefetchEvictionConfig.DAY_MS
            val all = store.listAll()
            var total = store.sumBytes()

            // ① aging: fetchedAtがretentionDays超を削除(tier問わず)。LRU順で処理。
            for (entry in all) {
                if (entry.fetchedAt < ageThreshold && delete(entry)) {
                    total -= entry.sizeBytes
                }
            }

            // ② 予算超破棄: Tier1→Tier2 をLRU順で削除。Tier3(Window内未読)は保護。
            if (total <= config.maxBytes) return
            for (entry in all) {
                if (total <= config.maxBytes) break
                val agedOut = entry.fetchedAt < ageThreshold // aging済みはskip
                val protectedEntry = tierOf(entry, window) == TIER_PROTECTED
                if (!agedOut && !protectedEntry && delete(entry)) total -= entry.sizeBytes
            }
            // Tier1/2を削り尽くしても total>max → Window自体が予算超。Tier3保護で破棄せず、
            // 呼出側(Controller)が予算内に収まった分だけprefetchし以降を打ち切る。
        }

        private suspend fun delete(entry: PrefetchEntry): Boolean =
            store.delete(entry.serverId, entry.bookId, entry.resourcePath, entry.variant)

        /** entryを[window]基準で3層へ分類。戻り値=[TIER_PROTECTED]なら保護(Window内未読)。 */
        private fun tierOf(
            entry: PrefetchEntry,
            window: ProtectedWindow,
        ): Int =
            when {
                entry.serverId != window.serverId -> TIER_OUT_OF_CONTEXT
                entry.bookId == window.nextBookId -> TIER_PROTECTED
                entry.bookId == window.currentBookId -> tierOfCurrentBook(entry, window)
                else -> TIER_OUT_OF_CONTEXT
            }

        /** 現Book内エントリのTier判定。画像系(pageNumber)とEPUB(href)で分岐。 */
        private fun tierOfCurrentBook(
            entry: PrefetchEntry,
            window: ProtectedWindow,
        ): Int =
            when {
                // 画像系PAGE: pageNumber基準(従来ロジック)。
                entry.resourceKind == PrefetchStore.RESOURCE_KIND_PAGE ->
                    pageTier(entry.pageNumber, window.currentPageNumber)
                // EPUB: 現章(href一致)=保護・現Bookの非HTMLリソース=保護(章横断共有)・他章=既読後方。
                window.currentChapterHref != null &&
                    entry.resourcePath == window.currentChapterHref -> TIER_PROTECTED
                entry.resourceKind == PrefetchStore.RESOURCE_KIND_HTML -> TIER_BEHIND_READ
                else -> TIER_PROTECTED // CSS/画像/フォントは章横断で共有されるため保護
            }

        /** 画像系PAGEのTier。pageNumber=null(異常)は文脈外扱い。 */
        private fun pageTier(
            pageNumber: Int?,
            currentPageNumber: Int,
        ): Int =
            when {
                pageNumber == null -> TIER_OUT_OF_CONTEXT
                pageNumber >= currentPageNumber -> TIER_PROTECTED
                else -> TIER_BEHIND_READ
            }

        private companion object {
            private const val TIER_OUT_OF_CONTEXT = 1 // 文脈外(他サーバ・同サーバ他Book)。破棄優先。
            private const val TIER_BEHIND_READ = 2 // 現Book既読後方。次優先。
            private const val TIER_PROTECTED = 3 // Window内未読(現Book現在位置以降・次Book)。保護。
        }
    }
