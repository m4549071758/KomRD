package dev.komrd.core.prefetch

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchEvictorTest {
    private val window =
        ProtectedWindow(serverId = "s1", currentBookId = "b1", currentPageNumber = 5, nextBookId = "b2")

    private fun evictor(
        store: FakePrefetchStore,
        config: PrefetchEvictionConfig,
        now: Long,
    ): PrefetchEvictor = PrefetchEvictorImpl(store, flowOf(config), clock = { now })

    @Test
    fun agesOutEntries_olderThanRetention() =
        runTest {
            val store = FakePrefetchStore()
            val now = 10_000L
            val threshold = now - 3 * PrefetchEvictionConfig.DAY_MS
            store.putEntry("s1", "b1", 1, sizeBytes = 100, fetchedAt = threshold - 1, lastAccessedAt = 500)
            store.putEntry("s1", "b1", 2, sizeBytes = 100, fetchedAt = threshold + 1, lastAccessedAt = 600)
            evictor(store, PrefetchEvictionConfig(), now).evict(window)
            assertFalse(store.isCached("s1", "b1", 1))
            assertTrue(store.isCached("s1", "b1", 2))
        }

    @Test
    fun evictsByPriority_outOfContextThenBehindRead_thenProtectsWindow() =
        runTest {
            val store = FakePrefetchStore()
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 300)
            val now = 10_000L
            // Tier1: 他サーバ(文脈外)
            store.putEntry("s2", "bx", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 100)
            // Tier2: 現Book既読後方(page<5)
            store.putEntry("s1", "b1", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 200)
            // Tier3: 現Book未読(page>=5)
            store.putEntry("s1", "b1", 5, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 300)
            evictor(store, config, now).evict(window)
            assertFalse(store.isCached("s2", "bx", 1))
            assertFalse(store.isCached("s1", "b1", 1))
            assertTrue(store.isCached("s1", "b1", 5))
        }

    @Test
    fun evictsLRU_oldestAccessedFirstWithinTier() =
        runTest {
            val store = FakePrefetchStore()
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 300)
            val now = 10_000L
            // どちらもTier1(他Book)・lastAccessedAtが異なる。古い方を先に破棄。
            store.putEntry("s2", "bx", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 300)
            store.putEntry("s2", "by", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 100)
            evictor(store, config, now).evict(window)
            assertFalse(store.isCached("s2", "by", 1))
            assertTrue(store.isCached("s2", "bx", 1))
        }

    @Test
    fun preservesWindow_whenWindowItselfExceedsBudget() =
        runTest {
            val store = FakePrefetchStore()
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 300)
            val now = 10_000L
            // 全部Tier3(Window内未読)。Tier1/2が無く予算超でも保護し削除しない。
            store.putEntry("s1", "b1", 5, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 100)
            store.putEntry("s1", "b2", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 200)
            evictor(store, config, now).evict(window)
            assertTrue(store.isCached("s1", "b1", 5))
            assertTrue(store.isCached("s1", "b2", 1))
        }

    @Test
    fun noEviction_whenUnderBudget() =
        runTest {
            val store = FakePrefetchStore()
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 1_000)
            val now = 10_000L
            store.putEntry("s2", "bx", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 100)
            store.putEntry("s1", "b1", 1, sizeBytes = 200, fetchedAt = now, lastAccessedAt = 200)
            evictor(store, config, now).evict(window)
            assertTrue(store.isCached("s2", "bx", 1))
            assertTrue(store.isCached("s1", "b1", 1))
        }

    @Test
    fun evictsEpubBehindReadChapters_beforeCurrentChapterProtected() =
        runTest {
            val store = FakePrefetchStore()
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 300)
            val now = 10_000L
            val epubWindow =
                ProtectedWindow(
                    serverId = "s1",
                    currentBookId = "b1",
                    currentPageNumber = 1,
                    nextBookId = "b2",
                    currentChapterIndex = 1,
                    currentChapterHref = "ch2.xhtml",
                )
            // 現章(href一致) = 保護
            store.putEpubEntry("s1", "b1", "ch2.xhtml", "HTML", sizeBytes = 200, fetchedAt = now, lastAccessedAt = 100)
            // 他章(既読後方) = 破棄優先
            store.putEpubEntry("s1", "b1", "ch1.xhtml", "HTML", sizeBytes = 200, fetchedAt = now, lastAccessedAt = 50)
            evictor(store, config, now).evict(epubWindow)
            assertFalse("既読後方章は破棄", store.isCachedResource("s1", "b1", "ch1.xhtml"))
            assertTrue("現章は保護", store.isCachedResource("s1", "b1", "ch2.xhtml"))
        }

    @Test
    fun protectsEpubNonHtmlResourcesInCurrentBook() =
        runTest {
            val store = FakePrefetchStore()
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 300)
            val now = 10_000L
            val epubWindow =
                ProtectedWindow(
                    serverId = "s1",
                    currentBookId = "b1",
                    currentPageNumber = 0,
                    nextBookId = null,
                    currentChapterIndex = 0,
                    currentChapterHref = "ch1.xhtml",
                )
            // 現BookのCSS(章横断共有) = 保護
            store.putEpubEntry("s1", "b1", "style.css", "CSS", sizeBytes = 200, fetchedAt = now, lastAccessedAt = 100)
            // 文脈外 = 破棄優先
            store.putEpubEntry("s2", "bx", "ch1.xhtml", "HTML", sizeBytes = 200, fetchedAt = now, lastAccessedAt = 50)
            evictor(store, config, now).evict(epubWindow)
            assertTrue("現Book CSSは保護", store.isCachedResource("s1", "b1", "style.css"))
            assertFalse("他サーバは文脈外で破棄", store.isCachedResource("s2", "bx", "ch1.xhtml"))
        }

    @Test
    fun agesOutEpubEntries_olderThanRetention() =
        runTest {
            val store = FakePrefetchStore()
            val now = 10_000L
            val threshold = now - 3 * PrefetchEvictionConfig.DAY_MS
            val epubWindow =
                ProtectedWindow(
                    serverId = "s1",
                    currentBookId = "b1",
                    currentPageNumber = 0,
                    nextBookId = null,
                    currentChapterIndex = 0,
                    currentChapterHref = "ch1.xhtml",
                )
            store.putEpubEntry(
                "s1",
                "b1",
                "ch1.xhtml",
                "HTML",
                sizeBytes = 100,
                fetchedAt = threshold - 1,
                lastAccessedAt = 100,
            )
            store.putEpubEntry(
                "s1",
                "b1",
                "style.css",
                "CSS",
                sizeBytes = 100,
                fetchedAt = threshold + 1,
                lastAccessedAt = 100,
            )
            evictor(store, PrefetchEvictionConfig(), now).evict(epubWindow)
            assertFalse("aging超のHTMLは破棄", store.isCachedResource("s1", "b1", "ch1.xhtml"))
            assertTrue("aging内のCSSは保持", store.isCachedResource("s1", "b1", "style.css"))
        }
}
