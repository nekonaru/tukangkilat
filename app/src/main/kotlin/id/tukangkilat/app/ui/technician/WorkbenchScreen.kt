package id.tukangkilat.app.ui.technician

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val reviews: List<ReviewDto> = emptyList(),
    val payout: PayoutAccountDto? = null,
    val schedules: List<AutoOnlineScheduleDto> = emptyList(),
    val date: String = "",
    val slot: String = "",
    val bank: String = "",
    val accountNo: String = "",
    val accountName: String = "",
    val dayOfWeek: String = "1",
    val startTime: String = "08:00",
    val endTime: String = "17:00",
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
                val reviews = runCatching { api.technicianReviews() }.getOrDefault(emptyList())
                val schedules = runCatching { api.autoSchedules() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    isLoading = false, blocks = blocks, portfolio = portfolio, stats = stats,
                    payout = payout, reviews = reviews, schedules = schedules
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
            "dayOfWeek" -> _state.value.copy(dayOfWeek = value)
            "startTime" -> _state.value.copy(startTime = value)
            "endTime" -> _state.value.copy(endTime = value)
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

    fun saveSchedule() {
        val s = _state.value
        val day = s.dayOfWeek.toIntOrNull()
        if (day == null || day !in 1..7) { _state.value = s.copy(notice = "Pilih hari yang valid"); return }
        viewModelScope.launch {
            try {
                api.saveAutoSchedule(CreateAutoScheduleRequest(day, s.startTime, s.endTime))
                _state.value = _state.value.copy(notice = "Jadwal otomatis disimpan")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun deleteSchedule(id: Long) {
        viewModelScope.launch {
            try { api.deleteAutoSchedule(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun uploadPortfolio(context: android.content.Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(notice = "Mengunggah ${uris.size} foto")
            var uploaded = 0
            for (uri in uris) {
                try {
                    val bytes = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Berkas tidak bisa dibaca")
                    }
                    val part = MultipartBody.Part.createFormData(
                        "file", "portfolio.jpg", bytes.toRequestBody("image/jpeg".toMediaType())
                    )
                    val url = api.uploadPhoto(part)["url"] ?: throw IllegalStateException("Unggahan gagal")
                    api.addPortfolio(AddPortfolioRequest(url, "Pekerjaan selesai"))
                    uploaded++
                } catch (e: Exception) {
                    _state.value = _state.value.copy(notice = e.cleanApiError())
                }
            }
            _state.value = _state.value.copy(notice = "$uploaded foto ditambahkan")
            refresh()
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
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { uris ->
        viewModel.uploadPortfolio(context, uris)
    }
    var preview by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Ruang Kerja", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = state.tab) {
                listOf("Kalender", "Portofolio", "Performa", "Ulasan", "Rekening").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKOutlinedField(state.date, { viewModel.onField("date", it) }, label = "Tanggal (YYYY-MM-DD)")
                    TKOutlinedField(state.slot, { viewModel.onField("slot", it) }, label = "Slot, misal 09.00")
                    TKButton("Blokir jadwal", viewModel::addBlock)
                    SectionLabel("Jadwal online otomatis")
                    TKOutlinedField(state.dayOfWeek, { viewModel.onField("dayOfWeek", it) }, label = "Hari (1 Senin - 7 Minggu)")
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKOutlinedField(state.startTime, { viewModel.onField("startTime", it) }, label = "Mulai (HH:mm)", modifier = Modifier.weight(1f))
                        TKOutlinedField(state.endTime, { viewModel.onField("endTime", it) }, label = "Selesai (HH:mm)", modifier = Modifier.weight(1f))
                    }
                    TKButton("Simpan jadwal otomatis", viewModel::saveSchedule, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.schedules, key = { it.id }) { schedule ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("Hari ${schedule.dayOfWeek} · ${schedule.startTime}-${schedule.endTime}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.deleteSchedule(schedule.id) }) { Text("Hapus") }
                            }
                        }
                        items(state.blocks, key = { it.id }) { block ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text("Blokir ${block.date} · ${block.slotLabel}", modifier = Modifier.weight(1f))
                                TextButton(onClick = { viewModel.deleteBlock(block.id) }) { Text("Hapus") }
                            }
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKButton("Tambah foto portofolio (maks 5)", {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.portfolio, key = { it.id }) { item ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                AsyncImage(
                                    model = "${BuildConfig.BASE_URL}${item.photoUrl}",
                                    contentDescription = item.caption,
                                    modifier = Modifier.size(56.dp).clip(TKShape.Media).clickable { preview = item.photoUrl }
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
                3 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    items(state.reviews, key = { it.orderId }) { review ->
                        Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card).padding(TKSpacing.Sm)) {
                            Text("${review.serviceName} · ${review.orderCode}", fontWeight = FontWeight.Bold)
                            Text("${review.stars} bintang · ${review.createdAt.take(10)}")
                            Text(review.comment)
                            if (review.photos.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    review.photos.forEach { url ->
                                        AsyncImage(
                                            model = "${BuildConfig.BASE_URL}${url}",
                                            contentDescription = "Foto ulasan",
                                            modifier = Modifier.size(72.dp).clip(TKShape.Media).clickable { preview = url }
                                        )
                                    }
                                }
                            }
                        }
                    }
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

    preview?.let { url ->
        AlertDialog(
            onDismissRequest = { preview = null },
            confirmButton = { TextButton(onClick = { preview = null }) { Text("Tutup") } },
            text = {
                AsyncImage(
                    model = "${BuildConfig.BASE_URL}${url}",
                    contentDescription = "Pratinjau foto",
                    modifier = Modifier.fillMaxWidth().height(320.dp).clip(TKShape.Media).background(TKColor.InkSoft)
                )
            }
        )
    }
}
