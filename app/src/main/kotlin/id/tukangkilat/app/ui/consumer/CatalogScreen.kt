package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CatalogUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val query: String = "",
    val skill: String? = null,
    val categoryId: Long? = null,
    val maxPrice: String = "",
    val minRating: String = "",
    val sort: String = "price",
    val services: List<ServiceSearchDto> = emptyList(),
    val bundles: List<ServiceBundleDto> = emptyList(),
    val estimate: EstimateDto? = null,
    val notice: String? = null
)

@HiltViewModel
class CatalogViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(CatalogUiState())
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val s = _state.value
                val services = api.searchServices(
                    query = s.query.ifBlank { null }, skill = s.skill, categoryId = s.categoryId,
                    maxPrice = s.maxPrice.toLongOrNull(), minRating = s.minRating.toDoubleOrNull(), sort = s.sort
                )
                val bundles = runCatching { api.bundles() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(isLoading = false, services = services, bundles = bundles)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onField(field: String, value: String) {
        _state.value = when (field) {
            "query" -> _state.value.copy(query = value)
            "maxPrice" -> _state.value.copy(maxPrice = value)
            "minRating" -> _state.value.copy(minRating = value)
            else -> _state.value
        }
    }

    fun filter(field: String, value: String?) {
        _state.value = when (field) {
            "skill" -> _state.value.copy(skill = value)
            "sort" -> _state.value.copy(sort = value ?: "price")
            else -> _state.value
        }
        refresh()
    }

    fun estimate(service: ServiceSearchDto) {
        viewModelScope.launch {
            try {
                val address = api.addresses().firstOrNull()
                    ?: throw IllegalStateException("Tambahkan alamat terlebih dahulu")
                val date = java.time.LocalDate.now().plusDays(1).toString()
                val slot = api.slots(date).firstOrNull { it.available }
                    ?: throw IllegalStateException("Tidak ada slot tersedia")
                val result = api.estimate(EstimateRequest(service.id, address.id, date, slot.id))
                _state.value = _state.value.copy(estimate = result)
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onBack: () -> Unit, onOpenService: (Long) -> Unit, viewModel: CatalogViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Katalog Layanan", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TKOutlinedField(state.query, { viewModel.onField("query", it) }, label = "Cari layanan")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = TKSpacing.Xs)) {
                listOf(null, "LISTRIK", "PIPA", "AC").forEach { skill ->
                    ChoiceFilterChip(
                        label = skill ?: "Semua",
                        selected = state.skill == skill,
                        onClick = { viewModel.filter("skill", skill) }
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                TKOutlinedField(state.maxPrice, { viewModel.onField("maxPrice", it) }, label = "Harga maks", modifier = Modifier.weight(1f))
                TKOutlinedField(state.minRating, { viewModel.onField("minRating", it) }, label = "Rating min", modifier = Modifier.weight(1f))
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("price" to "Harga", "rating" to "Rating", "name" to "Nama").forEach { (key, label) ->
                    ChoiceFilterChip(label = label, selected = state.sort == key, onClick = { viewModel.filter("sort", key) })
                }
            }
            TKButton("Terapkan filter", viewModel::refresh)
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm)) {
                if (state.bundles.isNotEmpty()) {
                    item { SectionLabel("Paket bundling") }
                    items(state.bundles, key = { it.id }) { bundle ->
                        TKCard {
                            Text(bundle.name, fontWeight = FontWeight.Bold)
                            Text(bundle.description, style = MaterialTheme.typography.bodySmall)
                            Text("Diskon ${bundle.discountPercent} persen", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                item { SectionLabel("Layanan") }
                items(state.services, key = { it.id }) { service ->
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
                            .clickable { onOpenService(service.id) }.padding(TKSpacing.Sm)
                    ) {
                        Text(service.name, fontWeight = FontWeight.Bold)
                        Text("${service.skill} · ${service.durationMinutes} menit · rating ${service.ratingAvg}", style = MaterialTheme.typography.bodySmall)
                        Text("Rp${service.basePrice + service.visitFee}")
                        TextButton(onClick = { viewModel.estimate(service) }) { Text("Estimasi harga") }
                    }
                }
                state.estimate?.let { estimate ->
                    item {
                        TKCard {
                            Text("Estimasi total Rp${estimate.total}", fontWeight = FontWeight.Bold)
                            estimate.notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
            }
        }
    }
}
