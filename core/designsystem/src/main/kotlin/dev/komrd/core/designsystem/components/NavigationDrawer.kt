package dev.komrd.core.designsystem.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.contentColorFor
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

enum class DrawerValue {
    Closed,
    Open,
}

/**
 * settle先を進行度とfling速度から決定する純関数。
 * 開く方向(正)のflingのみOpenに寄せ、閉じる方向(負)の速度はprogressベース判定に委ねる。
 */
internal fun computeSettleTarget(
    progress: Float,
    velocityPx: Float,
): DrawerValue = if (progress > 0.5f || velocityPx > MinFlingPx) DrawerValue.Open else DrawerValue.Closed

class DrawerState(
    initialValue: DrawerValue,
) {
    private val progressState = mutableFloatStateOf(initialProgress(initialValue))
    private val animationToken = AtomicInteger(0)

    var value: DrawerValue by mutableStateOf(initialValue)
        private set

    /**
     * ドロワーの開き具合。0f=完全閉、1f=完全開。ドラッグ中は連続的に変化する。
     */
    val openProgress: Float get() = progressState.floatValue

    val isOpen: Boolean get() = value == DrawerValue.Open

    private fun syncValue(progress: Float) {
        val target = if (progress >= 0.5f) DrawerValue.Open else DrawerValue.Closed
        if (value != target) value = target
    }

    private fun setProgress(progress: Float) {
        val clamped = progress.coerceIn(ClosedProgress, OpenProgress)
        progressState.floatValue = clamped
        syncValue(clamped)
    }

    suspend fun open() {
        animateTo(OpenProgress)
        value = DrawerValue.Open
    }

    suspend fun close() {
        animateTo(ClosedProgress)
        value = DrawerValue.Closed
    }

    fun snapTo(target: DrawerValue) {
        invalidateAnimation()
        val progress = initialProgress(target)
        progressState.floatValue = progress
        value = target
    }

    /**
     * ドラッグ中の進行度を直接設定する。実行中のアニメーションは無効化され即座に反映する。
     */
    internal fun dragTo(progress: Float) {
        invalidateAnimation()
        setProgress(progress)
    }

    /**
     * ドラッグ終了時に進行度と速度から開閉先を決定しアニメーションで落ち着かせる。
     */
    internal suspend fun settle(
        velocityPx: Float,
        widthPx: Float,
    ) {
        val target = computeSettleTarget(openProgress, velocityPx)
        animateTo(initialProgress(target))
        value = target
    }

    private suspend fun animateTo(targetProgress: Float) {
        val myToken = animationToken.incrementAndGet()
        val start = openProgress
        animate(
            initialValue = start,
            targetValue = targetProgress,
            animationSpec = drawerAnimationSpec(),
        ) { v, _ ->
            if (animationToken.get() == myToken) setProgress(v)
        }
        if (animationToken.get() == myToken) {
            progressState.floatValue = targetProgress
            syncValue(targetProgress)
        }
    }

    private fun invalidateAnimation() {
        animationToken.incrementAndGet()
    }

    private fun drawerAnimationSpec() =
        spring<Float>(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )

    private companion object {
        const val OpenProgress: Float = 1f
        const val ClosedProgress: Float = 0f

        fun initialProgress(value: DrawerValue): Float = if (value == DrawerValue.Open) OpenProgress else ClosedProgress
    }
}

@Composable
fun rememberDrawerState(initialValue: DrawerValue): DrawerState = remember { DrawerState(initialValue) }

@Composable
@Suppress("LongParameterList")
fun ModalNavigationDrawer(
    drawerState: DrawerState,
    modifier: Modifier = Modifier,
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val drawerWidthPx = with(density) { DrawerWidth.toPx() }
    val progress = drawerState.openProgress
    val offsetPx = (progress - 1f) * drawerWidthPx
    val scrimColor = KomrdTheme.colors.scrim
    val scrimAlpha = scrimColor.alpha * progress

    Box(modifier.fillMaxSize()) {
        content()

        // Scrim: 開時のみ表示、タップで閉じる。ドラッグで閉じる方向へ追従。
        if (progress > 0f) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(scrimColor.copy(alpha = scrimAlpha))
                        .pointerInput(drawerState) {
                            detectHorizontalDragGestures(
                                onDragStart = { },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    val next = (progress + dragAmount / drawerWidthPx).coerceIn(0f, 1f)
                                    drawerState.dragTo(next)
                                },
                                onDragEnd = {
                                    scope.launch { drawerState.settle(0f, drawerWidthPx) }
                                },
                                onDragCancel = {
                                    scope.launch { drawerState.settle(0f, drawerWidthPx) }
                                },
                            )
                        }.clickable { scope.launch { drawerState.close() } },
            )
        }

        // ドロワーパネル: offsetはpx単位で進行度に連動。
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(DrawerWidth)
                    .offset { IntOffset(offsetPx.roundToInt(), 0) }
                    .pointerInput(drawerState) {
                        detectHorizontalDragGestures(
                            onDragStart = { },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                val next = (progress + dragAmount / drawerWidthPx).coerceIn(0f, 1f)
                                drawerState.dragTo(next)
                            },
                            onDragEnd = {
                                scope.launch { drawerState.settle(0f, drawerWidthPx) }
                            },
                            onDragCancel = {
                                scope.launch { drawerState.settle(0f, drawerWidthPx) }
                            },
                        )
                    },
        ) {
            drawerContent()
        }

        // 閉時: 左端EdgeWidth帯の右ドラッグで開く。
        if (!drawerState.isOpen) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(EdgeWidth)
                        .pointerInput(drawerState) {
                            val tracker = VelocityTracker()
                            var accDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    accDrag = 0f
                                    tracker.resetTracking()
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    accDrag += dragAmount
                                    val next = (accDrag / drawerWidthPx).coerceIn(0f, 1f)
                                    drawerState.dragTo(next)
                                },
                                onDragEnd = {
                                    val velocity = tracker.calculateVelocity().x
                                    scope.launch { drawerState.settle(velocity, drawerWidthPx) }
                                },
                                onDragCancel = {
                                    scope.launch { drawerState.settle(0f, drawerWidthPx) }
                                },
                            )
                        },
            )
        }
    }
}

@Composable
fun ModalDrawerSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxHeight().width(DrawerWidth),
        color = KomrdTheme.colors.surface,
        contentColor = contentColorFor(KomrdTheme.colors.surface),
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
@Suppress("LongParameterList")
fun NavigationDrawerItem(
    label: @Composable () -> Unit,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    badge: @Composable (() -> Unit)? = null,
) {
    val colors = KomrdTheme.colors
    val containerColor = if (selected) colors.secondary.copy(alpha = 0.3f) else Color.Transparent
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = if (selected) colors.primary else contentColorFor(colors.surface),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) icon()
            label()
            Spacer(Modifier.width(0.dp))
            if (badge != null) {
                Spacer(modifier = Modifier.weight(1f))
                badge()
            }
        }
    }
}

internal val DrawerWidth = 320.dp
internal val EdgeWidth = 24.dp
internal const val MinFlingPx = 200f
