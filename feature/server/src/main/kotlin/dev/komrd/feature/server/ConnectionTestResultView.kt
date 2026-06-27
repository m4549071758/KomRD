package dev.komrd.feature.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.AlertDialog
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.Text

@Composable
fun ConnectionTestResultView(
    state: ConnectionTestState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connection_test_title)) },
        text = {
            when (state) {
                is ConnectionTestState.Testing ->
                    Text(stringResource(R.string.connection_test_status_testing))
                is ConnectionTestState.Success ->
                    Text(
                        stringResource(
                            R.string.connection_test_status_success,
                            state.userId?.let { " (user: $it)" } ?: "",
                        ),
                    )
                is ConnectionTestState.Failed ->
                    Text(
                        text = failureMessage(state.error),
                        color = KomrdTheme.colors.error,
                    )
            }
        },
        confirmButton = {
            Button(
                text = stringResource(R.string.connection_test_dismiss_button),
                variant = ButtonVariant.Ghost,
                onClick = onDismiss,
            )
        },
    )
}

@Composable
private fun failureMessage(error: KomgaError): String =
    when (error) {
        is KomgaError.Unauthorized ->
            stringResource(R.string.connection_test_error_unauthorized)
        is KomgaError.UntrustedCertificate ->
            stringResource(
                R.string.connection_test_error_untrusted_certificate,
                error.certificate?.subject
                    ?: stringResource(R.string.connection_test_unknown_certificate_subject),
            )
        is KomgaError.Network ->
            stringResource(R.string.connection_test_error_network, error.message)
        is KomgaError.Http ->
            stringResource(R.string.connection_test_error_http, error.statusCode)
        is KomgaError.Serialization ->
            stringResource(R.string.connection_test_error_serialization)
        is KomgaError.Unknown ->
            stringResource(R.string.connection_test_error_unknown, error.message)
    }
