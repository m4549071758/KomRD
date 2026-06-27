package dev.komrd.core.designsystem.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.komrd.core.designsystem.KomrdTheme

@Composable
@Suppress("LongParameterList")
fun DropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: IntOffset = IntOffset(0, 0),
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!expanded) return
    Popup(
        alignment = Alignment.TopStart,
        offset = offset,
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(
            modifier = modifier.widthIn(min = 112.dp, max = 280.dp),
            shape = DropdownMenuDefaults.shape,
            color = KomrdTheme.colors.surface,
            shadowElevation = DropdownMenuDefaults.elevation,
        ) {
            Column(modifier = Modifier.padding(DropdownMenuDefaults.contentPadding)) {
                content()
            }
        }
    }
}

@Composable
fun DropdownMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            leadingIcon()
            androidx.compose.foundation.layout
                .Spacer(Modifier.width(12.dp))
        }
        text()
        if (trailingIcon != null) {
            androidx.compose.foundation.layout
                .Spacer(Modifier.width(12.dp))
            trailingIcon()
        }
    }
}

internal object DropdownMenuDefaults {
    val shape =
        androidx.compose.foundation.shape
            .RoundedCornerShape(12.dp)
    val elevation = 6.dp
    val contentPadding: PaddingValues = PaddingValues(vertical = 8.dp)
}
