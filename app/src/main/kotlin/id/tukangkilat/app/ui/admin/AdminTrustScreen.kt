package id.tukangkilat.app.ui.admin

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

data class AdminTrustUiState(
    val tab: Int = 0,
    val sos: List<SosAlertDto> = emptyList(),
    val incidents: List<IncidentDto> = emptyList(),
    val subadmins: List<SubadminDto> = emptyList(),
    val retention: List<RetentionDto> = emptyList(),
    val backups: List<BackupJobDto> = emptyList(),
    val incidentTitle: String = "",
    val incidentDesc: String = "",
    val roleName: String = "",
    val permissions: String = "",
    val backupName: String = "",
    val notice: String? = null
)

@HiltViewModel
class AdminTrustViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(AdminTrustUiState())
    val state: StateFlow<AdminTrustUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = AdminTrustUiState(
                    tab = current.tab, notice = current.notice,
                    incidentTitle = current.incidentTitle, incidentDesc = current.incidentDesc,
                    roleName = current.roleName, permissions = current.permissions, backupName = current.backupName,
                    sos = runCatching { api.adminSos(null) }.getOrDefault(emptyList()),
                    incidents = runCatching { api.incidents(null) }.getOrDefault(emptyList()),
                    subadmins = runCatching { api.subadmins() }.getOrDefault(emptyList()),
                    retention = runCatching { api.retentionPolicies() }.getOrDefault(emptyList()),
                    backups = runCatching { api.backupJobs() }.getOrDefault(emptyList())
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun field(name: String, value: String) {
        _state.value = when (name) {
            "incidentTitle" -> _state.value.copy(incidentTitle = value)
            "incidentDesc" -> _state.value.copy(incidentDesc = value)
            "roleName" -> _state.value.copy(roleName = value)
            "permissions" -> _state.value.copy(permissions = value)
            "backupName" -> _state.value.copy(backupName = value)
            else -> _state.value
        }
    }

    fun createIncident() {
        val s = _state.value
        if (s.incidentTitle.isBlank()) { _state.value = s.copy(notice = "Judul wajib"); return }
        viewModelScope.launch {
            try {
                api.createIncident(CreateIncidentRequest(s.incidentTitle, s.incidentDesc, "MEDIUM", ""))
                _state.value = _state.value.copy(notice = "Insiden dibuat", incidentTitle = "", incidentDesc = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveSubadmin(adminId: Long) {
        val s = _state.value
        if (s.roleName.isBlank()) { _state.value = s.copy(notice = "Nama peran wajib"); return }
        viewModelScope.launch {
            try {
                api.saveSubadmin(SaveSubadminRequest(adminId, s.roleName, s.permissions))
                _state.value = _state.value.copy(notice = "Peran disimpan", roleName = "", permissions = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveBackup() {
        val s = _state.value
        if (s.backupName.isBlank()) { _state.value = s.copy(notice = "Nama jadwal wajib"); return }
        viewModelScope.launch {
            try {
                api.saveBackupJob(SaveBackupJobRequest(s.backupName, "HARIAN", true))
                _state.value = _state.value.copy(notice = "Jadwal dibuat", backupName = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminTrustScreen(onBack: () -> Unit, viewModel: AdminTrustViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Kepercayaan Admin", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("SOS", "Insiden", "Peran", "Retensi", "Cadangan").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.sos, key = { it.id }) { alert ->
                        TKCard {
                            Text("SOS ${alert.id} · ${alert.status}", fontWeight = FontWeight.Bold)
                            Text("Waktu ${alert.createdAt.take(10)} · ${alert.note}")
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.incidentTitle, { viewModel.field("incidentTitle", it) }, label = "Judul insiden")
                    TKOutlinedField(state.incidentDesc, { viewModel.field("incidentDesc", it) }, label = "Deskripsi")
                    TKButton("Buat insiden", viewModel::createIncident, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.incidents, key = { it.id }) { incident ->
                            Text("${incident.code} · ${incident.title} · ${incident.status}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.roleName, { viewModel.field("roleName", it) }, label = "Nama peran")
                    TKOutlinedField(state.permissions, { viewModel.field("permissions", it) }, label = "Izin, dipisah koma")
                    TKButton("Simpan peran", { viewModel.saveSubadmin(1L) }, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.subadmins, key = { it.id }) { role ->
                            Text("${role.roleName}: ${role.permissions}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                3 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.retention, key = { it.code }) { policy ->
                        Text("${policy.code} · ${policy.tableName} · ${policy.days} hari", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.backupName, { viewModel.field("backupName", it) }, label = "Nama jadwal cadangan")
                    TKButton("Buat jadwal", viewModel::saveBackup, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.backups, key = { it.id }) { job ->
                            Text("${job.name} · ${job.frequency} · ${job.enabled}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
