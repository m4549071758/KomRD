package dev.komrd.core.prefetch

import dev.komrd.core.cache.PrefetchStore
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.epub.EpubRepository
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookMediaProfile
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.EpubChapter
import dev.komrd.core.model.EpubManifest
import dev.komrd.core.model.EpubResource
import dev.komrd.core.model.Server
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
class PrefetchControllerImplTest {
    @Test
    fun parallelismRespected_capsConcurrentFetches() =
        runTest {
            val fetcher = FakePageFetcher().apply { gate = CompletableDeferred() }
            val store = FakePrefetchStore()
            val ctrl = controller(this, fetcher, store, parallelism = 2)

            ctrl.start(server(), book("b1", (1..5).toList()), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle() // 2ワーカーがgateで待機

            assertEquals(2, fetcher.maxConcurrent.get())

            fetcher.releaseGate()
            advanceUntilIdle()
            ctrl.stop()

            assertEquals(5, fetcher.calls.size)
            assertEquals(2, fetcher.maxConcurrent.get())
        }

    @Test
    fun cachedPagesSkipped_notFetched() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            store.prepopulate("s1", "b1", 2) // page2はキャッシュ済
            val ctrl = controller(this, fetcher, store, parallelism = 1)

            ctrl.start(server(), book("b1", listOf(1, 2, 3)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle()
            ctrl.stop()

            val fetched = fetcher.calls.map { it.second }.toSet()
            assertFalse("キャッシュ済みpage2は取得されない", 2 in fetched)
            assertTrue("page1/3は取得される", setOf(1, 3).all { it in fetched })
        }

    @Test
    fun demandPreemptsSequential_fetchedBeforeRemainingPrefetch() =
        runTest {
            val fetcher = FakePageFetcher().apply { gate = CompletableDeferred() }
            val store = FakePrefetchStore()
            val ctrl = controller(this, fetcher, store, parallelism = 1)

            ctrl.start(server(), book("b1", listOf(1, 2, 3, 4)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle() // 1ワーカーがpage1でgate待機

            // スライダージャンプ想定のDemand Fetch(page9=Window外へジャンプ)
            ctrl.demand("b1", 9)
            fetcher.releaseGate()
            advanceUntilIdle()
            ctrl.stop()

            val calls = fetcher.calls.map { it.second }
            assertTrue("demand(9)は取得される", 9 in calls)
            assertTrue(
                "demand(9)は残り順次Prefetch(2)より先に取得される",
                calls.indexOf(9) < calls.indexOf(2),
            )
        }

    @Test
    fun recenterOnPageChanged_drainsOldAndEnqueuesNewWindow() =
        runTest {
            val fetcher = FakePageFetcher().apply { gate = CompletableDeferred() }
            val store = FakePrefetchStore()
            val ctrl = controller(this, fetcher, store, parallelism = 2)

            ctrl.start(server(), book("b1", (1..10).toList()), currentPageNumber = 2, nextBook = null)
            advanceUntilIdle() // 2ワーカーがpage2,3でgate待機(残り4..10はchannel)

            ctrl.onPageChanged(8) // 再センタリング: channelをdrain→新Window 8,9,10
            fetcher.releaseGate()
            advanceUntilIdle()
            ctrl.stop()

            val fetched = fetcher.calls.map { it.second }.toSet()
            assertTrue("新Window 8,9,10は取得される", listOf(8, 9, 10).all { it in fetched })
            assertFalse("drainされた旧Window 4..7は取得されない", listOf(4, 5, 6, 7).any { it in fetched })
        }

    @Test
    fun backoffRetriesOnNetworkError_thenSucceeds() =
        runTest {
            val fetcher = FakePageFetcher()
            fetcher.failThenSuccess("b1", 3, KomgaError.Network("net"), times = 2)
            val store = FakePrefetchStore()
            val ctrl =
                controller(
                    this,
                    fetcher,
                    store,
                    parallelism = 1,
                    backoff = BackoffConfig(baseMillis = 100, maxAttempts = 5),
                )

            ctrl.start(server(), book("b1", listOf(1, 2, 3)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle() // 仮想時間でバックオフdelay(100ms,200ms)を進めて再試行
            ctrl.stop()

            assertEquals("page3は失敗2回+成功1回=3回呼出", 3, fetcher.calls.count { it.second == 3 })
            assertTrue("再試行後にpage3がキャッシュされる", store.isCached("s1", "b1", 3))
        }

    @Test
    fun nonRetryableErrorSkipsTarget_noRetry() =
        runTest {
            val fetcher = FakePageFetcher()
            fetcher.enqueueResult("b1", 2, KomgaResult.Failure(KomgaError.Unauthorized()))
            val store = FakePrefetchStore()
            val ctrl =
                controller(
                    this,
                    fetcher,
                    store,
                    parallelism = 1,
                    backoff = BackoffConfig(baseMillis = 100, maxAttempts = 5),
                )

            ctrl.start(server(), book("b1", listOf(1, 2, 3)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle()
            ctrl.stop()

            assertEquals("401はretryしない(1回のみ)", 1, fetcher.calls.count { it.second == 2 })
            assertFalse("page2はキャッシュされない", store.isCached("s1", "b1", 2))
            assertTrue("page1/3はキャッシュされる", store.isCached("s1", "b1", 1) && store.isCached("s1", "b1", 3))
        }

    @Test
    fun pauseSuspendsResumeContinues() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            val ctrl = controller(this, fetcher, store, parallelism = 1)

            ctrl.start(server(), book("b1", listOf(1, 2, 3)), currentPageNumber = 1, nextBook = null)
            ctrl.pause()
            advanceUntilIdle()

            assertTrue("pause中は取得しない", fetcher.calls.isEmpty())

            ctrl.resume()
            advanceUntilIdle()
            ctrl.stop()

            assertEquals("resume後は全件取得", 3, fetcher.calls.size)
        }

    @Test
    fun overBudget_truncatesRemainingPrefetch() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            // 予算0=必ず予算超。put後ただちに打ち切り(drain)し残りprefetchを停止する。
            val config = PrefetchEvictionConfig(retentionDays = 3, maxBytes = 0)
            val ctrl = controller(this, fetcher, store, parallelism = 1, evictionConfig = config)

            ctrl.start(server(), book("b1", listOf(1, 2, 3, 4, 5)), currentPageNumber = 1, nextBook = null)
            advanceUntilIdle()
            ctrl.stop()

            val fetched = fetcher.calls.map { it.second }
            assertTrue("現在位置page1は取得される", 1 in fetched)
            assertFalse(
                "予算超で打ち切り・page3以降は取得されない",
                listOf(3, 4, 5).any { it in fetched },
            )
        }

    @Test
    fun pdfBook_storesWithJpegVariant_andPassesPdfMediaProfile() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            val ctrl = controller(this, fetcher, store, parallelism = 1)

            ctrl.start(
                server(),
                book("b1", listOf(1, 2, 3), mediaProfile = BookMediaProfile.PDF),
                currentPageNumber = 1,
                nextBook = null,
            )
            advanceUntilIdle()
            ctrl.stop()

            assertTrue("page1はjpeg variantでキャッシュ", store.isCached("s1", "b1", 1, PrefetchStore.VARIANT_JPEG))
            assertTrue(
                "page2/3もjpeg variant",
                store.isCached("s1", "b1", 2, PrefetchStore.VARIANT_JPEG) &&
                    store.isCached("s1", "b1", 3, PrefetchStore.VARIANT_JPEG),
            )
            // full variantには入らない
            assertFalse("画像系full variantには入らない", store.isCached("s1", "b1", 1, PrefetchStore.VARIANT_FULL))
            // FetcherへmediaProfile=PDFが伝播(convert=jpegの元)。
            assertTrue("全fetchがPDF mediaProfile", fetcher.mediaProfiles.all { it == BookMediaProfile.PDF })
        }

    @Test
    fun imageBook_storesWithFullVariant_andPassesImageMediaProfile() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            val ctrl = controller(this, fetcher, store, parallelism = 1)

            ctrl.start(
                server(),
                book("b1", listOf(1, 2), mediaProfile = BookMediaProfile.IMAGE),
                currentPageNumber = 1,
                nextBook = null,
            )
            advanceUntilIdle()
            ctrl.stop()

            assertTrue("page1はfull variantでキャッシュ", store.isCached("s1", "b1", 1, PrefetchStore.VARIANT_FULL))
            assertFalse("jpeg variantには入らない", store.isCached("s1", "b1", 1, PrefetchStore.VARIANT_JPEG))
            assertTrue("全fetchがIMAGE mediaProfile", fetcher.mediaProfiles.all { it == BookMediaProfile.IMAGE })
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Suppress("LongParameterList") // テストDSL・EPUB分岐のFake注入を一括指定
    private fun controller(
        scope: TestScope,
        fetcher: FakePageFetcher,
        store: FakePrefetchStore,
        parallelism: Int,
        backoff: BackoffConfig = BackoffConfig(baseMillis = 100, maxAttempts = 5),
        evictor: PrefetchEvictor = NoOpPrefetchEvictor,
        evictionConfig: PrefetchEvictionConfig = PrefetchEvictionConfig(),
        epubRepository: EpubRepository? = null,
        resourceFetcher: ResourceFetcher? = null,
    ): PrefetchControllerImpl {
        // backgroundScope自身のdispatcherを使う（advanceUntilIdleで確実に進めるため）
        val dispatcher = scope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        return PrefetchControllerImpl(
            scope = scope,
            pageFetcher = fetcher,
            store = store,
            evictor = evictor,
            evictionConfigFlow = flowOf(evictionConfig),
            parallelismFlow = flowOf(parallelism),
            backoff = backoff,
            dispatcher = dispatcher,
            epubRepository = epubRepository,
            resourceFetcher = resourceFetcher,
        )
    }

    private fun server(): Server =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = "https://example.com",
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun book(
        id: String,
        pages: List<Int>,
        mediaProfile: BookMediaProfile = BookMediaProfile.IMAGE,
    ): BookDetail =
        BookDetail(
            id = id,
            serverId = "s1",
            name = id,
            pages = pages.map { BookPage(number = it, url = "u$it") },
            mediaProfile = mediaProfile,
        )

    @Test
    fun epubBook_fetchesChapterResourcesAndStoresWithFullVariant() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            val epubRepo =
                FakeEpubRepository(
                    manifest =
                        EpubManifest(
                            readingOrder =
                                listOf(
                                    EpubChapter("ch1.xhtml", "application/xhtml+xml", "Ch1"),
                                    EpubChapter("ch2.xhtml", "application/xhtml+xml", "Ch2"),
                                ),
                            toc = emptyList(),
                            resources = listOf(EpubResource("style.css", "text/css", null)),
                            title = "Book",
                            readingProgression = "ltr",
                        ),
                )
            val resourceFetcher = FakeResourceFetcher()
            val ctrl =
                controller(
                    this,
                    fetcher,
                    store,
                    parallelism = 1,
                    epubRepository = epubRepo,
                    resourceFetcher = resourceFetcher,
                )

            // EPUBはpages空・mediaProfile=EPUB。currentPageNumberは0-based章インデックス(0=ch1)。
            ctrl.start(server(), book("b1", emptyList(), BookMediaProfile.EPUB), currentPageNumber = 0, nextBook = null)
            advanceUntilIdle()
            ctrl.stop()

            // ch1以降(ch1,ch2)+style.css が取得される・variant=full。
            assertTrue("ch1取得", resourceFetcher.calls.any { it.second == "ch1.xhtml" })
            assertTrue("ch2取得", resourceFetcher.calls.any { it.second == "ch2.xhtml" })
            assertTrue("style.css取得", resourceFetcher.calls.any { it.second == "style.css" })
            assertTrue(
                "ch1はHTML/fullでキャッシュ",
                store.isCachedResource("s1", "b1", "ch1.xhtml", PrefetchStore.VARIANT_FULL),
            )
            assertTrue(
                "style.cssはCSS/fullでキャッシュ",
                store.isCachedResource("s1", "b1", "style.css", PrefetchStore.VARIANT_FULL),
            )
            // 画像系PageFetcherはEPUB媒体では呼ばれない。
            assertTrue("EPUBでPageFetcherは不使用", fetcher.calls.isEmpty())
        }

    @Test
    fun epubBook_manifestLoadFailure_skipsPrefetchGracefully() =
        runTest {
            val fetcher = FakePageFetcher()
            val store = FakePrefetchStore()
            val epubRepo = FakeEpubRepository(manifestResult = KomgaResult.Failure(KomgaError.Network("net")))
            val resourceFetcher = FakeResourceFetcher()
            val ctrl =
                controller(
                    this,
                    fetcher,
                    store,
                    parallelism = 1,
                    epubRepository = epubRepo,
                    resourceFetcher = resourceFetcher,
                )

            ctrl.start(server(), book("b1", emptyList(), BookMediaProfile.EPUB), currentPageNumber = 0, nextBook = null)
            advanceUntilIdle()
            ctrl.stop()

            // manifest取得失敗=非致命・prefetch無し・例外伝播無し。
            assertTrue("manifest失敗時はresource取得なし", resourceFetcher.calls.isEmpty())
            assertTrue("store空", store.listAll().isEmpty())
        }
}

/** [ResourceFetcher]のテスト用Fake。 */
class FakeResourceFetcher : ResourceFetcher {
    val calls = mutableListOf<Pair<String, String>>() // (bookId, resourcePath)
    var result: (bookId: String, resourcePath: String) -> KomgaResult<ByteArray> =
        { _, path -> KomgaResult.Success(path.toByteArray()) }

    override suspend fun fetch(
        server: Server,
        bookId: String,
        resourcePath: String,
    ): KomgaResult<ByteArray> {
        calls += bookId to resourcePath
        return result(bookId, resourcePath)
    }
}

/** [EpubRepository]のテスト用Fake。 */
class FakeEpubRepository(
    private val manifest: EpubManifest? = null,
    private val manifestResult: KomgaResult<EpubManifest>? = null,
) : EpubRepository {
    override suspend fun loadManifest(
        server: Server,
        bookId: String,
    ): KomgaResult<EpubManifest> = manifestResult ?: KomgaResult.Success(manifest!!)

    override suspend fun loadResource(
        server: Server,
        bookId: String,
        href: String,
    ): KomgaResult<ByteArray> = KomgaResult.Success(byteArrayOf(0))

    override suspend fun loadPositions(
        server: Server,
        bookId: String,
    ): KomgaResult<List<dev.komrd.core.model.EpubLocator>> = KomgaResult.Success(emptyList())

    override suspend fun loadProgression(
        server: Server,
        bookId: String,
    ): KomgaResult<dev.komrd.core.model.EpubLocator?> = KomgaResult.Success(null)
}
