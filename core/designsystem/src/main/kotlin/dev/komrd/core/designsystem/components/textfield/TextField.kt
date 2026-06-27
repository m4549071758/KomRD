package dev.komrd.core.designsystem.components.textfield

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.textfield.base.CommonDecorationBox
import dev.komrd.core.designsystem.components.textfield.base.FocusedOutlineThickness
import dev.komrd.core.designsystem.components.textfield.base.HorizontalIconPadding
import dev.komrd.core.designsystem.components.textfield.base.LabelBottomPadding
import dev.komrd.core.designsystem.components.textfield.base.SupportingTopPadding
import dev.komrd.core.designsystem.components.textfield.base.TextFieldColors
import dev.komrd.core.designsystem.components.textfield.base.TextFieldHorizontalPadding
import dev.komrd.core.designsystem.components.textfield.base.TextFieldMinHeight
import dev.komrd.core.designsystem.components.textfield.base.TextFieldVerticalPadding
import dev.komrd.core.designsystem.components.textfield.base.UnfocusedOutlineThickness
import dev.komrd.core.designsystem.components.textfield.base.containerOutline

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = KomrdTheme.typography.input,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    placeholder: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    shape: Shape = TextFieldDefaults.Shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    cursorBrush: Brush = SolidColor(colors.cursorColor(isError).value),
) {
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled, isError, interactionSource).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(LocalTextSelectionColors provides colors.selectionColors) {
        BasicTextField(
            modifier =
                modifier
                    .defaultMinSize(
                        minHeight = TextFieldDefaults.MinHeight,
                    ).fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value,
                    innerTextField = innerTextField,
                    visualTransformation = visualTransformation,
                    label = label,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    prefix = prefix,
                    suffix = suffix,
                    supportingText = supportingText,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape,
                )
            },
        )
    }
}

@Composable
fun TextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = KomrdTheme.typography.input,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    placeholder: @Composable (() -> Unit)? = null,
    prefix: @Composable (() -> Unit)? = null,
    suffix: @Composable (() -> Unit)? = null,
    label: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    shape: Shape = TextFieldDefaults.Shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    cursorBrush: Brush = SolidColor(colors.cursorColor(isError).value),
) {
    val textColor =
        textStyle.color.takeOrElse {
            colors.textColor(enabled, isError, interactionSource).value
        }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))

    CompositionLocalProvider(LocalTextSelectionColors provides colors.selectionColors) {
        BasicTextField(
            modifier =
                modifier
                    .defaultMinSize(
                        minHeight = TextFieldDefaults.MinHeight,
                    ).fillMaxWidth(),
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            readOnly = readOnly,
            textStyle = mergedTextStyle,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            visualTransformation = visualTransformation,
            onTextLayout = onTextLayout,
            interactionSource = interactionSource,
            cursorBrush = cursorBrush,
            decorationBox = @Composable { innerTextField ->
                TextFieldDefaults.DecorationBox(
                    value = value.text,
                    innerTextField = innerTextField,
                    visualTransformation = visualTransformation,
                    label = label,
                    placeholder = placeholder,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    prefix = prefix,
                    suffix = suffix,
                    supportingText = supportingText,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    shape = shape,
                )
            },
        )
    }
}

@Immutable
object TextFieldDefaults {
    val MinHeight = TextFieldMinHeight
    val Shape: Shape = RoundedCornerShape(8.dp)

    private fun contentPadding(
        start: Dp = TextFieldHorizontalPadding,
        end: Dp = TextFieldHorizontalPadding,
        top: Dp = TextFieldVerticalPadding,
        bottom: Dp = TextFieldVerticalPadding,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    private fun labelPadding(
        start: Dp = 0.dp,
        top: Dp = 0.dp,
        end: Dp = 0.dp,
        bottom: Dp = LabelBottomPadding,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    private fun supportingTextPadding(
        start: Dp = 0.dp,
        top: Dp = SupportingTopPadding,
        end: Dp = TextFieldHorizontalPadding,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    private fun leadingIconPadding(
        start: Dp = HorizontalIconPadding,
        top: Dp = 0.dp,
        end: Dp = 0.dp,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    private fun trailingIconPadding(
        start: Dp = 0.dp,
        top: Dp = 0.dp,
        end: Dp = HorizontalIconPadding,
        bottom: Dp = 0.dp,
    ): PaddingValues = PaddingValues(start, top, end, bottom)

    @Composable
    fun containerBorderThickness(interactionSource: InteractionSource): Dp {
        val focused by interactionSource.collectIsFocusedAsState()

        return if (focused) FocusedOutlineThickness else UnfocusedOutlineThickness
    }

    @Composable
    fun DecorationBox(
        value: String,
        innerTextField: @Composable () -> Unit,
        enabled: Boolean,
        visualTransformation: VisualTransformation,
        interactionSource: InteractionSource,
        isError: Boolean = false,
        label: @Composable (() -> Unit)? = null,
        placeholder: @Composable (() -> Unit)? = null,
        leadingIcon: @Composable (() -> Unit)? = null,
        trailingIcon: @Composable (() -> Unit)? = null,
        prefix: @Composable (() -> Unit)? = null,
        suffix: @Composable (() -> Unit)? = null,
        supportingText: @Composable (() -> Unit)? = null,
        shape: Shape = Shape,
        colors: TextFieldColors = colors(),
        container: @Composable () -> Unit = {
            ContainerBox(enabled, isError, interactionSource, colors, shape)
        },
    ) {
        CommonDecorationBox(
            value = value,
            innerTextField = innerTextField,
            visualTransformation = visualTransformation,
            placeholder = placeholder,
            label = label,
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            prefix = prefix,
            suffix = suffix,
            supportingText = supportingText,
            enabled = enabled,
            isError = isError,
            interactionSource = interactionSource,
            colors = colors,
            contentPadding = contentPadding(),
            labelPadding = labelPadding(),
            supportingTextPadding = supportingTextPadding(),
            leadingIconPadding = leadingIconPadding(),
            trailingIconPadding = trailingIconPadding(),
            container = container,
        )
    }

    @Composable
    fun ContainerBox(
        enabled: Boolean,
        isError: Boolean,
        interactionSource: InteractionSource,
        colors: TextFieldColors,
        shape: Shape = Shape,
        borderThickness: Dp = containerBorderThickness(interactionSource),
    ) {
        Box(
            Modifier
                .background(colors.containerColor(enabled, isError, interactionSource).value, shape)
                .containerOutline(enabled, isError, interactionSource, colors, borderThickness, shape),
        )
    }

    @Composable
    fun colors(): TextFieldColors =
        TextFieldColors(
            focusedTextColor = KomrdTheme.colors.text,
            unfocusedTextColor = KomrdTheme.colors.text,
            disabledTextColor = KomrdTheme.colors.onDisabled,
            errorTextColor = KomrdTheme.colors.text,
            focusedContainerColor = KomrdTheme.colors.surface,
            unfocusedContainerColor = KomrdTheme.colors.surface,
            disabledContainerColor = KomrdTheme.colors.disabled,
            errorContainerColor = KomrdTheme.colors.surface,
            cursorColor = KomrdTheme.colors.primary,
            errorCursorColor = KomrdTheme.colors.error,
            textSelectionColors = LocalTextSelectionColors.current,
            focusedOutlineColor = KomrdTheme.colors.transparent,
            unfocusedOutlineColor = KomrdTheme.colors.transparent,
            disabledOutlineColor = KomrdTheme.colors.transparent,
            errorOutlineColor = KomrdTheme.colors.error,
            focusedLeadingIconColor = KomrdTheme.colors.primary,
            unfocusedLeadingIconColor = KomrdTheme.colors.primary,
            disabledLeadingIconColor = KomrdTheme.colors.onDisabled,
            errorLeadingIconColor = KomrdTheme.colors.primary,
            focusedTrailingIconColor = KomrdTheme.colors.primary,
            unfocusedTrailingIconColor = KomrdTheme.colors.primary,
            disabledTrailingIconColor = KomrdTheme.colors.onDisabled,
            errorTrailingIconColor = KomrdTheme.colors.primary,
            focusedLabelColor = KomrdTheme.colors.primary,
            unfocusedLabelColor = KomrdTheme.colors.primary,
            disabledLabelColor = KomrdTheme.colors.textDisabled,
            errorLabelColor = KomrdTheme.colors.error,
            focusedPlaceholderColor = KomrdTheme.colors.textSecondary,
            unfocusedPlaceholderColor = KomrdTheme.colors.textSecondary,
            disabledPlaceholderColor = KomrdTheme.colors.textDisabled,
            errorPlaceholderColor = KomrdTheme.colors.textSecondary,
            focusedSupportingTextColor = KomrdTheme.colors.primary,
            unfocusedSupportingTextColor = KomrdTheme.colors.primary,
            disabledSupportingTextColor = KomrdTheme.colors.textDisabled,
            errorSupportingTextColor = KomrdTheme.colors.error,
            focusedPrefixColor = KomrdTheme.colors.primary,
            unfocusedPrefixColor = KomrdTheme.colors.primary,
            disabledPrefixColor = KomrdTheme.colors.onDisabled,
            errorPrefixColor = KomrdTheme.colors.primary,
            focusedSuffixColor = KomrdTheme.colors.primary,
            unfocusedSuffixColor = KomrdTheme.colors.primary,
            disabledSuffixColor = KomrdTheme.colors.onDisabled,
            errorSuffixColor = KomrdTheme.colors.primary,
        )
}
