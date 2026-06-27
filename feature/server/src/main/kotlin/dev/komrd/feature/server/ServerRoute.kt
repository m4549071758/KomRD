package dev.komrd.feature.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ServerRoute(
    onBack: () -> Unit = {},
    onOpenSettings: (dev.komrd.core.model.Server) -> Unit = {},
    viewModel: ServerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ServerScreenScaffold(
        state = state,
        onBack = onBack,
        onAdd = viewModel::onAdd,
        onEdit = viewModel::onEdit,
        onDelete = viewModel::onDelete,
        onSelectActive = viewModel::onSelectActive,
        onVerifyConnection = viewModel::onVerifyConnection,
        onOpenSettings = onOpenSettings,
        onDismissConnectionTest = viewModel::onDismissConnectionTest,
        onConfirmPin = viewModel::onConfirmPin,
        onCancelPin = viewModel::onCancelPin,
        onFormNameChange = viewModel::onFormNameChange,
        onFormUrlChange = viewModel::onFormUrlChange,
        onFormAuthChange = viewModel::onFormAuthChange,
        onFormApiKeyChange = viewModel::onFormApiKeyChange,
        onFormUsernameChange = viewModel::onFormUsernameChange,
        onFormPasswordChange = viewModel::onFormPasswordChange,
        onSaveForm = viewModel::onSaveForm,
        onCancelForm = viewModel::onCancelForm,
    )
}
