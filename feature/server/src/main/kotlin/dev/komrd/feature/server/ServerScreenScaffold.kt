package dev.komrd.feature.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.DropdownMenu
import dev.komrd.core.designsystem.components.DropdownMenuItem
import dev.komrd.core.designsystem.components.ExtendedFloatingActionButton
import dev.komrd.core.designsystem.components.Icon
import dev.komrd.core.designsystem.components.IconButton
import dev.komrd.core.designsystem.components.IconButtonVariant
import dev.komrd.core.designsystem.components.Scaffold
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.progressindicators.CircularProgressIndicator
import dev.komrd.core.designsystem.components.topbar.TopBar
import dev.komrd.core.model.Server

@Suppress("LongParameterList", "LongMethod")
@Composable
fun ServerScreenScaffold(
    state: ServerUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Server) -> Unit,
    onDelete: (String) -> Unit,
    onSelectActive: (String) -> Unit,
    onVerifyConnection: (Server) -> Unit,
    onOpenSettings: (Server) -> Unit,
    onDismissConnectionTest: () -> Unit,
    onConfirmPin: () -> Unit,
    onCancelPin: () -> Unit,
    onFormNameChange: (String) -> Unit,
    onFormUrlChange: (String) -> Unit,
    onFormAuthChange: (AuthMethodSelection) -> Unit,
    onFormApiKeyChange: (String) -> Unit,
    onFormUsernameChange: (String) -> Unit,
    onFormPasswordChange: (String) -> Unit,
    onSaveForm: () -> Unit,
    onCancelForm: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopBar {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onBack,
                        variant = IconButtonVariant.Ghost,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.server_back_button),
                        )
                    }
                    Text(
                        text = stringResource(R.string.server_screen_title),
                        style = KomrdTheme.typography.h3,
                        modifier = Modifier.weight(1f).padding(start = 16.dp),
                    )
                }
            }
        },
        floatingActionButton = {
            if (state.form == null) {
                ExtendedFloatingActionButton(
                    onClick = onAdd,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.server_add_fab)) },
                )
            }
        },
    ) { padding ->
        val form = state.form
        if (form != null) {
            ServerFormScreen(
                form = form,
                onNameChange = onFormNameChange,
                onUrlChange = onFormUrlChange,
                onAuthChange = onFormAuthChange,
                onApiKeyChange = onFormApiKeyChange,
                onUsernameChange = onFormUsernameChange,
                onPasswordChange = onFormPasswordChange,
                onSave = onSaveForm,
                onCancel = onCancelForm,
                modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            )
        } else if (state.loading) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding =
                    androidx.compose.foundation.layout
                        .PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.servers, key = { it.server.id }) { row ->
                    ServerRowItem(
                        row = row,
                        onVerify = { onVerifyConnection(row.server) },
                        onEdit = { onEdit(row.server) },
                        onDelete = { onDelete(row.server.id) },
                        onSetActive = { onSelectActive(row.server.id) },
                        onOpenSettings = { onOpenSettings(row.server) },
                    )
                }
            }
        }
    }

    state.connectionTest?.let { test ->
        ConnectionTestResultView(
            state = test,
            onDismiss = onDismissConnectionTest,
        )
    }

    state.trustDialog?.let { dialog ->
        TrustConfirmationDialog(
            state = dialog,
            onConfirm = onConfirmPin,
            onCancel = onCancelPin,
        )
    }
}

@Composable
private fun ServerRowItem(
    row: ServerRow,
    onVerify: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetActive: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = row.server.name + if (row.isActive) stringResource(R.string.server_active_suffix) else "",
            style = KomrdTheme.typography.h3,
        )
        Text(text = row.server.baseUrl, style = KomrdTheme.typography.body3)
        Text(text = row.authLabel, style = KomrdTheme.typography.body3)
        Column(modifier = Modifier.padding(top = 4.dp)) {
            if (!row.isActive) {
                Button(
                    text = stringResource(R.string.server_activate_button),
                    variant = ButtonVariant.Ghost,
                    onClick = onSetActive,
                )
            }
            Button(
                text = stringResource(R.string.server_connection_test_button),
                variant = ButtonVariant.Ghost,
                onClick = onVerify,
            )
            IconButton(
                onClick = { menuOpen = true },
                variant = IconButtonVariant.Ghost,
            ) {
                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.server_menu_button))
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.server_edit_menu_item)) }, onClick = {
                    menuOpen = false
                    onEdit()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.server_settings_menu_item)) }, onClick = {
                    menuOpen = false
                    onOpenSettings()
                })
                DropdownMenuItem(text = { Text(stringResource(R.string.server_delete_menu_item)) }, onClick = {
                    menuOpen = false
                    onDelete()
                })
            }
        }
    }
}
