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
import id.tukangkilat.app.BuildConfig
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

data class OpsUiState(
    val tab: Int = 0,
    val documents: List<TechDocumentDto> = emptyList(),
    val areas: List<ServiceAreaDto> = emptyList(),
    val rule: AutoAcceptDto? = null,
    val route: List<RouteStopDto> = emptyList(),
    val equipment: List<EquipmentDto> = emptyList(),
    val journal: List<JournalDto> = emptyList(),
    val leaves: List<LeaveDto> = emptyList(),
    val templates: List<TemplateDto> = emptyList(),
    val badges: List<BadgeDto> = emptyList(),
    val stats: OpsStatsDto? = null,
    val docType: String = "KTP",
    val docUrl: String = "",
    val areaName: String = "",
    val areaLat: String = "",
    val areaLng: String = "",
    val journalNote: String = "",
    val templateTitle: String = "",
    val templateBody: String = "",
    val leaveStart: String = "",
    val leaveEnd: String = "",
    val materialName: String = "",
    val materialQty: String = "1",
    val materialCost: String = "0",
    val checkinLat: String = "",
    val checkinLng: String = "",
    val notice: String? = null
)

@HiltViewModel
class OpsViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(OpsUiState())
    val state: StateFlow<OpsUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = OpsUiState(
                    tab = current.tab,
                    notice = current.notice,
                    docType = current.docType, docUrl = current.docUrl,
                    areaName = current.areaName, areaLat = current.areaLat, areaLng = current.areaLng,
                    journalNote = current.journalNote, templateTitle = current.templateTitle,
                    templateBody = current.templateBody, leaveStart = current.leaveStart, leaveEnd = current.leaveEnd,
                    documents = runCatching { api.techDocuments() }.getOrDefault(emptyList()),
                    areas = runCatching { api.serviceAreas() }.getOrDefault(emptyList()),
                    materialName = current.materialName,
                    materialQty = current.materialQty,
                    materialCost = current.materialCost,
                    checkinLat = current.checkinLat, checkinLng = current.checkinLng,
                    rule = runCatching { api.autoAccept() }.getOrNull(),
                    route = runCatching { api.route() }.getOrDefault(emptyList()),
                    equipment = runCatching { api.equipment() }.getOrDefault(emptyList()),
                    journal = runCatching { api.journal() }.getOrDefault(emptyList()),
                    leaves = runCatching { api.leaves() }.getOrDefault(emptyList()),
                    templates = runCatching { api.chatTemplates() }.getOrDefault(emptyList()),
                    badges = runCatching { api.badges() }.getOrDefault(emptyList()),
                    stats = runCatching { api.opsStats() }.getOrNull()
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }

    fun field(name: String, value: String) {
        _state.value = when (name) {
            "docType" -> _state.value.copy(docType = value)
            "docUrl" -> _state.value.copy(docUrl = value)
            "areaName" -> _state.value.copy(areaName = value)
            "areaLat" -> _state.value.copy(areaLat = value)
            "areaLng" -> _state.value.copy(areaLng = value)
            "journalNote" -> _state.value.copy(journalNote = value)
            "templateTitle" -> _state.value.copy(templateTitle = value)
            "templateBody" -> _state.value.copy(templateBody = value)
            "leaveStart" -> _state.value.copy(leaveStart = value)
            "leaveEnd" -> _state.value.copy(leaveEnd = value)
            "checkinLat" -> _state.value.copy(checkinLat = value)
            "checkinLng" -> _state.value.copy(checkinLng = value)
            else -> _state.value
        }
    }

    fun addDocument() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.addTechDocument(AddDocumentRequest(s.docType, s.docUrl))
                _state.value = s.copy(notice = "Dokumen dikirim", docUrl = "")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun addArea(excluded: Boolean) {
        val s = _state.value
        val lat = s.areaLat.toDoubleOrNull()
        val lng = s.areaLng.toDoubleOrNull()
        if (s.areaName.isBlank() || lat == null || lng == null) {
            _state.value = s.copy(notice = "Isi nama dan koordinat wilayah")
            return
        }
        viewModelScope.launch {
            try {
                api.addServiceArea(AddServiceAreaRequest(s.areaName, lat, lng, 10, excluded))
                _state.value = _state.value.copy(notice = "Wilayah ditambahkan", areaName = "", areaLat = "", areaLng = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveRule(enabled: Boolean, minPrice: String, maxDistance: String) {
        viewModelScope.launch {
            try {
                api.saveAutoAccept(SaveAutoAcceptRequest(
                    enabled, minPrice.toLongOrNull() ?: 200_000, (maxDistance.toIntOrNull() ?: 20).coerceIn(1, 100), ""
                ))
                _state.value = _state.value.copy(notice = "Aturan disimpan")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun addJournal() {
        val s = _state.value
        if (s.journalNote.isBlank()) { _state.value = s.copy(notice = "Catatan wajib diisi"); return }
        viewModelScope.launch {
            try {
                api.addJournal(AddJournalRequest(java.time.LocalDate.now().toString(), s.journalNote))
                _state.value = _state.value.copy(notice = "Jurnal disimpan", journalNote = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun addTemplate() {
        val s = _state.value
        if (s.templateTitle.isBlank() || s.templateBody.isBlank()) {
            _state.value = s.copy(notice = "Judul dan isi wajib")
            return
        }
        viewModelScope.launch {
            try {
                api.addChatTemplate(AddTemplateRequest(s.templateTitle, s.templateBody))
                _state.value = _state.value.copy(notice = "Template disimpan", templateTitle = "", templateBody = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun addLeave() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.addLeave(AddLeaveRequest(s.leaveStart, s.leaveEnd, "Libur teknisi"))
                _state.value = _state.value.copy(notice = "Pengajuan libur dikirim", leaveStart = "", leaveEnd = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun toggleEquipment(name: String, checked: Boolean) {
        viewModelScope.launch {
            try {
                api.setEquipment(SetEquipmentRequest(name, checked))
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun checkin(orderId: Long, kind: String) {
        val s = _state.value
        val lat = s.checkinLat.toDoubleOrNull()
        val lng = s.checkinLng.toDoubleOrNull()
        if (lat == null || lng == null) {
            _state.value = s.copy(notice = "Isi koordinat check-in")
            return
        }
        viewModelScope.launch {
            try {
                api.checkin(orderId, CheckinRequest(kind, lat, lng))
                _state.value = _state.value.copy(notice = "Check-in $kind tersimpan")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun addMaterial(orderId: Long) {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.addMaterial(AddMaterialRequest(
                    orderId, s.materialName, s.materialQty.toIntOrNull() ?: 1, s.materialCost.toLongOrNull() ?: 0
                ))
                _state.value = _state.value.copy(notice = "Material ditambahkan", materialName = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpsScreen(onBack: () -> Unit, viewModel: OpsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Operasional Teknisi", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Dokumen", "Wilayah", "Otomatis", "Rute", "Alat", "Jurnal", "Chat", "Libur", "Prestasi").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.docType, { viewModel.field("docType", it) }, label = "Jenis (KTP, SERTIFIKAT)")
                    TKOutlinedField(state.docUrl, { viewModel.field("docUrl", it) }, label = "URL berkas")
                    TKButton("Kirim dokumen", viewModel::addDocument, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.documents, key = { it.id }) { doc ->
                            Text("${doc.docType} · ${doc.status} · ${doc.createdAt.take(10)}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.areaName, { viewModel.field("areaName", it) }, label = "Nama wilayah")
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKOutlinedField(state.areaLat, { viewModel.field("areaLat", it) }, label = "Latitude", modifier = Modifier.weight(1f))
                        TKOutlinedField(state.areaLng, { viewModel.field("areaLng", it) }, label = "Longitude", modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKButton("Tambah wilayah", { viewModel.addArea(false) }, modifier = Modifier.weight(1f))
                        TKButton("Kecualikan", { viewModel.addArea(true) }, modifier = Modifier.weight(1f), variant = TKButtonVariant.Outlined)
                    }
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.areas, key = { it.id }) { area ->
                            Text("${area.areaName} · ${area.radiusKm} km${if (area.isExcluded) " · dikecualikan" else ""}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    val rule = state.rule
                    var enabled by remember(rule?.enabled) { mutableStateOf(rule?.enabled ?: false) }
                    var minPrice by remember(rule?.minPrice) { mutableStateOf((rule?.minPrice ?: 200_000).toString()) }
                    var distance by remember(rule?.maxDistance) { mutableStateOf((rule?.maxDistance ?: 20).toString()) }
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                        Text("Terima tawaran otomatis")
                    }
                    TKOutlinedField(minPrice, { minPrice = it }, label = "Harga minimum")
                    TKOutlinedField(distance, { distance = it }, label = "Jarak maksimum (km)")
                    TKButton("Simpan aturan", { viewModel.saveRule(enabled, minPrice, distance) })
                    state.stats?.let { stats ->
                        TKCard {
                            Text("Level ${stats.level} · progres ${stats.levelProgress}%", fontWeight = FontWeight.Bold)
                            Text("Respons ${stats.responseMinutes} menit · selesai ${stats.completionHours} jam")
                            Text("Pembatalan ${stats.cancellationRate}% · total Rp${stats.totalEarnings}")
                        }
                    }
                }
                3 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.route, key = { it.orderId }) { stop ->
                        TKCard {
                            Text("${stop.code} · ${stop.serviceName}")
                            Text("${stop.scheduledDate} · ${stop.distanceKm} km · ${stop.status}")
                            TKOutlinedField(state.checkinLat, { viewModel.field("checkinLat", it) }, label = "Latitude check-in")
                            TKOutlinedField(state.checkinLng, { viewModel.field("checkinLng", it) }, label = "Longitude check-in")
                            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                                TKButton("Check-in datang", { viewModel.checkin(stop.orderId, "DATANG") }, variant = TKButtonVariant.Tonal)
                                TKButton("Check-out", { viewModel.checkin(stop.orderId, "PULANG") }, variant = TKButtonVariant.Outlined)
                            }
                            TKOutlinedField(state.materialName, { viewModel.field("materialName", it) }, label = "Nama material")
                            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                                TKOutlinedField(state.materialQty, { viewModel.field("materialQty", it) }, label = "Jumlah", modifier = Modifier.weight(1f))
                                TKOutlinedField(state.materialCost, { viewModel.field("materialCost", it) }, label = "Biaya", modifier = Modifier.weight(1f))
                            }
                            TKButton("Tambah material", { viewModel.addMaterial(stop.orderId) })
                        }
                    }
                }
                4 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.equipment, key = { it.id }) { item ->
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = item.checked, onCheckedChange = { viewModel.toggleEquipment(item.name, it) })
                            Text(item.name, modifier = Modifier.weight(1f))
                        }
                    }
                }
                5 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.journalNote, { viewModel.field("journalNote", it) }, label = "Catatan hari ini")
                    TKButton("Simpan jurnal", viewModel::addJournal, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.journal, key = { it.id }) { entry ->
                            Text("${entry.entryDate}: ${entry.note}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                6 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.templateTitle, { viewModel.field("templateTitle", it) }, label = "Judul template")
                    TKOutlinedField(state.templateBody, { viewModel.field("templateBody", it) }, label = "Isi pesan")
                    TKButton("Simpan template", viewModel::addTemplate, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.templates, key = { it.id }) { template ->
                            Text("${template.title}: ${template.body}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                7 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKOutlinedField(state.leaveStart, { viewModel.field("leaveStart", it) }, label = "Mulai", modifier = Modifier.weight(1f))
                        TKOutlinedField(state.leaveEnd, { viewModel.field("leaveEnd", it) }, label = "Selesai", modifier = Modifier.weight(1f))
                    }
                    TKButton("Ajukan libur", viewModel::addLeave, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.leaves, key = { it.id }) { leave ->
                            Text("${leave.startDate} sampai ${leave.endDate} · ${leave.status}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.badges, key = { it.id }) { badge ->
                        TKCard {
                            Text(badge.title, fontWeight = FontWeight.Bold)
                            Text(badge.code, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
