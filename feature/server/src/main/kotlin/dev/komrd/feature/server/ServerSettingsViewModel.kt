package dev.komrd.feature.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.server.ServerSettingsRepository
import dev.komrd.core.data.server.UserRepository
import dev.komrd.core.model.ServerSettings
import dev.komrd.core.model.SettingsUpdate
import dev.komrd.core.model.UserAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ServerSettingsUiState {
    data object Loading : ServerSettingsUiState

    data class Error(
        val message: String,
    ) : ServerSettingsUiState

    /**
     * 設定編集可能状態。[isAdmin]がfalseのときは読取のみ+警告表示。
     * [original]は取得時のスナップショットで差分PATCHの比較基準。
     */
    data class Content(
        val original: ServerSettings,
        val form: ServerSettingsForm,
        val user: UserAccount?,
        val isAdmin: Boolean,
        val saving: Boolean = false,
        val feedback: ServerSettingsFeedback? = null,
    ) : ServerSettingsUiState
}

sealed interface ServerSettingsFeedback {
    data class Success(
        val message: String,
    ) : ServerSettingsFeedback

    data class Failure(
        val message: String,
    ) : ServerSettingsFeedback
}

data class ServerSettingsForm(
    val deleteEmptyCollections: Boolean = false,
    val deleteEmptyReadLists: Boolean = false,
    val taskPoolSize: String = "",
    val rememberMeDurationDays: String = "",
    val renewRememberMeKey: Boolean = false,
    val serverPort: String = "",
    val serverContextPath: String = "",
    val thumbnailSize: String = "",
    val koboPort: String = "",
    val koboProxy: Boolean = false,
)

/** thumbnailSizeの選択肢（Komga enum: DEFAULT/MEDIUM/LARGE/XLARGE）。 */
val ThumbnailSizeOptions = listOf("DEFAULT", "MEDIUM", "LARGE", "XLARGE")

@HiltViewModel
@Suppress("TooManyFunctions")
class ServerSettingsViewModel
    @Inject
    constructor(
        private val settingsRepository: ServerSettingsRepository,
        private val userRepository: UserRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<ServerSettingsUiState>(ServerSettingsUiState.Loading)
        val state: StateFlow<ServerSettingsUiState> = _state.asStateFlow()

        private var bound = false

        fun bind(serverId: String) {
            if (bound) return
            bound = true
            viewModelScope.launch {
                val settingsResult = settingsRepository.get(serverId)
                val userResult = userRepository.currentUser(serverId)
                when (settingsResult) {
                    is KomgaResult.Failure ->
                        _state.value =
                            ServerSettingsUiState.Error(settingsResult.error.message)
                    is KomgaResult.Success -> {
                        val user = (userResult as? KomgaResult.Success)?.value
                        val isAdmin = user?.let { userRepository.isAdmin(it) } ?: false
                        _state.value =
                            ServerSettingsUiState.Content(
                                original = settingsResult.value,
                                form = settingsResult.value.toForm(),
                                user = user,
                                isAdmin = isAdmin,
                            )
                    }
                }
            }
        }

        fun onDeleteEmptyCollectionsChange(value: Boolean) = updateForm { it.copy(deleteEmptyCollections = value) }

        fun onDeleteEmptyReadListsChange(value: Boolean) = updateForm { it.copy(deleteEmptyReadLists = value) }

        fun onTaskPoolSizeChange(value: String) = updateForm { it.copy(taskPoolSize = value) }

        fun onRememberMeDurationDaysChange(value: String) = updateForm { it.copy(rememberMeDurationDays = value) }

        fun onRenewRememberMeKeyChange(value: Boolean) = updateForm { it.copy(renewRememberMeKey = value) }

        fun onServerPortChange(value: String) = updateForm { it.copy(serverPort = value) }

        fun onServerContextPathChange(value: String) = updateForm { it.copy(serverContextPath = value) }

        fun onThumbnailSizeChange(value: String) = updateForm { it.copy(thumbnailSize = value) }

        fun onKoboPortChange(value: String) = updateForm { it.copy(koboPort = value) }

        fun onKoboProxyChange(value: Boolean) = updateForm { it.copy(koboProxy = value) }

        fun onDismissFeedback() = updateContent { it.copy(feedback = null) }

        @Suppress("ReturnCount") // 非ADMIN/parse失敗/Content未確定の早期returnが明示的なため許容
        fun onSave(serverId: String) {
            val content = _state.value as? ServerSettingsUiState.Content ?: return
            if (!content.isAdmin) {
                updateContent { it.copy(feedback = ServerSettingsFeedback.Failure("管理者権限が必要です")) }
                return
            }
            val parsed =
                parseForm(content.form) ?: run {
                    updateContent { it.copy(feedback = ServerSettingsFeedback.Failure("数値項目の形式が不正です")) }
                    return
                }
            val update = SettingsUpdate.diff(content.original, parsed)
            if (update.isEmpty) {
                updateContent { it.copy(feedback = ServerSettingsFeedback.Success("変更はありません")) }
                return
            }
            viewModelScope.launch {
                updateContent { it.copy(saving = true, feedback = null) }
                when (val result = settingsRepository.update(serverId, update)) {
                    is KomgaResult.Success -> {
                        // 保存後はoriginalを最新へ更新し、次回差分の基準をリセットする
                        updateContent {
                            it.copy(
                                original = parsed,
                                saving = false,
                                feedback = ServerSettingsFeedback.Success("保存しました"),
                            )
                        }
                    }
                    is KomgaResult.Failure -> {
                        updateContent {
                            it.copy(
                                saving = false,
                                feedback = ServerSettingsFeedback.Failure(result.error.message),
                            )
                        }
                    }
                }
            }
        }

        @Suppress("ReturnCount") // 各数値項目のparse失敗早期returnが明示的なため許容
        private fun parseForm(form: ServerSettingsForm): ServerSettings? {
            val taskPoolSize = form.taskPoolSize.toIntOrNull() ?: return null
            val rememberMeDays = form.rememberMeDurationDays.toLongOrNull() ?: return null
            val serverPort = form.serverPort.toIntOrNull() ?: return null
            val koboPort = form.koboPort.toIntOrNull() ?: return null
            return ServerSettings(
                deleteEmptyCollections = form.deleteEmptyCollections,
                deleteEmptyReadLists = form.deleteEmptyReadLists,
                taskPoolSize = taskPoolSize,
                rememberMeDurationDays = rememberMeDays,
                renewRememberMeKey = form.renewRememberMeKey,
                koboPort = koboPort,
                koboProxy = form.koboProxy,
                thumbnailSize = form.thumbnailSize.ifBlank { null },
                serverPort = serverPort,
                serverContextPath = form.serverContextPath.ifBlank { null },
            )
        }

        private fun ServerSettings.toForm(): ServerSettingsForm =
            ServerSettingsForm(
                deleteEmptyCollections = deleteEmptyCollections ?: false,
                deleteEmptyReadLists = deleteEmptyReadLists ?: false,
                taskPoolSize = taskPoolSize?.toString().orEmpty(),
                rememberMeDurationDays = rememberMeDurationDays?.toString().orEmpty(),
                renewRememberMeKey = renewRememberMeKey ?: false,
                serverPort = serverPort?.toString().orEmpty(),
                serverContextPath = serverContextPath.orEmpty(),
                thumbnailSize = thumbnailSize.orEmpty(),
                koboPort = koboPort?.toString().orEmpty(),
                koboProxy = koboProxy ?: false,
            )

        private fun updateForm(transform: (ServerSettingsForm) -> ServerSettingsForm) {
            updateContent { content -> content.copy(form = transform(content.form)) }
        }

        private fun updateContent(transform: (ServerSettingsUiState.Content) -> ServerSettingsUiState.Content) {
            val current = _state.value as? ServerSettingsUiState.Content ?: return
            _state.value = transform(current)
        }
    }
