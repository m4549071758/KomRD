package dev.komrd.feature.server

import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.Server

/** サーバ一覧の1行分。 */
data class ServerRow(
    val server: Server,
    val isActive: Boolean,
) {
    val authLabel: String
        get() {
            val auth = server.auth
            return when (auth) {
                is AuthMethod.ApiKey -> "API Key"
                is AuthMethod.Basic -> "Basic (${auth.username})"
            }
        }
}

/** 認証方式の選択状態（フォーム用）。 */
sealed interface AuthMethodSelection {
    data object ApiKey : AuthMethodSelection

    data object Basic : AuthMethodSelection
}

/** サーバ追加/編集フォームの状態。 */
data class ServerFormState(
    val editingId: String? = null,
    val name: String = "",
    val baseUrl: String = "",
    val authMethod: AuthMethodSelection = AuthMethodSelection.ApiKey,
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val nameError: String? = null,
    val urlError: String? = null,
    val saving: Boolean = false,
    val saveError: String? = null,
) {
    val isNew: Boolean get() = editingId == null
}

/** 接続テストの結果表示状態。 */
sealed interface ConnectionTestState {
    data class Testing(
        val serverId: String,
    ) : ConnectionTestState

    data class Success(
        val serverId: String,
        val userId: String?,
    ) : ConnectionTestState

    data class Failed(
        val serverId: String,
        val error: KomgaError,
    ) : ConnectionTestState
}

/** TLS信頼確認ダイアログの状態。 */
data class TrustDialogState(
    val serverId: String,
    val certificate: CertificateInfo,
    val mismatch: Boolean,
    val error: String? = null,
)

/** ServerRouteのUI状態。 */
data class ServerUiState(
    val loading: Boolean = true,
    val servers: List<ServerRow> = emptyList(),
    val activeServerId: String? = null,
    val form: ServerFormState? = null,
    val connectionTest: ConnectionTestState? = null,
    val trustDialog: TrustDialogState? = null,
)
