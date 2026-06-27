package dev.komrd.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.foundation.ripple

@Composable
fun RadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors = KomrdTheme.colors
    val dotColor =
        animateColorAsState(
            targetValue =
                when {
                    !enabled -> colors.disabled
                    selected -> colors.primary
                    else -> Color.Transparent
                },
            animationSpec = tween(durationMillis = 100),
            label = "radioDot",
        )
    val ringColor =
        animateColorAsState(
            targetValue =
                when {
                    !enabled -> colors.disabled
                    selected -> colors.primary
                    else -> colors.outline
                },
            animationSpec = tween(durationMillis = 100),
            label = "radioRing",
        )

    val selectableModifier =
        if (onClick != null) {
            Modifier
                .requiredSize(MinInteractiveSize)
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    enabled = enabled,
                    role = Role.RadioButton,
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false, radius = MinInteractiveSize / 2),
                )
        } else {
            Modifier
        }

    Canvas(
        modifier
            .then(selectableModifier)
            .wrapContentSize(Alignment.Center)
            .requiredSize(RadioSize),
    ) {
        val strokeWidth = StrokeWidthPx.toPx()
        drawCircle(
            color = ringColor.value,
            radius = (size.minDimension - strokeWidth) / 2f,
            style = Stroke(width = strokeWidth),
        )
        if (selected) {
            drawCircle(
                color = dotColor.value,
                radius = DotRadius.toPx(),
                style = Fill,
            )
        }
    }
}

private val RadioSize = 20.dp
private val DotRadius = 5.dp
private val StrokeWidthPx = 2.dp
private val MinInteractiveSize = 44.dp
