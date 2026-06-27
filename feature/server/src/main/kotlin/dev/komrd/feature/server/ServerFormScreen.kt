package dev.komrd.feature.server

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.SegmentedButton
import dev.komrd.core.designsystem.components.SegmentedButtonDefaults
import dev.komrd.core.designsystem.components.SingleChoiceSegmentedButtonRow
import dev.komrd.core.designsystem.components.Text
import dev.komrd.core.designsystem.components.textfield.OutlinedTextField

@Suppress("LongParameterList", "LongMethod")
@Composable
fun ServerFormScreen(
    form: ServerFormState,
    onNameChange: (String) -> Unit,
    onUrlChange: (String) -> Unit,
    onAuthChange: (AuthMethodSelection) -> Unit,
    onApiKeyChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(if (form.isNew) R.string.server_form_title_add else R.string.server_form_title_edit))
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.server_form_label_name)) },
                isError = form.nameError != null,
                supportingText = form.nameError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = form.baseUrl,
                onValueChange = onUrlChange,
                label = { Text(stringResource(R.string.server_form_label_base_url)) },
                isError = form.urlError != null,
                supportingText = form.urlError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Text(text = stringResource(R.string.server_form_label_auth))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = form.authMethod is AuthMethodSelection.ApiKey,
                    onClick = { onAuthChange(AuthMethodSelection.ApiKey) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text(stringResource(R.string.server_form_auth_api_key)) }
                SegmentedButton(
                    selected = form.authMethod is AuthMethodSelection.Basic,
                    onClick = { onAuthChange(AuthMethodSelection.Basic) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) { Text(stringResource(R.string.server_form_auth_basic)) }
            }
            when (form.authMethod) {
                AuthMethodSelection.ApiKey ->
                    OutlinedTextField(
                        value = form.apiKey,
                        onValueChange = onApiKeyChange,
                        label = { Text(stringResource(R.string.server_form_label_api_key)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                AuthMethodSelection.Basic -> {
                    OutlinedTextField(
                        value = form.username,
                        onValueChange = onUsernameChange,
                        label = { Text(stringResource(R.string.server_form_label_username)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = form.password,
                        onValueChange = onPasswordChange,
                        label = { Text(stringResource(R.string.server_form_label_password)) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        }
        form.saveError?.let { error ->
            Text(text = error, color = KomrdTheme.colors.error)
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                text = stringResource(R.string.action_cancel),
                variant = ButtonVariant.Ghost,
                onClick = onCancel,
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.action_save),
                variant = ButtonVariant.Primary,
                onClick = onSave,
                modifier = Modifier.weight(1f),
                enabled = !form.saving,
            )
        }
    }
}
