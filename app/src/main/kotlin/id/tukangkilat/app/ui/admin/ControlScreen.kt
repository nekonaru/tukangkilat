package id.tukangkilat.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

data class ControlUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tab: Int = 0,
    val disputes: List<DisputeDto> = emptyList(),
    val leaders: List<TechnicianLeaderDto> = emptyList(),
    val audit: List<AuditLogDto> = emptyList(),
    val announcements: List<AnnouncementDto> = emptyList(),
    val configs: List<SystemConfigDto> = emptyList(),
    val decision: String = "DISETUJUI",
    val amount: String = "",
    val note: String = "",
    val title: String = "",
    val body: String = "",
    val notice: String? = null
)

@HiltViewModel
class ControlViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(ControlUiState())
    val state: StateFlow<ControlUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = ControlUiState(
                    isLoading = false,
                    disputes = runCatching { api.disputes() }.getOrDefault(emptyList()),
                    leaders = runCatching { api.leaders() }.getOrDefault(emptyList()),
                    audit = runCatching { api.auditLogs() }.getOrDefault(emptyList()),
                    announcements = runCatching { api.announcements() }.getOrDefault(emptyList()),
                    configs = runCatching { api.systemConfigs() }.getOrDefault(emptyList())
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun onField(field: String, value: String) {
        _state.value = when (field) {
            "decision" -> _state.value.copy(decision = value)
            "amount" -> _state.value.copy(amount = value)
            "note" -> _state.value.copy(note = value)
            "title" -> _state.value.copy(title = value)
            "body" -> _state.value.copy(body = value)
            else -> _state.value
        }
    }

    fun resolve(id: Long) {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.resolveDispute(id, ResolveDisputeRequest(s.decision, s.amount.toLongOrNull() ?: 0, s.note))
                _state.value = _state.value.copy(notice = "Sengketa diproses")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun announce() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.createAnnouncement(CreateAnnouncementRequest(s.title, s.body))
                _state.value = _state.value.copy(notice = "Pengumuman dikirim", title = "", body = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun setConfig(key: String, value: String) {
        viewModelScope.launch {
            try { api.setSystemConfig(key, mapOf("value" to value)); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@Composable
fun ControlScreen(onBack: () -> Unit, viewModel: ControlViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Pusat Kendali", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Sengketa", "Peringkat", "Audit", "Siaran", "Konfigurasi").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when {
                state.isLoading -> SkeletonCard()
                state.error != null -> ErrorView(message = state.error ?: "Ada kendala sebentar", onRetry = viewModel::refresh)
                state.tab == 0 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.disputes, key = { it.id }) { dispute ->
                        Column(Modifier.padding(vertical = TKSpacing.Xs)) {
                            Text("Sengketa #${dispute.id} · pesanan ${dispute.orderId}")
                            Text("Keputusan: ${dispute.decision} · Rp${dispute.amount}", style = MaterialTheme.typography.bodySmall)
                            TKOutlinedField(state.decision, { viewModel.onField("decision", it) }, label = "Keputusan")
                            TKOutlinedField(state.amount, { viewModel.onField("amount", it) }, label = "Nominal refund")
                            TKOutlinedField(state.note, { viewModel.onField("note", it) }, label = "Catatan")
                            TKButton("Proses", { viewModel.resolve(dispute.id) }, variant = TKButtonVariant.Tonal)
                        }
                    }
                }
                state.tab == 1 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.leaders, key = { it.technicianId }) { leader ->
                        Text(
                            "${leader.name} · ${String.format("%.1f", leader.ratingAvg)} · ${leader.jobsCount} job · terima ${leader.acceptRate}%",
                            modifier = Modifier.padding(vertical = TKSpacing.Xs)
                        )
                    }
                }
                state.tab == 2 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.audit, key = { it.id }) { log ->
                        Text("${log.action} · ${log.target}", modifier = Modifier.padding(vertical = TKSpacing.Xs))
                    }
                }
                state.tab == 3 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.title, { viewModel.onField("title", it) }, label = "Judul pengumuman")
                    TKOutlinedField(state.body, { viewModel.onField("body", it) }, label = "Isi pengumuman")
                    TKButton("Kirim ke semua pengguna", viewModel::announce)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.announcements, key = { it.id }) { item ->
                            Text("${item.title}: ${item.body}", modifier = Modifier.padding(vertical = TKSpacing.Xs))
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.configs, key = { it.key }) { config ->
                        var value by remember(config.key) { mutableStateOf(config.value) }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(config.key, modifier = Modifier.weight(1f))
                            TextField(value = value, onValueChange = { value = it }, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.setConfig(config.key, value) }) { Text("Simpan") }
                        }
                    }
                }
            }
        }
    }
}
