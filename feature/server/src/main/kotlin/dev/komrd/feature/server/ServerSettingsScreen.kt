package dev.komrd.feature.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.DropdownMenu
import dev.komrd.core.designsystem.components.DropdownMenuItem
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Switch
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.progressindicators.CircularProgressIndicator
import dev.komrd.core.designsystem.components.textfield.OutlinedTextField
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.UserAccount

@Suppress("LongParameterList", "LongMethod")
@Composable
fun ServerSettingsScreen(
    state: ServerSettingsUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onDismissFeedback: () -> Unit,
    onDeleteEmptyCollectionsChange: (Boolean) -> Unit,
    onDeleteEmptyReadListsChange: (Boolean) -> Unit,
    onTaskPoolSizeChange: (String) -> Unit,
    onRememberMeDurationDaysChange: (String) -> Unit,
    onRenewRememberMeKeyChange: (Boolean) -> Unit,
    onServerPortChange: (String) -> Unit,
    onServerContextPathChange: (String) -> Unit,
    onThumbnailSizeChange: (String) -> Unit,
    onKoboPortChange: (String) -> Unit,
    onKoboProxyChange: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.server_back_button),
                        )
                    }
                    Text(
                        text = stringResource(R.string.server_settings_menu_item),
                        style = KomrdTheme.typography.h3,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            }
        },
    ) { padding ->
        when (val s = state) {
            is ServerSettingsUiState.Loading ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
            is ServerSettingsUiState.Error ->
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text(s.message, style = KomrdTheme.typography.body2) }
            is ServerSettingsUiState.Content ->
                ServerSettingsContent(
                    content = s,
                    padding = padding,
                    onSave = onSave,
                    onDismissFeedback = onDismissFeedback,
                    onDeleteEmptyCollectionsChange = onDeleteEmptyCollectionsChange,
                    onDeleteEmptyReadListsChange = onDeleteEmptyReadListsChange,
                    onTaskPoolSizeChange = onTaskPoolSizeChange,
                    onRememberMeDurationDaysChange = onRememberMeDurationDaysChange,
                    onRenewRememberMeKeyChange = onRenewRememberMeKeyChange,
                    onServerPortChange = onServerPortChange,
                    onServerContextPathChange = onServerContextPathChange,
                    onThumbnailSizeChange = onThumbnailSizeChange,
                    onKoboPortChange = onKoboPortChange,
                    onKoboProxyChange = onKoboProxyChange,
                )
        }
    }
}

@Suppress("LongParameterList", "LongMethod")
@Composable
private fun ServerSettingsContent(
    content: ServerSettingsUiState.Content,
    padding: androidx.compose.foundation.layout.PaddingValues,
    onSave: () -> Unit,
    onDismissFeedback: () -> Unit,
    onDeleteEmptyCollectionsChange: (Boolean) -> Unit,
    onDeleteEmptyReadListsChange: (Boolean) -> Unit,
    onTaskPoolSizeChange: (String) -> Unit,
    onRememberMeDurationDaysChange: (String) -> Unit,
    onRenewRememberMeKeyChange: (Boolean) -> Unit,
    onServerPortChange: (String) -> Unit,
    onServerContextPathChange: (String) -> Unit,
    onThumbnailSizeChange: (String) -> Unit,
    onKoboPortChange: (String) -> Unit,
    onKoboProxyChange: (Boolean) -> Unit,
) {
    val form = content.form
    val editable = content.isAdmin
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!editable) {
            Text(
                text = stringResource(R.string.server_settings_read_only_message),
                style = KomrdTheme.typography.body2,
                color = KomrdTheme.colors.error,
            )
        }

        UserInfoSection(user = content.user, isAdmin = content.isAdmin)

        Text(
            text = stringResource(R.string.server_settings_menu_item),
            style = KomrdTheme.typography.h3,
        )
        ToggleRow(
            label = stringResource(R.string.server_settings_delete_empty_collections),
            checked = form.deleteEmptyCollections,
            enabled = editable,
            onCheckedChange = onDeleteEmptyCollectionsChange,
        )
        ToggleRow(
            label = stringResource(R.string.server_settings_delete_empty_read_lists),
            checked = form.deleteEmptyReadLists,
            enabled = editable,
            onCheckedChange = onDeleteEmptyReadListsChange,
        )
        OutlinedTextField(
            value = form.taskPoolSize,
            onValueChange = onTaskPoolSizeChange,
            label = { Text(stringResource(R.string.server_settings_task_pool_size)) },
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = form.rememberMeDurationDays,
            onValueChange = onRememberMeDurationDaysChange,
            label = { Text(stringResource(R.string.server_settings_remember_me_duration_days)) },
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ToggleRow(
            label = stringResource(R.string.server_settings_renew_remember_me_key),
            checked = form.renewRememberMeKey,
            enabled = editable,
            onCheckedChange = onRenewRememberMeKeyChange,
        )
        OutlinedTextField(
            value = form.serverPort,
            onValueChange = onServerPortChange,
            label = { Text(stringResource(R.string.server_settings_server_port)) },
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        OutlinedTextField(
            value = form.serverContextPath,
            onValueChange = onServerContextPathChange,
            label = { Text(stringResource(R.string.server_settings_context_path)) },
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        ThumbnailSizeSelector(
            selected = form.thumbnailSize,
            enabled = editable,
            onSelected = onThumbnailSizeChange,
        )
        OutlinedTextField(
            value = form.koboPort,
            onValueChange = onKoboPortChange,
            label = { Text(stringResource(R.string.server_settings_kobo_port)) },
            enabled = editable,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        ToggleRow(
            label = stringResource(R.string.server_settings_kobo_proxy),
            checked = form.koboProxy,
            enabled = editable,
            onCheckedChange = onKoboProxyChange,
        )

        content.feedback?.let { feedback ->
            val (message, color) =
                when (feedback) {
                    is ServerSettingsFeedback.Success -> feedback.message to KomrdTheme.colors.textSecondary
                    is ServerSettingsFeedback.Failure -> feedback.message to KomrdTheme.colors.error
                }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = message, style = KomrdTheme.typography.body2, color = color)
                Button(
                    text = stringResource(R.string.connection_test_dismiss_button),
                    variant = ButtonVariant.Ghost,
                    onClick = onDismissFeedback,
                )
            }
        }

        Button(
            text = stringResource(R.string.action_save),
            variant = ButtonVariant.Primary,
            onClick = onSave,
            enabled = editable && !content.saving,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** User information section (M8-04): displays role, shared libraries, and age restriction. */
@Composable
private fun UserInfoSection(
    user: UserAccount?,
    isAdmin: Boolean,
) {
    if (user == null) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = stringResource(R.string.server_settings_user_info),
            style = KomrdTheme.typography.h3,
        )
        Text(
            text =
                stringResource(
                    R.string.server_settings_role_format,
                    if (isAdmin) "ADMIN" else user.roles.joinToString("/").ifBlank { "USER" },
                ),
            style = KomrdTheme.typography.body2,
        )
        val sharedLabel =
            if (user.sharedAllLibraries) {
                stringResource(R.string.server_settings_share_all_libraries)
            } else {
                stringResource(
                    R.string.server_settings_shared_libraries_format,
                    user.sharedLibrariesIds
                        .joinToString(", ")
                        .ifBlank { stringResource(R.string.server_settings_shared_libraries_none) },
                )
            }
        Text(text = sharedLabel, style = KomrdTheme.typography.body2)
        user.ageRestriction?.let { ar ->
            Text(
                text =
                    stringResource(
                        R.string.server_settings_age_restriction_format,
                        ar.restriction,
                        ar.age,
                    ),
                style = KomrdTheme.typography.body2,
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = KomrdTheme.typography.body2)
        Switch(
            checked = checked,
            onCheckedChange = if (enabled) onCheckedChange else null,
            enabled = enabled,
        )
    }
}

@Composable
private fun ThumbnailSizeSelector(
    selected: String,
    enabled: Boolean,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.server_settings_thumbnail_size),
            style = KomrdTheme.typography.body2,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = selected.ifBlank { stringResource(R.string.server_settings_thumbnail_size_unset) },
                style = KomrdTheme.typography.body2,
                color = KomrdTheme.colors.textSecondary,
            )
            IconButton(
                onClick = { if (enabled) expanded = true },
                variant = dev.komrd.core.designsystem.components.IconButtonVariant.Ghost,
                enabled = enabled,
            ) {
                Icon(
                    Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(R.string.server_settings_thumbnail_size_select),
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                ThumbnailSizeOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            expanded = false
                            onSelected(option)
                        },
                    )
                }
            }
        }
    }
}
