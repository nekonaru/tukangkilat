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

data class PlatformUiState(
    val tab: Int = 0,
    val summary: PlatformSummaryDto? = null,
    val flags: List<FeatureFlagDto> = emptyList(),
    val content: List<ContentPageDto> = emptyList(),
    val broadcasts: List<BroadcastDto> = emptyList(),
    val reports: List<ReviewReportDto> = emptyList(),
    val policies: List<SlaPolicyDto> = emptyList(),
    val breaches: List<SlaBreachDto> = emptyList(),
    val weekly: List<WeeklyReportDto> = emptyList(),
    val flagKey: String = "",
    val flagNote: String = "",
    val contentCode: String = "",
    val contentTitle: String = "",
    val contentBody: String = "",
    val broadcastTitle: String = "",
    val broadcastBody: String = "",
    val broadcastRole: String = "KONSUMEN",
    val broadcastAt: String = "",
    val notice: String? = null
)

@HiltViewModel
class AdminPlatformViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(PlatformUiState())
    val state: StateFlow<PlatformUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = PlatformUiState(
                    tab = current.tab, notice = current.notice,
                    flagKey = current.flagKey, flagNote = current.flagNote,
                    contentCode = current.contentCode, contentTitle = current.contentTitle, contentBody = current.contentBody,
                    broadcastTitle = current.broadcastTitle, broadcastBody = current.broadcastBody,
                    broadcastRole = current.broadcastRole, broadcastAt = current.broadcastAt,
                    summary = runCatching { api.platformSummary() }.getOrNull(),
                    flags = runCatching { api.featureFlags() }.getOrDefault(emptyList()),
                    content = runCatching { api.adminContent() }.getOrDefault(emptyList()),
                    broadcasts = runCatching { api.broadcasts() }.getOrDefault(emptyList()),
                    reports = runCatching { api.reviewReports() }.getOrDefault(emptyList()),
                    policies = runCatching { api.slaPolicies() }.getOrDefault(emptyList()),
                    breaches = runCatching { api.slaBreaches() }.getOrDefault(emptyList()),
                    weekly = runCatching { api.weeklyReports() }.getOrDefault(emptyList())
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun field(name: String, value: String) {
        _state.value = when (name) {
            "flagKey" -> _state.value.copy(flagKey = value)
            "flagNote" -> _state.value.copy(flagNote = value)
            "contentCode" -> _state.value.copy(contentCode = value)
            "contentTitle" -> _state.value.copy(contentTitle = value)
            "contentBody" -> _state.value.copy(contentBody = value)
            "broadcastTitle" -> _state.value.copy(broadcastTitle = value)
            "broadcastBody" -> _state.value.copy(broadcastBody = value)
            "broadcastRole" -> _state.value.copy(broadcastRole = value)
            "broadcastAt" -> _state.value.copy(broadcastAt = value)
            else -> _state.value
        }
    }

    fun saveFlag(enabled: Boolean) {
        val s = _state.value
        if (s.flagKey.isBlank()) { _state.value = s.copy(notice = "Isi kunci fitur"); return }
        viewModelScope.launch {
            try {
                api.setFeatureFlag(s.flagKey, SaveFeatureFlagRequest(enabled, "", s.flagNote))
                _state.value = _state.value.copy(notice = "Flag disimpan", flagKey = "", flagNote = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveContent() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.saveContent(SaveContentRequest(s.contentCode, s.contentTitle, s.contentBody))
                _state.value = _state.value.copy(notice = "Konten disimpan", contentCode = "", contentTitle = "", contentBody = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun createBroadcast() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.createBroadcast(CreateBroadcastRequest(s.broadcastTitle, s.broadcastBody, s.broadcastRole, s.broadcastAt))
                _state.value = _state.value.copy(notice = "Siaran dijadwalkan", broadcastTitle = "", broadcastBody = "", broadcastAt = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun resolveReport(id: Long, hide: Boolean) {
        viewModelScope.launch {
            try {
                api.resolveReviewReport(id, ResolveReviewReportRequest(hide))
                _state.value = _state.value.copy(notice = "Laporan diproses")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun compensate(id: Long) {
        viewModelScope.launch {
            try {
                api.compensateBreach(id)
                _state.value = _state.value.copy(notice = "Kompensasi dikirim")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun generateReport() {
        viewModelScope.launch {
            try {
                api.generateWeeklyReport()
                _state.value = _state.value.copy(notice = "Laporan mingguan dibuat")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminPlatformScreen(onBack: () -> Unit, viewModel: AdminPlatformViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Platform", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Ringkasan", "Fitur", "Konten", "Siaran", "Moderasi", "SLA", "Laporan").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    state.summary?.let { s ->
                        TKCard {
                            Text("Pengguna ${s.totalUsers} · Pesanan ${s.totalOrders}", fontWeight = FontWeight.Bold)
                            Text("Pendapatan Rp${s.revenue}")
                            Text("Sengketa terbuka ${s.openDisputes} · SLA ${s.slaBreaches} · ulasan dilaporkan ${s.flaggedReviews}")
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.flagKey, { viewModel.field("flagKey", it) }, label = "Kunci fitur")
                    TKOutlinedField(state.flagNote, { viewModel.field("flagNote", it) }, label = "Catatan")
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKButton("Aktifkan", { viewModel.saveFlag(true) }, modifier = Modifier.weight(1f))
                        TKButton("Nonaktifkan", { viewModel.saveFlag(false) }, modifier = Modifier.weight(1f), variant = TKButtonVariant.Outlined)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.flags, key = { it.key }) { flag ->
                            Text("${flag.key}: ${if (flag.enabled) "aktif" else "nonaktif"}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.contentCode, { viewModel.field("contentCode", it) }, label = "Kode")
                    TKOutlinedField(state.contentTitle, { viewModel.field("contentTitle", it) }, label = "Judul")
                    TKOutlinedField(state.contentBody, { viewModel.field("contentBody", it) }, label = "Isi")
                    TKButton("Simpan konten", viewModel::saveContent, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.content, key = { it.id }) { page ->
                            Text("${page.code}: ${page.title}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                3 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.broadcastTitle, { viewModel.field("broadcastTitle", it) }, label = "Judul")
                    TKOutlinedField(state.broadcastBody, { viewModel.field("broadcastBody", it) }, label = "Isi")
                    TKOutlinedField(state.broadcastRole, { viewModel.field("broadcastRole", it) }, label = "Peran (KONSUMEN/TUKANG)")
                    TKOutlinedField(state.broadcastAt, { viewModel.field("broadcastAt", it) }, label = "Waktu ISO-8601")
                    TKButton("Jadwalkan siaran", viewModel::createBroadcast, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.broadcasts, key = { it.id }) { item ->
                            Text("${item.title} · ${item.status}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                4 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.reports, key = { it.id }) { report ->
                        TKCard {
                            Text("Laporan #${report.id} · ulasan ${report.reviewOrderId}")
                            Text(report.reason, style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                                TKButton("Sembunyikan", { viewModel.resolveReport(report.id, true) }, variant = TKButtonVariant.Tonal)
                                TKButton("Tolak", { viewModel.resolveReport(report.id, false) }, variant = TKButtonVariant.Outlined)
                            }
                        }
                    }
                }
                5 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.policies, key = { it.code }) { policy ->
                        Text("${policy.code}: ${policy.minutes} menit, kompensasi Rp${policy.compensation}", modifier = Modifier.padding(vertical = 4.dp))
                    }
                    items(state.breaches, key = { it.id }) { breach ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("Pesanan ${breach.orderId} · ${breach.minutesLate} menit", modifier = Modifier.weight(1f))
                            if (!breach.compensated) TextButton(onClick = { viewModel.compensate(breach.id) }) { Text("Kompensasi") }
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKButton("Buat laporan mingguan", viewModel::generateReport, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.weekly, key = { it.id }) { report ->
                            Text("${report.weekStart}: ${report.orders} pesanan · Rp${report.revenue}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
            }
        }
    }
}
