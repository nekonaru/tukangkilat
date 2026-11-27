package id.tukangkilat.app.ui.technician

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

data class TechEconomyUiState(
    val tab: Int = 0,
    val reputation: ReputationDto? = null,
    val certifications: List<CertificationDto> = emptyList(),
    val courses: List<CourseDto> = emptyList(),
    val enrollments: List<EnrollmentDto> = emptyList(),
    val gear: List<GearDto> = emptyList(),
    val teams: List<TeamDto> = emptyList(),
    val helpers: List<HelperDto> = emptyList(),
    val quality: List<QualityDto> = emptyList(),
    val storefront: StorefrontDto? = null,
    val tax: TaxDto? = null,
    val coarse: String = "",
    val amount: String = "",
    val headline: String = "",
    val description: String = "",
    val price: String = "",
    val helperId: String = "",
    val dayOfWeek: String = "1",
    val hour: String = "9",
    val notice: String? = null
)

@HiltViewModel
class TechEconomyViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(TechEconomyUiState())
    val state: StateFlow<TechEconomyUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = TechEconomyUiState(
                    tab = current.tab, notice = current.notice, coarse = current.coarse,
                    amount = current.amount, headline = current.headline, description = current.description,
                    price = current.price, helperId = current.helperId, dayOfWeek = current.dayOfWeek, hour = current.hour,
                    reputation = runCatching { api.reputation() }.getOrNull(),
                    certifications = runCatching { api.certifications() }.getOrDefault(emptyList()),
                    courses = runCatching { api.courses() }.getOrDefault(emptyList()),
                    enrollments = runCatching { api.enrollments() }.getOrDefault(emptyList()),
                    gear = runCatching { api.gear() }.getOrDefault(emptyList()),
                    teams = runCatching { api.teams() }.getOrDefault(emptyList()),
                    helpers = runCatching { api.helpers() }.getOrDefault(emptyList()),
                    quality = runCatching { api.qualityScores() }.getOrDefault(emptyList()),
                    storefront = runCatching { api.storefront() }.getOrNull(),
                    tax = current.tax
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun field(name: String, value: String) {
        _state.value = when (name) {
            "coarse" -> _state.value.copy(coarse = value)
            "amount" -> _state.value.copy(amount = value)
            "headline" -> _state.value.copy(headline = value)
            "description" -> _state.value.copy(description = value)
            "price" -> _state.value.copy(price = value)
            "helperId" -> _state.value.copy(helperId = value)
            "dayOfWeek" -> _state.value.copy(dayOfWeek = value)
            "hour" -> _state.value.copy(hour = value)
            else -> _state.value
        }
    }

    fun completeCert(code: String) {
        viewModelScope.launch {
            try {
                api.completeCertification(code, CompleteCertRequest(80))
                _state.value = _state.value.copy(notice = "Sertifikasi selesai")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun enroll(code: String) {
        viewModelScope.launch {
            try { api.enroll(code); api.completeCourse(code); _state.value = _state.value.copy(notice = "Kursus selesai"); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun buyGear(id: Long) {
        viewModelScope.launch {
            try { api.buyGear(id); _state.value = _state.value.copy(notice = "Alat dibeli"); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun createTeam() {
        val name = _state.value.coarse.trim()
        if (name.isBlank()) return
        viewModelScope.launch {
            try { api.createTeam(CreateTeamRequest(name)); _state.value = _state.value.copy(notice = "Tim dibuat", coarse = ""); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun simulateTax() {
        val gross = _state.value.amount.toLongOrNull()
        if (gross == null) return
        viewModelScope.launch {
            try { _state.value = _state.value.copy(tax = api.simulateTax(SimulateTaxRequest(gross, 10))) }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveStorefront() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.saveStorefront(SaveStorefrontRequest(s.headline, s.description, s.price.toLongOrNull() ?: 0))
                _state.value = s.copy(notice = "Toko disimpan")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun addHelper(teamId: Long) {
        val id = _state.value.helperId.toLongOrNull()
        if (id == null) { _state.value = _state.value.copy(notice = "ID pembantu tidak valid"); return }
        viewModelScope.launch {
            try {
                api.addHelper(AddHelperRequest(teamId, id))
                _state.value = _state.value.copy(notice = "Pembantu ditambahkan", helperId = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveHeatmap() {
        val s = _state.value
        val day = s.dayOfWeek.toIntOrNull()
        val hour = s.hour.toIntOrNull()
        if (day == null || hour == null) { _state.value = s.copy(notice = "Hari dan jam tidak valid"); return }
        viewModelScope.launch {
            try {
                api.saveHeatmap(SaveHeatmapRequest(day, hour, 3))
                _state.value = s.copy(notice = "Heatmap disimpan")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun forecast() {
        viewModelScope.launch {
            try {
                val result = api.forecast(mapOf("period" to "MINGGUAN"))
                _state.value = _state.value.copy(notice = "Perkiraan Rp${result.expected}")
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun payout() {
        viewModelScope.launch {
            try {
                api.savePayoutSchedule(SavePayoutScheduleRequest("MINGGUAN", 100_000, true))
                _state.value = _state.value.copy(notice = "Jadwal penarikan disimpan")
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechEconomyScreen(onBack: () -> Unit, viewModel: TechEconomyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Ekonomi Teknisi", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Reputasi", "Sertifikat", "Akademi", "Alat", "Tim", "Pajak", "Toko", "Jadwal", "Kualitas").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    state.reputation?.let { r ->
                        TKCard {
                            Text("Skor keseluruhan ${r.overall}", fontWeight = FontWeight.Bold)
                            Text("Kualitas ${r.quality} · respons ${r.response} · ketepatan ${r.punctuality} · kepuasan ${r.satisfaction}")
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.certifications, key = { it.id }) { cert ->
                        TKCard {
                            Text("${cert.title} · ${cert.status}")
                            Text("Berlaku sampai ${cert.expiresAt} · nilai ${cert.score}")
                            TKButton("Selesaikan ujian", { viewModel.completeCert(cert.code) }, variant = TKButtonVariant.Tonal)
                        }
                    }
                }
                2 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.courses, key = { it.code }) { course ->
                        TKCard {
                            Text("${course.title} · ${course.minutes} menit", fontWeight = FontWeight.Bold)
                            Text("Reward ${course.rewardPoints} poin")
                            TKButton("Ambil kursus", { viewModel.enroll(course.code) }, variant = TKButtonVariant.Tonal)
                        }
                    }
                }
                3 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.gear, key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text("${item.name} · ${item.pricePoints} poin", modifier = Modifier.weight(1f))
                            if (!item.owned) TextButton(onClick = { viewModel.buyGear(item.id) }) { Text("Beli") }
                            else Text("Dimiliki", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                4 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.coarse, { viewModel.field("coarse", it) }, label = "Nama tim")
                    TKButton("Buat tim", viewModel::createTeam, variant = TKButtonVariant.Tonal)
                    TKOutlinedField(state.helperId, { viewModel.field("helperId", it) }, label = "ID pembantu")
                    state.teams.forEach { team ->
                        Column {
                            Text("${team.name} · ${state.helpers.count { it.teamId == team.id }} pembantu")
                            TKButton("Tambah pembantu", { viewModel.addHelper(team.id) }, variant = TKButtonVariant.Outlined)
                        }
                    }
                }
                5 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.amount, { viewModel.field("amount", it) }, label = "Penghasilan kotor")
                    TKButton("Simulasi pajak 10%", viewModel::simulateTax, variant = TKButtonVariant.Tonal)
                    state.tax?.let { Text("Pajak Rp${it.taxAmount} · bersih Rp${it.net}") }
                }
                6 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.headline, { viewModel.field("headline", it) }, label = "Judul toko")
                    TKOutlinedField(state.description, { viewModel.field("description", it) }, label = "Deskripsi")
                    TKOutlinedField(state.price, { viewModel.field("price", it) }, label = "Tarif khusus")
                    TKButton("Simpan toko", viewModel::saveStorefront, variant = TKButtonVariant.Tonal)
                    state.storefront?.let { Text("${it.headline}: ${it.description} · Rp${it.customPrice}") }
                }
                7 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKButton("Hitung perkiraan pendapatan", viewModel::forecast, variant = TKButtonVariant.Tonal)
                    TKButton("Aktifkan penarikan mingguan", viewModel::payout, variant = TKButtonVariant.Outlined)
                    TKOutlinedField(state.dayOfWeek, { viewModel.field("dayOfWeek", it) }, label = "Hari heatmap (1-7)")
                    TKOutlinedField(state.hour, { viewModel.field("hour", it) }, label = "Jam heatmap (0-23)")
                    TKButton("Simpan heatmap", viewModel::saveHeatmap)
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.quality, key = { it.id }) { item ->
                        Text("Pesanan ${item.orderId} · skor ${item.score} · ${item.note}", modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
