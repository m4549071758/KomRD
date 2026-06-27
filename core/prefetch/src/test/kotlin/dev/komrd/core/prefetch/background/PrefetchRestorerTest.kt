package dev.komrd.core.prefetch.background

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.BookDetail
import dev.komrd.core.model.BookPage
import dev.komrd.core.model.NextBook
import dev.komrd.core.model.Server
import dev.komrd.core.prefetch.FakePrefetchController
import dev.komrd.core.prefetch.PrefetchContext
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PrefetchRestorer] の単体テスト（M4 / 純JVM）。
 *
 * 依存5つはすべて interface のため in-memory Fake で差し替え、runTest で restore() を検証する。
 * restore() は「ContextStore→server→loadBook→resolveNextBook→controller.start」の順で復元し、
 * いずれかの段階で失敗したら何もせず false を返す。
 */
class PrefetchRestorerTest {
    private val server: Server =
        Server(
            id = "s1",
            name = "Home",
            baseUrl = "https://example.com",
            auth = AuthMethod.ApiKey("api-key-1"),
        )

    private fun book(
        id: String,
        seriesId: String? = "series-1",
    ): BookDetail =
        BookDetail(
            id = id,
            seriesId = seriesId,
            serverId = "s1",
            name = id,
            pages = (1..10).map { BookPage(number = it, url = "u$it") },
        )

    @Test
    fun restore_withoutContext_returnsFalse_andDoesNotStart() =
        runTest {
            val ctxStore = FakePrefetchContextStore(initial = null)
            val controller = FakePrefetchController()
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book("b1"))),
                    FakeNextBookResolver(KomgaResult.Success(null)),
                    controller,
                )

            assertFalse(restorer.restore())
            assertNull(controller.lastStart)
        }

    @Test
    fun restore_withoutServer_returnsFalse_andDoesNotStart() =
        runTest {
            val ctxStore = FakePrefetchContextStore(PrefetchContext("s1", "b1", 3, null, null))
            val controller = FakePrefetchController()
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf()), // server無し
                    FakeReaderRepository(KomgaResult.Success(book("b1"))),
                    FakeNextBookResolver(KomgaResult.Success(null)),
                    controller,
                )

            assertFalse(restorer.restore())
            assertNull(controller.lastStart)
        }

    @Test
    fun restore_loadBookFailure_returnsFalse_andDoesNotStart() =
        runTest {
            val ctxStore = FakePrefetchContextStore(PrefetchContext("s1", "b1", 3, null, null))
            val controller = FakePrefetchController()
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Failure(KomgaError.Network("net"))),
                    FakeNextBookResolver(KomgaResult.Success(null)),
                    controller,
                )

            assertFalse(restorer.restore())
            assertNull(controller.lastStart)
        }

    @Test
    fun restore_success_startsControllerWithRestoredContext() =
        runTest {
            val ctxStore =
                FakePrefetchContextStore(
                    PrefetchContext("s1", "b1", currentPage = 3, nextBookId = null, nextBookPagesCount = null),
                )
            val controller = FakePrefetchController()
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book("b1"))),
                    FakeNextBookResolver(KomgaResult.Success(NextBook("b2", 20))),
                    controller,
                )

            assertTrue(restorer.restore())
            val call = controller.lastStart
            assertEquals("b1", call?.book?.id)
            assertEquals(3, call?.currentPage)
            // nextBookId が null のため nextBook は null（Resolver は呼ばれない）
            assertNull(call?.nextBook)
        }

    @Test
    fun restore_withNextBookIdMismatch_discardsResolvedNextBook() =
        runTest {
            // ContextStoreの nextBookId(b2) と Resolver が解決した bookId(b9) が不一致→null 採用。
            val ctxStore =
                FakePrefetchContextStore(
                    PrefetchContext("s1", "b1", 3, nextBookId = "b2", nextBookPagesCount = null),
                )
            val controller = FakePrefetchController()
            val resolver = FakeNextBookResolver(KomgaResult.Success(NextBook("b9", 99)))
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book("b1"))),
                    resolver,
                    controller,
                )

            assertTrue(restorer.restore())
            // pagesCount 欠落のため Resolver を呼ぶが、不一致のため null になる。
            assertEquals(1, resolver.resolveCalls)
            assertNull(controller.lastStart?.nextBook)
        }

    @Test
    fun restore_withNextBookPagesCount_restoresFromContextWithoutReResolving() =
        runTest {
            // pagesCount が永続化されていれば Resolver を呼ばず NextBook を再構築（Series全走査回避）。
            val ctxStore =
                FakePrefetchContextStore(
                    PrefetchContext("s1", "b1", 3, nextBookId = "b2", nextBookPagesCount = 30),
                )
            val controller = FakePrefetchController()
            val resolver = FakeNextBookResolver(KomgaResult.Success(NextBook("b9", 99)))
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book("b1"))),
                    resolver,
                    controller,
                )

            assertTrue(restorer.restore())
            assertEquals(0, resolver.resolveCalls) // 再解決スキップ
            val next = controller.lastStart?.nextBook
            assertEquals("b2", next?.bookId)
            assertEquals(30, next?.pagesCount)
        }

    @Test
    fun restore_withNextBookIdButNullSeriesId_skipsResolverAndDiscardsNextBook() =
        runTest {
            // nextBookId あり・pagesCount なし・book.seriesId なし → Resolver 呼べず null。
            val ctxStore =
                FakePrefetchContextStore(
                    PrefetchContext("s1", "b1", 3, nextBookId = "b2", nextBookPagesCount = null),
                )
            val controller = FakePrefetchController()
            val resolver = FakeNextBookResolver(KomgaResult.Success(NextBook("b2", 30)))
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book("b1", seriesId = null))),
                    resolver,
                    controller,
                )

            assertTrue(restorer.restore())
            assertEquals(0, resolver.resolveCalls)
            assertNull(controller.lastStart?.nextBook)
        }

    @Test
    fun restore_withNextBookIdAndMatchingResolvedBook_adoptsResolvedNextBook() =
        runTest {
            // nextBookId(b2) と Resolver の解決(b2) が一致 → 解決結果を採用。
            val ctxStore =
                FakePrefetchContextStore(
                    PrefetchContext("s1", "b1", 3, nextBookId = "b2", nextBookPagesCount = null),
                )
            val controller = FakePrefetchController()
            val resolver = FakeNextBookResolver(KomgaResult.Success(NextBook("b2", 30)))
            val restorer =
                PrefetchRestorer(
                    ctxStore,
                    FakeServerRepository(mapOf("s1" to server)),
                    FakeReaderRepository(KomgaResult.Success(book("b1", seriesId = "series-1"))),
                    resolver,
                    controller,
                )

            assertTrue(restorer.restore())
            assertEquals(1, resolver.resolveCalls)
            // Resolver の呼出引数の context は Series(seriesId)
            val next = controller.lastStart?.nextBook
            assertEquals("b2", next?.bookId)
            assertEquals(30, next?.pagesCount)
        }
}
