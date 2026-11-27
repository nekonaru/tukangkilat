package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrustUiState(
    val tab: Int = 0,
    val devices: List<TrustedDeviceDto> = emptyList(),
    val sessions: List<SessionDto> = emptyList(),
    val consents: List<ConsentDto> = emptyList(),
    val blocks: List<BlockDto> = emptyList(),
    val alerts: List<LoginAlertDto> = emptyList(),
    val sos: List<SosAlertDto> = emptyList(),
    val lock: AppLockDto? = null,
    val twoFactor: TwoFactorDto? = null,
    val privacy: PrivacySnapshotDto? = null,
    val deviceName: String = "",
    val deviceId: String = "",
    val pin: String = "",
    val otp: String = "",
    val blockId: String = "",
    val sosNote: String = "",
    val visitOrderId: String = "",
    val exportedText: String = "",
    val notice: String? = null
)

@HiltViewModel
class TrustViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(TrustUiState())
    val state: StateFlow<TrustUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = TrustUiState(
                    tab = current.tab, notice = current.notice,
                    pin = current.pin, otp = current.otp, blockId = current.blockId, sosNote = current.sosNote,
                    visitOrderId = current.visitOrderId,
                    devices = runCatching { api.trustedDevices() }.getOrDefault(emptyList()),
                    sessions = runCatching { api.sessions() }.getOrDefault(emptyList()),
                    consents = runCatching { api.consents() }.getOrDefault(emptyList()),
                    blocks = runCatching { api.blocks() }.getOrDefault(emptyList()),
                    alerts = runCatching { api.loginAlerts() }.getOrDefault(emptyList()),
                    sos = runCatching { api.sosAlerts() }.getOrDefault(emptyList()),
                    lock = runCatching { api.appLock() }.getOrNull(),
                    twoFactor = runCatching { api.twoFactor() }.getOrNull(),
                    privacy = runCatching { api.privacySnapshot() }.getOrNull()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun field(name: String, value: String) {
        _state.value = when (name) {
            "deviceName" -> _state.value.copy(deviceName = value)
            "deviceId" -> _state.value.copy(deviceId = value)
            "pin" -> _state.value.copy(pin = value)
            "otp" -> _state.value.copy(otp = value)
            "blockId" -> _state.value.copy(blockId = value)
            "sosNote" -> _state.value.copy(sosNote = value)
            "visitOrderId" -> _state.value.copy(visitOrderId = value)
            else -> _state.value
        }
    }

    fun registerDevice() {
        val s = _state.value
        if (s.deviceName.isBlank() || s.deviceId.isBlank()) {
            _state.value = s.copy(notice = "Isi nama dan ID perangkat")
            return
        }
        viewModelScope.launch {
            try {
                api.registerDevice(RegisterDeviceRequest(s.deviceName, s.deviceId))
                _state.value = _state.value.copy(notice = "Perangkat didaftarkan", deviceName = "", deviceId = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveConsent(kind: String, granted: Boolean) {
        viewModelScope.launch {
            try {
                api.saveConsent(SaveConsentRequest(kind, granted))
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun setPin(enabled: Boolean) {
        val pin = _state.value.pin
        if (pin.length !in 4..8 || !pin.all { it.isDigit() }) {
            _state.value = _state.value.copy(notice = "PIN harus 4 sampai 8 digit")
            return
        }
        viewModelScope.launch {
            try {
                api.setPin(SetPinRequest(pin, enabled))
                _state.value = _state.value.copy(notice = "PIN disimpan", pin = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun requestOtp() {
        viewModelScope.launch {
            try {
                val result = api.requestOtp()
                _state.value = _state.value.copy(twoFactor = result, notice = "OTP simulasi: ${result.otpCode}")
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun verifyOtp() {
        val code = _state.value.otp
        viewModelScope.launch {
            try {
                api.verifyOtp(mapOf("code" to code))
                _state.value = _state.value.copy(notice = "Verifikasi dua langkah aktif", otp = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun blockUser() {
        val id = _state.value.blockId.toLongOrNull()
        if (id == null) { _state.value = _state.value.copy(notice = "ID pengguna tidak valid"); return }
        viewModelScope.launch {
            try {
                api.blockUser(mapOf("userId" to id))
                _state.value = _state.value.copy(notice = "Pengguna diblokir", blockId = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun sendSos() {
        viewModelScope.launch {
            try {
                api.createSos(CreateSosRequest(null, -6.2, 106.8, _state.value.sosNote))
                _state.value = _state.value.copy(notice = "Sinyal darurat terkirim", sosNote = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun deletePrivacyData() {
        viewModelScope.launch {
            try {
                api.deletePrivacyData()
                _state.value = _state.value.copy(notice = "Data privasi lokal dihapus")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun toggleDeviceTrust(id: Long, trusted: Boolean) {
        viewModelScope.launch {
            try {
                api.setDeviceTrust(id, mapOf("trusted" to !trusted))
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun removeDevice(id: Long) {
        viewModelScope.launch {
            try { api.removeDevice(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun revoke(id: Long) {
        viewModelScope.launch {
            try { api.revokeSession(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun readAlert(id: Long) {
        viewModelScope.launch {
            try { api.markAlertRead(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun disableTwoFactor() {
        viewModelScope.launch {
            try { api.disableTwoFactor(); _state.value = _state.value.copy(notice = "Verifikasi dua langkah nonaktif"); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun exportPrivacy() {
        viewModelScope.launch {
            try {
                val text = api.exportPrivacyData()
                _state.value = _state.value.copy(exportedText = text.take(500), notice = "Data diekspor")
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun loadSafeVisit() {
        val id = _state.value.visitOrderId.toLongOrNull()
        if (id == null) { _state.value = _state.value.copy(notice = "ID pesanan tidak valid"); return }
        viewModelScope.launch {
            try {
                val result = api.safeVisit(id)
                _state.value = _state.value.copy(notice = "Kode kunjungan: ${result.codeWord}")
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrustCenterScreen(onBack: () -> Unit, viewModel: TrustViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Pusat Kepercayaan", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Perangkat", "Sesi", "Privasi", "Keamanan", "Darurat").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.deviceName, { viewModel.field("deviceName", it) }, label = "Nama perangkat")
                    TKOutlinedField(state.deviceId, { viewModel.field("deviceId", it) }, label = "ID perangkat")
                    TKButton("Daftarkan perangkat", viewModel::registerDevice, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.devices, key = { it.id }) { device ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("${device.deviceName} · ${if (device.trusted) "terpercaya" else "tidak terpercaya"}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.toggleDeviceTrust(device.id, device.trusted) }) { Text(if (device.trusted) "Cabut" else "Percaya") }
                                TextButton(onClick = { viewModel.removeDevice(device.id) }) { Text("Hapus") }
                            }
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.sessions, key = { it.id }) { session ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${session.deviceName} · ${session.startedAt.take(10)} · ${if (session.revoked) "dicabut" else "aktif"}", modifier = Modifier.weight(1f))
                            if (!session.revoked) TextButton(onClick = { viewModel.revoke(session.id) }) { Text("Cabut") }
                        }
                    }
                    items(state.alerts, key = { it.id }) { alert ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Login: ${alert.deviceName} · ${alert.createdAt.take(10)}", modifier = Modifier.weight(1f))
                            if (!alert.isRead) TextButton(onClick = { viewModel.readAlert(alert.id) }) { Text("Baca") }
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    state.privacy?.let { p ->
                        TKCard {
                            Text("Pesanan ${p.orders} · Alamat ${p.addresses} · Ulasan ${p.reviews}", fontWeight = FontWeight.Bold)
                            Text("Pesan ${p.messages} · Transaksi ${p.walletTransactions} · Perangkat ${p.devices}")
                        }
                    }
                    state.consents.forEach { consent ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = consent.granted, onCheckedChange = { viewModel.saveConsent(consent.kind, it) })
                            Text(consent.kind)
                        }
                    }
                    TKOutlinedField(state.blockId, { viewModel.field("blockId", it) }, label = "ID pengguna diblokir")
                    TKButton("Blokir pengguna", viewModel::blockUser, variant = TKButtonVariant.Outlined)
                    TKButton("Ekspor data JSON", viewModel::exportPrivacy, variant = TKButtonVariant.Tonal)
                    state.exportedText.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    TKOutlinedField(state.visitOrderId, { viewModel.field("visitOrderId", it) }, label = "ID pesanan untuk kode kunjungan")
                    TKButton("Lihat kode kunjungan", viewModel::loadSafeVisit, variant = TKButtonVariant.Outlined)
                    TKButton("Hapus data privasi lokal", viewModel::deletePrivacyData, variant = TKButtonVariant.Tonal)
                }
                3 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.pin, { viewModel.field("pin", it) }, label = "PIN 4 sampai 8 digit")
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKButton("Aktifkan PIN", { viewModel.setPin(true) }, modifier = Modifier.weight(1f))
                        TKButton("Nonaktifkan", { viewModel.setPin(false) }, modifier = Modifier.weight(1f), variant = TKButtonVariant.Outlined)
                    }
                    state.twoFactor?.let { tf ->
                        Text("Verifikasi dua langkah: ${if (tf.enabled) "aktif" else "nonaktif"}")
                    }
                    TKButton("Minta OTP simulasi", viewModel::requestOtp, variant = TKButtonVariant.Tonal)
                    TKOutlinedField(state.otp, { viewModel.field("otp", it) }, label = "Kode OTP")
                    TKButton("Verifikasi OTP", viewModel::verifyOtp)
                    TKButton("Nonaktifkan 2FA", viewModel::disableTwoFactor, variant = TKButtonVariant.Outlined)
                }
                else -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.sosNote, { viewModel.field("sosNote", it) }, label = "Catatan darurat")
                    TKButton("Kirim sinyal darurat", viewModel::sendSos)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.sos, key = { it.id }) { alert ->
                            Text("SOS ${alert.id} · ${alert.status} · ${alert.createdAt.take(10)}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
