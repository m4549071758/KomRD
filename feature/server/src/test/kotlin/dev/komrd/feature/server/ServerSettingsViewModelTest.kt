package dev.komrd.feature.server

import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.server.ServerSettingsRepository
import dev.komrd.core.data.server.UserRepository
import dev.komrd.core.model.AgeRestriction
import dev.komrd.core.model.ServerSettings
import dev.komrd.core.model.SettingsUpdate
import dev.komrd.core.model.UserAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ServerSettingsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var settingsRepo: FakeServerSettingsRepository
    private lateinit var userRepo: FakeUserRepository

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        settingsRepo = FakeServerSettingsRepository()
        userRepo = FakeUserRepository()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = ServerSettingsViewModel(settingsRepo, userRepo)

    @Test
    fun bind_success_loadsSettingsAndUser_admin() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            val vm = createVm()
            vm.bind("s1")
            val state = vm.state.value as ServerSettingsUiState.Content
            assertEquals(DEFAULT_SETTINGS, state.original)
            assertTrue(state.isAdmin)
            assertEquals(setOf("ADMIN", "USER"), state.user?.roles)
            assertEquals("8", state.form.taskPoolSize)
            assertEquals("8080", state.form.serverPort)
            assertEquals("LARGE", state.form.thumbnailSize)
        }

    @Test
    fun bind_success_nonAdmin_isAdminFalse() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = nonAdminUser()
            val vm = createVm()
            vm.bind("s1")
            val state = vm.state.value as ServerSettingsUiState.Content
            assertFalse(state.isAdmin)
            assertEquals(listOf("lib-1", "lib-2"), state.user?.sharedLibrariesIds)
            assertEquals(false, state.user?.sharedAllLibraries)
            assertEquals(18, state.user?.ageRestriction?.age)
        }

    @Test
    fun bind_settingsFailure_emitsError() =
        runTest(dispatcher) {
            settingsRepo.settingsResult = KomgaResult.Failure(KomgaError.Unknown("boom"))
            val vm = createVm()
            vm.bind("s1")
            assertTrue(vm.state.value is ServerSettingsUiState.Error)
            assertEquals("boom", (vm.state.value as ServerSettingsUiState.Error).message)
        }

    @Test
    fun bind_userFailureStillLoadsSettings_adminDefaultsFalse() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.userResult = KomgaResult.Failure(KomgaError.Unauthorized())
            val vm = createVm()
            vm.bind("s1")
            val state = vm.state.value as ServerSettingsUiState.Content
            assertEquals(DEFAULT_SETTINGS, state.original)
            assertNull(state.user)
            assertFalse(state.isAdmin)
        }

    @Test
    fun formChanges_updateFormState() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            val vm = createVm()
            vm.bind("s1")
            vm.onTaskPoolSizeChange("32")
            vm.onThumbnailSizeChange("XLARGE")
            vm.onDeleteEmptyCollectionsChange(false)
            val form = (vm.state.value as ServerSettingsUiState.Content).form
            assertEquals("32", form.taskPoolSize)
            assertEquals("XLARGE", form.thumbnailSize)
            assertEquals(false, form.deleteEmptyCollections)
        }

    @Test
    fun onSave_admin_changedFields_sendsDiffPatchAndShowsSuccess() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            val vm = createVm()
            vm.bind("s1")
            vm.onTaskPoolSizeChange("16")
            vm.onSave("s1")

            val captured = settingsRepo.lastUpdate
            assertEquals(16, captured?.taskPoolSize)
            // 変更していない項目はnull（差分PATCH）
            assertNull(captured?.deleteEmptyCollections)
            assertNull(captured?.serverPort)
            val state = vm.state.value as ServerSettingsUiState.Content
            assertTrue(state.feedback is ServerSettingsFeedback.Success)
            // originalが保存後スナップショットへ更新される
            assertEquals(16, state.original.taskPoolSize)
            assertFalse(state.saving)
        }

    @Test
    fun onSave_admin_noChanges_showsNoChangeFeedbackAndSkipsNetwork() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            val vm = createVm()
            vm.bind("s1")
            vm.onSave("s1")
            assertNull(settingsRepo.lastUpdate)
            val state = vm.state.value as ServerSettingsUiState.Content
            assertTrue(state.feedback is ServerSettingsFeedback.Success)
            assertEquals("変更はありません", (state.feedback as ServerSettingsFeedback.Success).message)
        }

    @Test
    fun onSave_admin_invalidNumber_showsFailureAndSkipsNetwork() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            val vm = createVm()
            vm.bind("s1")
            vm.onTaskPoolSizeChange("not-a-number")
            vm.onSave("s1")
            assertNull(settingsRepo.lastUpdate)
            val feedback = (vm.state.value as ServerSettingsUiState.Content).feedback
            assertTrue(feedback is ServerSettingsFeedback.Failure)
        }

    @Test
    fun onSave_nonAdmin_showsFailureAndSkipsNetwork() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = nonAdminUser()
            val vm = createVm()
            vm.bind("s1")
            vm.onTaskPoolSizeChange("16")
            vm.onSave("s1")
            assertNull(settingsRepo.lastUpdate)
            val feedback = (vm.state.value as ServerSettingsUiState.Content).feedback
            assertTrue(feedback is ServerSettingsFeedback.Failure)
            assertEquals("管理者権限が必要です", (feedback as ServerSettingsFeedback.Failure).message)
        }

    @Test
    fun onSave_failure_showsFailureFeedback() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            settingsRepo.updateResult = KomgaResult.Failure(KomgaError.Http(403, "forbidden"))
            val vm = createVm()
            vm.bind("s1")
            vm.onTaskPoolSizeChange("16")
            vm.onSave("s1")
            val state = vm.state.value as ServerSettingsUiState.Content
            val feedback = state.feedback
            assertTrue(feedback is ServerSettingsFeedback.Failure)
            assertFalse(state.saving)
        }

    @Test
    fun onDismissFeedback_clearsFeedback() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            userRepo.user = adminUser()
            val vm = createVm()
            vm.bind("s1")
            vm.onSave("s1")
            assertTrue((vm.state.value as ServerSettingsUiState.Content).feedback != null)
            vm.onDismissFeedback()
            assertNull((vm.state.value as ServerSettingsUiState.Content).feedback)
        }

    @Test
    fun isAdminFlagFromUserRepoUsedWhenUserLoaded() =
        runTest(dispatcher) {
            settingsRepo.settings = DEFAULT_SETTINGS
            // rolesにADMIN無し → UserRepository.isAdmin=false
            userRepo.user = UserAccount(id = "u", roles = setOf("USER", "FILE_DOWNLOAD"))
            val vm = createVm()
            vm.bind("s1")
            val state = vm.state.value as ServerSettingsUiState.Content
            assertFalse(state.isAdmin)
        }

    private fun adminUser() =
        UserAccount(
            id = "u1",
            email = "admin@example.com",
            roles = setOf("ADMIN", "USER"),
            sharedAllLibraries = true,
        )

    private fun nonAdminUser() =
        UserAccount(
            id = "u2",
            email = "user@example.com",
            roles = setOf("USER"),
            sharedAllLibraries = false,
            sharedLibrariesIds = listOf("lib-1", "lib-2"),
            ageRestriction = AgeRestriction(age = 18, restriction = "ALLOW_ONLY"),
        )

    private companion object {
        val DEFAULT_SETTINGS =
            ServerSettings(
                deleteEmptyCollections = true,
                deleteEmptyReadLists = false,
                taskPoolSize = 8,
                rememberMeDurationDays = 30,
                renewRememberMeKey = false,
                koboPort = 8083,
                koboProxy = false,
                thumbnailSize = "LARGE",
                serverPort = 8080,
                serverContextPath = "/komga",
            )
    }
}

private class FakeServerSettingsRepository : ServerSettingsRepository {
    var settings: ServerSettings = ServerSettings()
    var settingsResult: KomgaResult<ServerSettings>? = null
    var updateResult: KomgaResult<Unit> = KomgaResult.Success(Unit)
    var lastUpdate: SettingsUpdate? = null
    var updateInvoked = false

    // settingsResult未設定時は現在のsettingsで成功を返す（呼出時点のsettingsを遅延評価）。
    private fun effectiveSettings(): KomgaResult<ServerSettings> = settingsResult ?: KomgaResult.Success(settings)

    override suspend fun get(serverId: String): KomgaResult<ServerSettings> = effectiveSettings()

    override suspend fun update(
        serverId: String,
        update: SettingsUpdate,
    ): KomgaResult<Unit> {
        updateInvoked = true
        lastUpdate = update
        return updateResult
    }
}

private class FakeUserRepository : UserRepository {
    var user: UserAccount? = null
    var userResult: KomgaResult<UserAccount>? = null

    override suspend fun currentUser(serverId: String): KomgaResult<UserAccount> =
        userResult ?: user?.let { KomgaResult.Success(it) } ?: KomgaResult.Failure(KomgaError.Unknown("no user"))

    override fun isAdmin(user: UserAccount): Boolean = "ADMIN" in user.roles
}
