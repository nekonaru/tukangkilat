package id.tukangkilat.app.ui.technician

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.rememberAsyncImagePainter
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class WorkbenchUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tab: Int = 0,
    val blocks: List<AvailabilityBlockDto> = emptyList(),
    val portfolio: List<PortfolioDto> = emptyList(),
    val stats: TechnicianStatsDto? = null,
    val payout: PayoutAccountDto? = null,
    val date: String = "",
    val slot: String = "",
    val bank: String = "",
    val accountNo: String = "",
    val accountName: String = "",
    val notice: String? = null
)

@HiltViewModel
class WorkbenchViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(WorkbenchUiState())
    val state: StateFlow<WorkbenchUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val blocks = runCatching { api.availability() }.getOrDefault(emptyList())
                val portfolio = runCatching { api.portfolio() }.getOrDefault(emptyList())
                val stats = runCatching { api.technicianStats() }.getOrNull()
                val payout = runCatching { api.payoutAccount() }.getOrNull()
                _state.value = _state.value.copy(
                    isLoading = false, blocks = blocks, portfolio = portfolio, stats = stats, payout = payout
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun onField(field: String, value: String) {
        _state.value = when (field) {
            "date" -> _state.value.copy(date = value)
            "slot" -> _state.value.copy(slot = value)
            "bank" -> _state.value.copy(bank = value)
            "accountNo" -> _state.value.copy(accountNo = value)
            "accountName" -> _state.value.copy(accountName = value)
            else -> _state.value
        }
    }

    fun addBlock() {
        val s = _state.value
        if (s.date.isBlank() || s.slot.isBlank()) { _state.value = s.copy(notice = "Isi tanggal dan slot"); return }
        viewModelScope.launch {
            try {
                api.addAvailability(CreateAvailabilityBlockRequest(s.date, s.slot))
                _state.value = _state.value.copy(date = "", slot = "", notice = "Jadwal diblokir")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun deleteBlock(id: Long) {
        viewModelScope.launch {
            try { api.deleteAvailability(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun uploadPortfolio(context: android.content.Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Berkas tidak bisa dibaca")
                }
                val part = MultipartBody.Part.createFormData("file", "portfolio.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                val uploaded = api.uploadPhoto(part)
                val url = uploaded["url"] ?: throw IllegalStateException("Unggahan gagal")
                api.addPortfolio(AddPortfolioRequest(url, "Pekerjaan selesai"))
                _state.value = _state.value.copy(notice = "Foto ditambahkan")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun deletePortfolio(id: Long) {
        viewModelScope.launch {
            try { api.deletePortfolio(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun savePayout() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.savePayoutAccount(SavePayoutAccountRequest(s.bank, s.accountNo, s.accountName))
                _state.value = _state.value.copy(notice = "Rekening disimpan")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkbenchScreen(onBack: () -> Unit, viewModel: WorkbenchViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.uploadPortfolio(context, uri)
    }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Ruang Kerja", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab) {
                listOf("Kalender", "Portofolio", "Performa", "Rekening").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKOutlinedField(state.date, { viewModel.onField("date", it) }, label = "Tanggal (YYYY-MM-DD)")
                    TKOutlinedField(state.slot, { viewModel.onField("slot", it) }, label = "Slot, misal 09.00")
                    TKButton("Blokir jadwal", viewModel::addBlock)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.blocks, key = { it.id }) { block ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("${block.date} · ${block.slotLabel}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.deleteBlock(block.id) }) { Text("Hapus") }
                            }
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKButton("Tambah foto portofolio", {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.portfolio, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Image(
                                    painter = rememberAsyncImagePainter(item.photoUrl),
                                    contentDescription = item.caption,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(item.caption, modifier = Modifier.weight(1f).padding(start = TKSpacing.Xs))
                                TextButton(onClick = { viewModel.deletePortfolio(item.id) }) { Text("Hapus") }
                            }
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    state.stats?.let { stats ->
                        TKCard { Text("Rating ${String.format("%.1f", stats.ratingAvg)}", fontWeight = FontWeight.Bold) }
                        TKCard { Text("Pekerjaan selesai: ${stats.jobsCount}") }
                        TKCard { Text("Tawaran: ${stats.acceptedOffers} dari ${stats.totalOffers} diterima (${stats.acceptRate}%)") }
                    } ?: Text("Belum ada data performa.")
                }
                else -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKOutlinedField(state.bank, { viewModel.onField("bank", it) }, label = "Bank")
                    TKOutlinedField(state.accountNo, { viewModel.onField("accountNo", it) }, label = "Nomor rekening")
                    TKOutlinedField(state.accountName, { viewModel.onField("accountName", it) }, label = "Nama pemilik")
                    TKButton("Simpan rekening", viewModel::savePayout)
                    state.payout?.let { Text("Tersimpan: ${it.bank} - ${it.accountNo}") }
                }
            }
        }
    }
}
