package dev.komrd.feature.server

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.komrd.core.designsystem.KomrdTheme
import dev.komrd.core.designsystem.components.AlertDialog
import dev.komrd.core.designsystem.components.Button
import dev.komrd.core.designsystem.components.ButtonVariant
import dev.komrd.core.designsystem.components.Text
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TrustConfirmationDialog(
    state: TrustDialogState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val cert = state.certificate
    val validityPeriod =
        stringResource(
            R.string.trust_dialog_validity_period,
            formatDate(cert.notBefore),
            formatDate(cert.notAfter),
        )
    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                if (state.mismatch) {
                    stringResource(R.string.trust_dialog_title_mismatch)
                } else {
                    stringResource(R.string.trust_dialog_title)
                },
            )
        },
        text = {
            state.error?.let { error ->
                Text(
                    text = error,
                    style = KomrdTheme.typography.body2,
                    color = KomrdTheme.colors.error,
                )
            }
            if (state.mismatch) {
                Text(
                    text = stringResource(R.string.trust_dialog_mismatch_message),
                    style = KomrdTheme.typography.body2,
                    color = KomrdTheme.colors.error,
                )
            }
            Text(text = "Subject: ${cert.subject}", style = KomrdTheme.typography.body3)
            Text(text = "Issuer: ${cert.issuer}", style = KomrdTheme.typography.body3)
            Text(text = "SHA-256: ${cert.sha256Fingerprint}", style = KomrdTheme.typography.body3)
            Text(
                text = validityPeriod,
                style = KomrdTheme.typography.body3,
            )
        },
        confirmButton = {
            Button(
                text = stringResource(R.string.trust_dialog_confirm_button),
                variant = ButtonVariant.Ghost,
                onClick = onConfirm,
            )
        },
        dismissButton = {
            Button(
                text = stringResource(R.string.trust_dialog_dismiss_button),
                variant = ButtonVariant.Ghost,
                onClick = onCancel,
            )
        },
    )
}

private fun formatDate(instant: java.time.Instant): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault())
    return formatter.format(instant)
}
