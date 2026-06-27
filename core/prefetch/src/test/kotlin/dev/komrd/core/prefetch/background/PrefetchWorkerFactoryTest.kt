package dev.komrd.core.prefetch.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.model.BookDetail
import dev.komrd.core.prefetch.FakePrefetchController
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [PrefetchWorkerFactory] の単体テスト（M4 / Robolectric + work-testing）。
 *
 * [createWorker] が `workerClassName` で分岐し、[PrefetchWorker] の完全限定名なら同インスタンスを、
 * それ以外なら null を返すことを検証する。WorkerParameters は work-testing の
 * [TestListenableWorkerBuilder] でダミー Worker を構築し、リフレクションで取り出して流用する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrefetchWorkerFactoryTest {
    private lateinit var context: Context
    private lateinit var workerParams: WorkerParameters

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        workerParams = extractWorkerParams(DummyWorkerForFactoryTest::class.java)
    }

    @Test
    fun createWorker_forPrefetchWorkerClassName_returnsPrefetchWorkerInstance() {
        val factory = buildFactory()

        val worker = factory.createWorker(context, PrefetchWorker::class.java.name, workerParams)

        assertNotNull(worker)
        assertTrue(worker is PrefetchWorker)
    }

    @Test
    fun createWorker_forUnknownClassName_returnsNull() {
        val factory = buildFactory()

        val worker = factory.createWorker(context, "com.example.UnknownWorker", workerParams)

        assertNull(worker)
    }

    private fun buildFactory(): PrefetchWorkerFactory =
        PrefetchWorkerFactory(
            FakePrefetchController(),
            restorer(),
            PrefetchNotifier(RuntimeEnvironment.getApplication()),
        )

    private fun restorer(): PrefetchRestorer =
        PrefetchRestorer(
            FakePrefetchContextStore(),
            FakeServerRepository(mapOf()),
            FakeReaderRepository(bookDetailSuccess()),
            FakeNextBookResolver(KomgaResult.Success(null)),
            FakePrefetchController(),
        )

    private fun bookDetailSuccess(): KomgaResult<BookDetail> =
        KomgaResult.Success(BookDetail("b1", serverId = "s1", name = "b1", pages = emptyList()))

    /**
     * [TestListenableWorkerBuilder] で [workerClass] の Worker を構築し、その private な
     * WorkerParameters をリフレクションで取り出して返す。factory.createWorker に渡すための流用。
     */
    private fun <T : ListenableWorker> extractWorkerParams(workerClass: Class<T>): WorkerParameters {
        val worker = TestListenableWorkerBuilder.from(context, workerClass).build()
        val field =
            ListenableWorker::class.java.declaredFields.first { it.type == WorkerParameters::class.java }
        field.isAccessible = true
        return field.get(worker) as WorkerParameters
    }
}

/** [TestListenableWorkerBuilder.from] が構築できる標準コンストラクタを持つダミー Worker。トップレベルに置き
 *  androidx.work.WorkerFactory のリフレクションからアクセス可能にするため private inner class にしない。 */
class DummyWorkerForFactoryTest(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()
}
