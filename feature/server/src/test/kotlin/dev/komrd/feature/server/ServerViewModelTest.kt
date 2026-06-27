package dev.komrd.feature.server

import dev.komrd.core.common.error.CertificateInfo
import dev.komrd.core.common.error.KomgaError
import dev.komrd.core.common.result.KomgaResult
import dev.komrd.core.data.server.ServerRepository
import dev.komrd.core.datastore.ActiveServerStore
import dev.komrd.core.model.AuthMethod
import dev.komrd.core.model.ConnectionResult
import dev.komrd.core.model.Server
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class ServerViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repo: FakeServerRepository
    private lateinit var activeStore: FakeActiveServerStore

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        repo = FakeServerRepository()
        activeStore = FakeActiveServerStore()
    }

    @After
    fun teardown() {
        Dispatchers.resetMain()
    }

    private fun createVm() = ServerViewModel(repo, activeStore)

    @Test
    fun initial_state_emptyServers() =
        runTest(dispatcher) {
            val vm = createVm()
            assertEquals(emptyList<ServerRow>(), vm.uiState.value.servers)
            assertNull(vm.uiState.value.activeServerId)
        }

    @Test
    fun onAdd_opensEmptyForm() =
        runTest(dispatcher) {
            val vm = createVm()
            vm.onAdd()
            val form = vm.uiState.value.form
            assertNotNull(form)
            assertTrue(form?.isNew == true)
        }

    @Test
    fun onSaveForm_validatesNameAndUrl_thenPersistsAndClosesForm() =
        runTest(dispatcher) {
            val vm = createVm()
            vm.onAdd()
            vm.onSaveForm()
            val errored = vm.uiState.value.form!!
            assertEquals("名前を入力してください", errored.nameError)
            assertEquals("URLを入力してください", errored.urlError)
            vm.onFormNameChange("Home")
            vm.onFormUrlChange("https://komga.example")
            vm.onFormAuthChange(AuthMethodSelection.ApiKey)
            vm.onFormApiKeyChange("key123")
            vm.onSaveForm()
            assertNull(vm.uiState.value.form)
            assertEquals(1, repo.addedServers.size)
            assertEquals("Home", repo.addedServers.single().name)
            assertEquals("key123", (repo.addedServers.single().auth as AuthMethod.ApiKey).key)
            assertEquals(repo.addedServers.single().id, activeStore.activeId)
        }

    @Test
    fun onSaveForm_invalidUrl_showsUrlError() =
        runTest(dispatcher) {
            val vm = createVm()
            vm.onAdd()
            vm.onFormNameChange("Home")
            vm.onFormUrlChange("ftp://bad")
            vm.onSaveForm()
            assertEquals(
                "http(s):// で始まるURLを入力してください",
                vm.uiState.value.form
                    ?.urlError,
            )
        }

    @Test
    fun onEdit_populatesFormWithExistingValues() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.Basic("alice", "pw"))
            repo.seed(server)
            val vm = createVm()
            vm.onEdit(server)
            val form = vm.uiState.value.form!!
            assertEquals("s1", form.editingId)
            assertEquals("Home", form.name)
            assertEquals("https://k.example", form.baseUrl)
            assertTrue(form.authMethod is AuthMethodSelection.Basic)
            assertEquals("alice", form.username)
            assertEquals("pw", form.password)
        }

    @Test
    fun onSaveForm_editing_updatesExistingServer() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val vm = createVm()
            vm.onEdit(server)
            vm.onFormNameChange("Renamed")
            vm.onSaveForm()
            assertNull(vm.uiState.value.form)
            assertEquals("Renamed", repo.current.first().name)
            assertEquals("s1", repo.updatedIds.single())
            assertNull(activeStore.activeId)
        }

    @Test
    fun onDelete_callsRepositoryDeleteAndClearsActiveIfMatched() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val vm = createVm()
            activeStore.setActive("s1")
            vm.onDelete("s1")
            assertEquals("s1", repo.deletedIds.single())
            assertNull(activeStore.activeId)
        }

    @Test
    fun onSelectActive_updatesActiveStore() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val vm = createVm()
            vm.onSelectActive("s1")
            assertEquals("s1", activeStore.activeId)
        }

    @Test
    fun onVerifyConnection_success_showsSuccessState() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            repo.verifyResult = KomgaResult.Success(ConnectionResult.Authenticated("u1"))
            val vm = createVm()
            vm.onVerifyConnection(server)
            val state = vm.uiState.value.connectionTest
            assertTrue(state is ConnectionTestState.Success)
            assertEquals("u1", (state as ConnectionTestState.Success).userId)
        }

    @Test
    fun onVerifyConnection_unauthorized_showsFailedState() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            repo.verifyResult = KomgaResult.Failure(KomgaError.Unauthorized("bad"))
            val vm = createVm()
            vm.onVerifyConnection(server)
            val state = vm.uiState.value.connectionTest
            assertTrue(state is ConnectionTestState.Failed)
            assertTrue((state as ConnectionTestState.Failed).error is KomgaError.Unauthorized)
        }

    @Test
    fun onVerifyConnection_untrustedCertificate_opensTrustDialogAndMarksFailed() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val cert = certInfo("AA:BB")
            repo.verifyResult = KomgaResult.Failure(KomgaError.UntrustedCertificate(cert, "msg"))
            repo.mismatchResult = false
            val vm = createVm()
            vm.onVerifyConnection(server)
            assertTrue(vm.uiState.value.connectionTest is ConnectionTestState.Failed)
            val dialog = vm.uiState.value.trustDialog
            assertNotNull(dialog)
            assertEquals("s1", dialog?.serverId)
            assertEquals(cert, dialog?.certificate)
            assertFalse(dialog?.mismatch == true)
        }

    @Test
    fun onConfirmPin_callsPinCertificateAndClearsDialog() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val cert = certInfo("AA:BB")
            repo.verifyResult = KomgaResult.Failure(KomgaError.UntrustedCertificate(cert, "msg"))
            repo.mismatchResult = false
            val vm = createVm()
            vm.onVerifyConnection(server)
            vm.onConfirmPin()
            assertNull(vm.uiState.value.trustDialog)
            assertEquals("s1", repo.pinnedServers.single())
        }

    @Test
    fun onCancelPin_clearsDialog() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val cert = certInfo("AA:BB")
            repo.verifyResult = KomgaResult.Failure(KomgaError.UntrustedCertificate(cert, "msg"))
            repo.mismatchResult = false
            val vm = createVm()
            vm.onVerifyConnection(server)
            vm.onCancelPin()
            assertNull(vm.uiState.value.trustDialog)
        }

    @Test
    fun trustDialog_mismatchFlagPropagatedFromRepository() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            val cert = certInfo("CC:DD")
            repo.verifyResult = KomgaResult.Failure(KomgaError.UntrustedCertificate(cert, "msg"))
            repo.mismatchResult = true
            val vm = createVm()
            vm.onVerifyConnection(server)
            assertEquals(
                true,
                vm.uiState.value.trustDialog
                    ?.mismatch,
            )
        }

    @Test
    fun onDismissConnectionTest_clearsState() =
        runTest(dispatcher) {
            val server = Server("s1", "Home", "https://k.example", AuthMethod.ApiKey("k"))
            repo.seed(server)
            repo.verifyResult = KomgaResult.Success(ConnectionResult.Authenticated())
            val vm = createVm()
            vm.onVerifyConnection(server)
            vm.onDismissConnectionTest()
            assertNull(vm.uiState.value.connectionTest)
        }

    private fun certInfo(fingerprint: String): CertificateInfo =
        CertificateInfo(
            sha256Fingerprint = fingerprint,
            subject = "CN=test",
            issuer = "CN=test",
            notBefore = Instant.parse("2024-01-01T00:00:00Z"),
            notAfter = Instant.parse("2034-01-01T00:00:00Z"),
        )
}

private class FakeServerRepository : ServerRepository {
    val current = mutableListOf<Server>()
    val addedServers = mutableListOf<Server>()
    val updatedIds = mutableListOf<String>()
    val deletedIds = mutableListOf<String>()
    val pinnedServers = mutableListOf<String>()
    private val flow = MutableStateFlow<List<Server>>(emptyList())
    var verifyResult: KomgaResult<ConnectionResult> = KomgaResult.Success(ConnectionResult.Authenticated())
    var mismatchResult: Boolean = false

    fun seed(server: Server) {
        current.add(server)
        flow.value = current.toList()
    }

    override val servers: Flow<List<Server>> = flow.asStateFlow()

    override suspend fun byId(id: String): Server? = current.firstOrNull { it.id == id }

    override suspend fun add(server: Server) {
        current.add(server)
        addedServers.add(server)
        flow.value = current.toList()
    }

    override suspend fun update(server: Server) {
        val idx = current.indexOfFirst { it.id == server.id }
        if (idx >= 0) current[idx] = server
        updatedIds.add(server.id)
        flow.value = current.toList()
    }

    override suspend fun delete(id: String) {
        current.removeAll { it.id == id }
        deletedIds.add(id)
        flow.value = current.toList()
    }

    override suspend fun verifyConnection(server: Server): KomgaResult<ConnectionResult> = verifyResult

    override suspend fun pinCertificate(
        serverId: String,
        certificate: CertificateInfo,
    ): KomgaResult<Unit> {
        pinnedServers.add(serverId)
        return KomgaResult.Success(Unit)
    }

    override suspend fun pinCustomCa(
        serverId: String,
        certificates: List<java.security.cert.X509Certificate>,
    ): KomgaResult<Unit> = KomgaResult.Success(Unit)

    override fun existingPinMismatch(
        serverId: String,
        newFingerprint: String,
    ): Boolean = mismatchResult

    override fun certificateInfoOf(error: KomgaError): CertificateInfo? {
        val untrusted = error as? KomgaError.UntrustedCertificate ?: return null
        return untrusted.certificate
    }
}

private class FakeActiveServerStore : ActiveServerStore {
    var activeId: String? = null
    private val flow = MutableStateFlow<String?>(null)

    override val activeServerId: Flow<String?> = flow.asStateFlow()

    override suspend fun setActive(id: String) {
        activeId = id
        flow.value = id
    }

    override suspend fun clear() {
        activeId = null
        flow.value = null
    }
}
