package dev.komrd.core.designsystem.components

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerStateTest {
    @Test
    fun computeSettleTarget_progress_just_below_threshold_is_closed() {
        assertEquals(DrawerValue.Closed, computeSettleTarget(0.49f, 0f))
    }

    @Test
    fun computeSettleTarget_progress_exact_half_is_closed() {
        // 条件は厳密に progress > 0.5f。0.5f は含まないのでClosed。
        assertEquals(DrawerValue.Closed, computeSettleTarget(0.5f, 0f))
    }

    @Test
    fun computeSettleTarget_progress_just_above_threshold_is_open() {
        assertEquals(DrawerValue.Open, computeSettleTarget(0.51f, 0f))
    }

    @Test
    fun computeSettleTarget_positive_velocity_above_min_fling_is_open_even_low_progress() {
        assertEquals(DrawerValue.Open, computeSettleTarget(0.1f, MinFlingPx + 1f))
    }

    @Test
    fun computeSettleTarget_positive_velocity_at_min_fling_boundary_is_closed() {
        // velocityPx > MinFlingPx (厳密)。境界値MinFlingPxちょうどはOpenにならない。
        assertEquals(DrawerValue.Closed, computeSettleTarget(0.1f, MinFlingPx))
    }

    @Test
    fun computeSettleTarget_negative_velocity_does_not_open_when_low_progress() {
        // 閉じる方向(負)の速度はOpenに寄せない。progressベース判定。
        assertEquals(DrawerValue.Closed, computeSettleTarget(0.1f, -1000f))
    }

    @Test
    fun computeSettleTarget_progress_wins_over_negative_velocity_when_above_threshold() {
        // progress > 0.5 なら負方向速度でも進行度優先でOpen。
        assertEquals(DrawerValue.Open, computeSettleTarget(0.6f, -1000f))
    }

    @Test
    fun snapTo_open_sets_openProgress_to_one() {
        val state = DrawerState(DrawerValue.Closed)
        state.snapTo(DrawerValue.Open)
        assertEquals(1f, state.openProgress, 0.0001f)
        assertEquals(DrawerValue.Open, state.value)
        assertEquals(true, state.isOpen)
    }

    @Test
    fun snapTo_close_sets_openProgress_to_zero() {
        val state = DrawerState(DrawerValue.Open)
        state.snapTo(DrawerValue.Closed)
        assertEquals(0f, state.openProgress, 0.0001f)
        assertEquals(DrawerValue.Closed, state.value)
        assertEquals(false, state.isOpen)
    }

    @Test
    fun open_animates_openProgress_to_one() {
        runTest {
            val state = DrawerState(DrawerValue.Closed)
            withFrameClock {
                state.open()
            }
            assertEquals(1f, state.openProgress, 0.001f)
            assertEquals(DrawerValue.Open, state.value)
        }
    }

    @Test
    fun close_animates_openProgress_to_zero() {
        runTest {
            val state = DrawerState(DrawerValue.Open)
            withFrameClock {
                state.close()
            }
            assertEquals(0f, state.openProgress, 0.001f)
            assertEquals(DrawerValue.Closed, state.value)
        }
    }

    @Test
    fun dragTo_clamps_progress_range() {
        val state = DrawerState(DrawerValue.Closed)
        state.dragTo(-0.5f)
        assertEquals(0f, state.openProgress, 0.0001f)
        state.dragTo(2f)
        assertEquals(1f, state.openProgress, 0.0001f)
        state.dragTo(0.3f)
        assertEquals(0.3f, state.openProgress, 0.0001f)
    }

    @Test
    fun settle_open_target_animates_openProgress_to_one() {
        runTest {
            val state = DrawerState(DrawerValue.Closed)
            state.dragTo(0.8f)
            withFrameClock {
                state.settle(0f, 320f)
            }
            assertEquals(1f, state.openProgress, 0.001f)
            assertEquals(DrawerValue.Open, state.value)
        }
    }

    @Test
    fun settle_close_target_animates_openProgress_to_zero() {
        runTest {
            val state = DrawerState(DrawerValue.Open)
            state.dragTo(0.2f)
            withFrameClock {
                state.settle(0f, 320f)
            }
            assertEquals(0f, state.openProgress, 0.001f)
            assertEquals(DrawerValue.Closed, state.value)
        }
    }
}

/**
 * テスト用のMonotonicFrameClock。runTestの仮想時間をdelayで進めつつ次フレームを提供する。
 * animate() は withFrameNanos で駆動するため、JVM純粋テストで必要。
 */
private object TestFrameClock : MonotonicFrameClock {
    private const val FRAME_NANOS: Long = 16_000_000L
    private var currentNanos: Long = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        // runTestの仮想スケジューラがdelayを進め、springアニメーションが収束するまで繰り返す。
        kotlinx.coroutines.delay(16)
        currentNanos += FRAME_NANOS
        return onFrame(currentNanos)
    }
}

private suspend fun <R> withFrameClock(block: suspend () -> R): R = kotlinx.coroutines.withContext(TestFrameClock) { block() }
