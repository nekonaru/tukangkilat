package id.tukangkilat.app.ui.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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

data class AdminQualityUiState(
    val limits: List<RateLimitDto> = emptyList(),
    val versions: List<ApiVersionDto> = emptyList(),
    val jobs: List<ScheduledJobDto> = emptyList(),
    val code: String = "",
    val maxCalls: String = "60",
    val notice: String? = null
)

@HiltViewModel
class AdminQualityViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(AdminQualityUiState())
    val state: StateFlow<AdminQualityUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = AdminQualityUiState(
                    limits = runCatching { api.rateLimits() }.getOrDefault(emptyList()),
                    versions = runCatching { api.apiVersions() }.getOrDefault(emptyList()),
                    jobs = runCatching { api.scheduledJobs() }.getOrDefault(emptyList()),
                    code = current.code, maxCalls = current.maxCalls, notice = current.notice
                )
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun field(name: String, value: String) {
        when (name) {
            "code" -> _state.value = _state.value.copy(code = value)
            "maxCalls" -> _state.value = _state.value.copy(maxCalls = value)
        }
    }

    fun saveLimit() {
        val s = _state.value
        if (s.code.isBlank()) return
        viewModelScope.launch {
            try {
                api.saveRateLimit(s.code, SaveRateLimitRequest(s.maxCalls.toIntOrNull() ?: 60, 60, true))
                _state.value = s.copy(notice = "Batas disimpan", code = "")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun deprecate(version: String) {
        viewModelScope.launch {
            try {
                api.saveApiVersion(version, SaveApiVersionRequest("DEPREKATED", "Akan dihentikan"))
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun runJob(code: String) {
        viewModelScope.launch {
            try { api.runJob(code); _state.value = _state.value.copy(notice = "Jadwal dijalankan"); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@Composable
fun AdminQualityScreen(onBack: () -> Unit, viewModel: AdminQualityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Kualitas Platform", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = 0) { Tab(selected = true, onClick = {}, text = { Text("Pengaturan") }) }
            LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                item { SectionLabel("Batas laju") }
                item {
                    TKOutlinedField(state.code, { viewModel.field("code", it) }, label = "Kode")
                    TKOutlinedField(state.maxCalls, { viewModel.field("maxCalls", it) }, label = "Maksimal panggilan")
                    TKButton("Simpan batas", viewModel::saveLimit, variant = TKButtonVariant.Tonal)
                }
                items(state.limits, key = { it.code }) { limit ->
                    Text("${limit.code} · ${limit.maxCalls}/${limit.windowSeconds}s", modifier = Modifier.padding(vertical = 4.dp))
                }
                item { SectionLabel("Versi API") }
                items(state.versions, key = { it.version }) { version ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${version.version} · ${version.status}", modifier = Modifier.weight(1f))
                        if (version.status == "AKTIF") TextButton(onClick = { viewModel.deprecate(version.version) }) { Text("Hentikan") }
                    }
                }
                item { SectionLabel("Jadwal") }
                items(state.jobs, key = { it.id }) { job ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("${job.name} · ${job.intervalMs}ms", modifier = Modifier.weight(1f))
                        TextButton(onClick = { viewModel.runJob(job.code) }) { Text("Jalankan") }
                    }
                }
            }
        }
    }
}
