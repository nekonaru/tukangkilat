package id.tukangkilat.app.ui.admin

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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
    val messageReports: List<MessageReportDto> = emptyList(),
    val decision: String = "DISETUJUI",
    val amount: String = "",
    val note: String = "",
    val title: String = "",
    val body: String = "",
    val notice: String? = null,
    val voucherConsumer: String = "",
    val voucherAmount: String = "",
    val documents: List<TechDocumentDto> = emptyList()
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
                    configs = runCatching { api.systemConfigs() }.getOrDefault(emptyList()),
                    messageReports = runCatching { api.messageReports() }.getOrDefault(emptyList()),
                    documents = runCatching { api.adminDocuments("MENUNGGU") }.getOrDefault(emptyList())
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

    fun resolveReport(id: Long, hideMessage: Boolean) {
        viewModelScope.launch {
            try {
                api.resolveMessageReport(id, mapOf("hideMessage" to hideMessage))
                _state.value = _state.value.copy(notice = "Laporan diproses")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun exportCsv(context: android.content.Context, kind: String) {
        viewModelScope.launch {
            val message = withContext(Dispatchers.IO) {
                try {
                    val body = if (kind == "orders") api.exportOrders() else api.exportWallet()
                    val name = if (kind == "orders") "pesanan.csv" else "dompet.csv"
                    val file = File(context.cacheDir, name)
                    body.byteStream().use { input -> file.outputStream().use { output -> input.copyTo(output) } }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/csv"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    withContext(Dispatchers.Main) {
                        context.startActivity(Intent.createChooser(intent, "Bagikan $name"))
                    }
                    "Ekspor $name siap dibagikan"
                } catch (e: Exception) {
                    e.cleanApiError()
                }
            }
            _state.value = _state.value.copy(notice = message)
        }
    }

    fun onVoucher(field: String, value: String) {
        _state.value = when (field) {
            "consumer" -> _state.value.copy(voucherConsumer = value)
            "amount" -> _state.value.copy(voucherAmount = value)
            else -> _state.value
        }
    }

    fun issueVoucher() {
        val s = _state.value
        val consumer = s.voucherConsumer.toLongOrNull()
        val amount = s.voucherAmount.toLongOrNull()
        if (consumer == null || amount == null || amount <= 0) {
            _state.value = s.copy(notice = "Isi pengguna dan nominal dengan benar")
            return
        }
        viewModelScope.launch {
            try {
                api.issueVoucher(mapOf("consumerId" to consumer, "amount" to amount))
                _state.value = _state.value.copy(notice = "Voucher diterbitkan", voucherConsumer = "", voucherAmount = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun verifyDocument(id: Long, approve: Boolean) {
        viewModelScope.launch {
            try {
                api.verifyDocument(id, VerifyDocumentRequest(approve, ""))
                _state.value = _state.value.copy(notice = "Dokumen diverifikasi")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@Composable
fun ControlScreen(onBack: () -> Unit, viewModel: ControlViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Pusat Kendali", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Sengketa", "Peringkat", "Audit", "Siaran", "Konfigurasi", "Laporan", "Ekspor", "Voucher", "Dokumen").forEachIndexed { i, title ->
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
                state.tab == 4 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.configs, key = { it.key }) { config ->
                        var value by remember(config.key) { mutableStateOf(config.value) }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Text(config.key, modifier = Modifier.weight(1f))
                            TextField(value = value, onValueChange = { value = it }, modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.setConfig(config.key, value) }) { Text("Simpan") }
                        }
                    }
                }
                state.tab == 5 -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.messageReports, key = { it.id }) { report ->
                        Column(Modifier.padding(vertical = TKSpacing.Xs)) {
                            Text("Laporan #${report.id} · ${report.reporterName} · ${report.status}")
                            Text("Pesan: ${report.messageBody}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            Text("Alasan: ${report.reason}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(TKSpacing.Xs)) {
                                TKButton("Sembunyikan pesan", { viewModel.resolveReport(report.id, true) }, variant = TKButtonVariant.Tonal)
                                TKButton("Tolak laporan", { viewModel.resolveReport(report.id, false) }, variant = TKButtonVariant.Outlined)
                            }
                        }
                    }
                }
                state.tab == 6 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKButton("Ekspor pesanan (CSV)", { viewModel.exportCsv(context, "orders") })
                    TKButton("Ekspor dompet (CSV)", { viewModel.exportCsv(context, "wallet") }, variant = TKButtonVariant.Tonal)
                    Text("Berkas dibuka lewat aplikasi pembagian yang tersedia.", style = MaterialTheme.typography.bodySmall)
                }
                state.tab == 7 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    Text("Terbitkan voucher untuk konsumen.", style = MaterialTheme.typography.bodySmall)
                    TKOutlinedField(state.voucherConsumer, { viewModel.onVoucher("consumer", it) }, label = "ID konsumen")
                    TKOutlinedField(state.voucherAmount, { viewModel.onVoucher("amount", it) }, label = "Nominal voucher")
                    TKButton("Terbitkan voucher", viewModel::issueVoucher, variant = TKButtonVariant.Tonal)
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                    items(state.documents, key = { it.id }) { doc ->
                        Column(Modifier.padding(vertical = TKSpacing.Xs)) {
                            Text("Dokumen #${doc.id} · teknisi ${doc.docType}")
                            Text("Status ${doc.status} · ${doc.createdAt.take(10)}", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                                TKButton("Setujui", { viewModel.verifyDocument(doc.id, true) }, variant = TKButtonVariant.Tonal)
                                TKButton("Tolak", { viewModel.verifyDocument(doc.id, false) }, variant = TKButtonVariant.Outlined)
                            }
                        }
                    }
                }
            }
        }
    }
}
