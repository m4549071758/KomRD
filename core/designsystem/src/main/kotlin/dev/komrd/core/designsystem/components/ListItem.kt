package dev.komrd.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.foundation.ProvideContentColorTextStyle

@Composable
@Suppress("LongParameterList")
fun ListItem(
    headlineContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    overlineContent: @Composable (() -> Unit)? = null,
    supportingContent: @Composable (() -> Unit)? = null,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    colors: ListItemColors = ListItemDefaults.colors(),
    contentPadding: ListItemPadding = ListItemDefaults.contentPadding,
) {
    val typography = KomrdTheme.typography
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = contentPadding.horizontal, vertical = contentPadding.vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent()
            Spacer(modifier = Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            if (overlineContent != null) {
                ProvideContentColorTextStyle(colors.overlineColor, typography.label2) {
                    overlineContent()
                }
            }
            ProvideContentColorTextStyle(colors.headlineColor, typography.body2) {
                headlineContent()
            }
            if (supportingContent != null) {
                Spacer(modifier = Modifier.height(2.dp))
                ProvideContentColorTextStyle(colors.supportingColor, typography.body3) {
                    supportingContent()
                }
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailingContent()
        }
    }
}

data class ListItemColors(
    val headlineColor: Color,
    val overlineColor: Color,
    val supportingColor: Color,
)

object ListItemDefaults {
    @Composable
    fun colors(
        headlineColor: Color = KomrdTheme.colors.text,
        overlineColor: Color = KomrdTheme.colors.textSecondary,
        supportingColor: Color = KomrdTheme.colors.textSecondary,
    ): ListItemColors = ListItemColors(headlineColor, overlineColor, supportingColor)

    val contentPadding: ListItemPadding = ListItemPadding(horizontal = 16.dp, vertical = 12.dp)
}

data class ListItemPadding(
    val horizontal: androidx.compose.ui.unit.Dp,
    val vertical: androidx.compose.ui.unit.Dp,
)
