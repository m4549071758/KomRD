package dev.komrd.feature.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Server
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
@Suppress("TooManyFunctions")
class ServerViewModel
    @Inject
    constructor(
        private val repository: ServerRepository,
        private val activeServerStore: ActiveServerStore,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(ServerUiState())

        val uiState: StateFlow<ServerUiState> = mutableState.asStateFlow()

        init {
            viewModelScope.launch {
                combine(repository.servers, activeServerStore.activeServerId) { servers, activeId ->
                    ServerUiState(
                        loading = false,
                        servers = servers.map { server -> ServerRow(server, isActive = server.id == activeId) },
                        activeServerId = activeId,
                    )
                }.collect { ready ->
                    mutableState.update { current ->
                        // フォーム/ダイアログ/接続テストの状態は保持したまま一覧部分だけ更新
                        ready.copy(
                            form = current.form,
                            connectionTest = current.connectionTest,
                            trustDialog = current.trustDialog,
                        )
                    }
                }
            }
        }

        fun onAdd() {
            mutableState.update { it.copy(form = ServerFormState()) }
        }

        fun onEdit(server: Server) {
            val authMethod =
                when (server.auth) {
                    is AuthMethod.ApiKey -> AuthMethodSelection.ApiKey
                    is AuthMethod.Basic -> AuthMethodSelection.Basic
                }
            mutableState.update {
                it.copy(
                    form =
                        ServerFormState(
                            editingId = server.id,
                            name = server.name,
                            baseUrl = server.baseUrl,
                            authMethod = authMethod,
                            apiKey = (server.auth as? AuthMethod.ApiKey)?.key.orEmpty(),
                            username = (server.auth as? AuthMethod.Basic)?.username.orEmpty(),
                            password = (server.auth as? AuthMethod.Basic)?.password.orEmpty(),
                        ),
                )
            }
        }

        fun onCancelForm() {
            mutableState.update { it.copy(form = null) }
        }

        fun onFormNameChange(value: String) = updateForm { it.copy(name = value, nameError = null) }

        fun onFormUrlChange(value: String) = updateForm { it.copy(baseUrl = value, urlError = null) }

        fun onFormAuthChange(value: AuthMethodSelection) = updateForm { it.copy(authMethod = value) }

        fun onFormApiKeyChange(value: String) = updateForm { it.copy(apiKey = value) }

        fun onFormUsernameChange(value: String) = updateForm { it.copy(username = value) }

        fun onFormPasswordChange(value: String) = updateForm { it.copy(password = value) }

        fun onSaveForm() {
            val form = mutableState.value.form ?: return
            val nameError = if (form.name.isBlank()) "名前を入力してください" else null
            val urlError = validateBaseUrl(form.baseUrl)
            if (nameError != null || urlError != null) {
                updateForm { it.copy(nameError = nameError, urlError = urlError) }
                return
            }
            val auth =
                when (form.authMethod) {
                    AuthMethodSelection.ApiKey -> AuthMethod.ApiKey(form.apiKey)
                    AuthMethodSelection.Basic -> AuthMethod.Basic(form.username, form.password)
                }
            val id = form.editingId ?: UUID.randomUUID().toString()
            val server =
                Server(
                    id = id,
                    name = form.name.trim(),
                    baseUrl = form.baseUrl.trim().trimEnd('/'),
                    auth = auth,
                )
            viewModelScope.launch {
                updateForm { it.copy(saving = true, saveError = null) }
                // 例外を viewModelScope 既定ハンドラへ伝播させてクラッシュさせない。
                runCatching {
                    if (form.editingId == null) repository.add(server) else repository.update(server)
                }.onSuccess {
                    if (form.editingId == null) activeServerStore.setActive(id)
                    mutableState.update { it.copy(form = null) }
                }.onFailure { error ->
                    updateForm { it.copy(saving = false, saveError = error.message ?: "保存に失敗しました") }
                }
            }
        }

        fun onDelete(id: String) {
            viewModelScope.launch {
                repository.delete(id)
                if (mutableState.value.activeServerId == id) activeServerStore.clear()
            }
        }

        fun onSelectActive(id: String) {
            viewModelScope.launch { activeServerStore.setActive(id) }
        }

        fun onVerifyConnection(server: Server) {
            viewModelScope.launch {
                mutableState.update { it.copy(connectionTest = ConnectionTestState.Testing(server.id)) }
                val result = repository.verifyConnection(server)
                val state =
                    when (result) {
                        is KomgaResult.Success -> {
                            val userId = (result.value as? ConnectionResult.Authenticated)?.userId
                            ConnectionTestState.Success(server.id, userId)
                        }
                        is KomgaResult.Failure ->
                            handleVerifyFailure(server.id, result.error)
                    }
                mutableState.update { it.copy(connectionTest = state) }
            }
        }

        private fun handleVerifyFailure(
            serverId: String,
            error: KomgaError,
        ): ConnectionTestState {
            if (error is KomgaError.UntrustedCertificate) {
                val certificate = error.certificate
                if (certificate != null) {
                    val mismatch = repository.existingPinMismatch(serverId, certificate.sha256Fingerprint)
                    mutableState.update {
                        it.copy(trustDialog = TrustDialogState(serverId, certificate, mismatch))
                    }
                }
            }
            return ConnectionTestState.Failed(serverId, error)
        }

        fun onConfirmPin() {
            val dialog = mutableState.value.trustDialog ?: return
            viewModelScope.launch {
                when (val result = repository.pinCertificate(dialog.serverId, dialog.certificate)) {
                    is KomgaResult.Success -> mutableState.update { it.copy(trustDialog = null) }
                    is KomgaResult.Failure ->
                        mutableState.update {
                            it.copy(trustDialog = dialog.copy(error = result.error.message))
                        }
                }
            }
        }

        fun onCancelPin() {
            mutableState.update { it.copy(trustDialog = null) }
        }

        fun onDismissConnectionTest() {
            mutableState.update { it.copy(connectionTest = null) }
        }

        private fun updateForm(transform: (ServerFormState) -> ServerFormState) {
            mutableState.update { current ->
                current.form?.let { current.copy(form = transform(it)) } ?: current
            }
        }

        private fun validateBaseUrl(url: String): String? =
            when {
                url.isBlank() -> "URLを入力してください"
                !url.startsWith("http://", true) && !url.startsWith("https://", true) -> "http(s):// で始まるURLを入力してください"
                else -> null
            }
    }
