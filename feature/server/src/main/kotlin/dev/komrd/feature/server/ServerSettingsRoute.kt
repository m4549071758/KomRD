package dev.komrd.feature.server

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ServerSettingsRoute(
    serverId: String,
    onBack: () -> Unit,
    viewModel: ServerSettingsViewModel = hiltViewModel(),
) {
    LaunchedEffect(serverId) { viewModel.bind(serverId) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    ServerSettingsScreen(
        state = state,
        onBack = onBack,
        onSave = { viewModel.onSave(serverId) },
        onDismissFeedback = viewModel::onDismissFeedback,
        onDeleteEmptyCollectionsChange = viewModel::onDeleteEmptyCollectionsChange,
        onDeleteEmptyReadListsChange = viewModel::onDeleteEmptyReadListsChange,
        onTaskPoolSizeChange = viewModel::onTaskPoolSizeChange,
        onRememberMeDurationDaysChange = viewModel::onRememberMeDurationDaysChange,
        onRenewRememberMeKeyChange = viewModel::onRenewRememberMeKeyChange,
        onServerPortChange = viewModel::onServerPortChange,
        onServerContextPathChange = viewModel::onServerContextPathChange,
        onThumbnailSizeChange = viewModel::onThumbnailSizeChange,
        onKoboPortChange = viewModel::onKoboPortChange,
        onKoboProxyChange = viewModel::onKoboProxyChange,
    )
}
