package dev.komrd.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.contentColorFor

@Composable
@Suppress("LongParameterList")
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = FabDefaults.shape,
    containerColor: Color = KomrdTheme.colors.primary,
    contentColor: Color = contentColorFor(containerColor),
    elevation: androidx.compose.ui.unit.Dp = FabDefaults.elevation,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(FabDefaults.iconPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

@Composable
@Suppress("LongParameterList")
fun ExtendedFloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    text: @Composable () -> Unit,
    shape: Shape = FabDefaults.shape,
    containerColor: Color = KomrdTheme.colors.primary,
    contentColor: Color = contentColorFor(containerColor),
    elevation: androidx.compose.ui.unit.Dp = FabDefaults.elevation,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = elevation,
    ) {
        Row(
            modifier = Modifier.padding(FabDefaults.extendedPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) icon()
            text()
        }
    }
}

internal object FabDefaults {
    val shape = RoundedCornerShape(16.dp)
    val elevation = 6.dp
    val iconPadding = PaddingValues(16.dp)
    val extendedPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
}
