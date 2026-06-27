package dev.komrd.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.LocalContentColor

@Composable
@Suppress("LongParameterList")
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = KomrdTheme.colors.surface,
    tonalElevation: Dp = AlertDialogDefaults.elevation,
    properties: DialogProperties = DialogProperties(),
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        Surface(
            modifier = modifier.widthIn(min = AlertDialogDefaults.DialogMinWidth, max = AlertDialogDefaults.DialogMaxWidth),
            shape = shape,
            color = containerColor,
            shadowElevation = tonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(AlertDialogDefaults.DialogPadding),
                horizontalAlignment = Alignment.Start,
            ) {
                if (title != null) {
                    CompositionLocalProvider(LocalContentColor provides KomrdTheme.colors.text) {
                        Box(modifier = Modifier.padding(AlertDialogDefaults.TitlePadding)) { title() }
                    }
                }
                if (text != null) {
                    CompositionLocalProvider(LocalContentColor provides KomrdTheme.colors.text) {
                        Box(modifier = Modifier.padding(AlertDialogDefaults.TextPadding)) { text() }
                    }
                }
                Row(
                    modifier =
                        Modifier.fillMaxWidth().padding(top = AlertDialogDefaults.DialogSpacingBeforeButtons),
                    horizontalArrangement = Arrangement.spacedBy(AlertDialogDefaults.ButtonsSpacing, Alignment.End),
                ) {
                    if (dismissButton != null) {
                        dismissButton()
                    }
                    confirmButton()
                }
            }
        }
    }
}

internal object AlertDialogDefaults {
    val shape: Shape = RoundedCornerShape(28.dp)
    val elevation: Dp = 6.dp
    val DialogMinWidth = 280.dp
    val DialogMaxWidth = 560.dp
    val DialogPadding: PaddingValues = PaddingValues(24.dp)
    val TitlePadding: PaddingValues = PaddingValues(bottom = 16.dp)
    val TextPadding: PaddingValues = PaddingValues(bottom = 24.dp)
    val ButtonsSpacing: Dp = 8.dp
    val DialogSpacingBeforeButtons: Dp = 8.dp
}
