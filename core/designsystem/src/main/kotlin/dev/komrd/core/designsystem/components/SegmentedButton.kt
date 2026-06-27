package dev.komrd.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.contentColorFor

@Composable
fun SingleChoiceSegmentedButtonRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(modifier = modifier.fillMaxWidth(), content = content)
}

@Composable
@Suppress("LongParameterList")
fun RowScope.SegmentedButton(
    selected: Boolean,
    onClick: () -> Unit,
    shape: Shape,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = KomrdTheme.colors
    val containerColor = if (selected) colors.secondary else colors.surface
    val borderColor = colors.outline
    Surface(
        onClick = onClick,
        modifier = modifier.weight(1f),
        enabled = enabled,
        shape = shape,
        color = containerColor,
        contentColor = contentColorFor(containerColor),
        border = BorderStroke(SegmentedButtonDefaults.borderWidth, borderColor),
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.padding(SegmentedButtonDefaults.contentPadding),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            content()
        }
    }
}

object SegmentedButtonDefaults {
    val borderWidth = 1.dp
    val contentPadding =
        androidx.compose.foundation.layout
            .PaddingValues(horizontal = 16.dp, vertical = 8.dp)

    fun itemShape(
        index: Int,
        count: Int,
    ): Shape {
        if (count <= 1) return RoundedCornerShape(8.dp)
        val corner = 8.dp
        return when (index) {
            0 -> RoundedCornerShape(topStart = corner, bottomStart = corner)
            count - 1 -> RoundedCornerShape(topEnd = corner, bottomEnd = corner)
            else -> RectangleShape
        }
    }
}
