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

data class AdminMarketUiState(
    val tab: Int = 0,
    val summary: MarketSummaryDto? = null,
    val ledger: List<LedgerEntryDto> = emptyList(),
    val rules: List<CommissionRuleDto> = emptyList(),
    val account: String = "",
    val debit: String = "",
    val credit: String = "",
    val ruleCode: String = "",
    val rulePercent: String = "15",
    val serviceId: String = "",
    val discount: String = "",
    val notice: String? = null
)

@HiltViewModel
class AdminMarketViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(AdminMarketUiState())
    val state: StateFlow<AdminMarketUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = AdminMarketUiState(
                    tab = current.tab, notice = current.notice,
                    account = current.account, debit = current.debit, credit = current.credit,
                    ruleCode = current.ruleCode, rulePercent = current.rulePercent,
                    serviceId = current.serviceId, discount = current.discount,
                    summary = runCatching { api.marketSummary() }.getOrNull(),
                    ledger = runCatching { api.ledger() }.getOrDefault(emptyList()),
                    rules = runCatching { api.commissionRules() }.getOrDefault(emptyList())
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun field(name: String, value: String) {
        _state.value = when (name) {
            "account" -> _state.value.copy(account = value)
            "debit" -> _state.value.copy(debit = value)
            "credit" -> _state.value.copy(credit = value)
            "ruleCode" -> _state.value.copy(ruleCode = value)
            "rulePercent" -> _state.value.copy(rulePercent = value)
            "serviceId" -> _state.value.copy(serviceId = value)
            "discount" -> _state.value.copy(discount = value)
            else -> _state.value
        }
    }

    fun addLedger() {
        val s = _state.value
        viewModelScope.launch {
            try {
                api.addLedger(AddLedgerRequest(
                    s.account, s.debit.toLongOrNull() ?: 0, s.credit.toLongOrNull() ?: 0, "Manual admin"
                ))
                _state.value = s.copy(notice = "Entri buku besar dibuat", debit = "", credit = "")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun saveRule() {
        val s = _state.value
        if (s.ruleCode.isBlank()) { _state.value = s.copy(notice = "Kode aturan wajib"); return }
        viewModelScope.launch {
            try {
                api.saveCommission(s.ruleCode, SaveCommissionRequest(
                    s.serviceId.toLongOrNull(), s.rulePercent.toIntOrNull() ?: 15, 0, true
                ))
                _state.value = s.copy(notice = "Aturan komisi disimpan")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun createFlash() {
        val s = _state.value
        val service = s.serviceId.toLongOrNull()
        if (service == null) { _state.value = s.copy(notice = "ID layanan wajib"); return }
        viewModelScope.launch {
            try {
                api.createFlashSale(mapOf(
                    "serviceId" to service.toInt(),
                    "discountPercent" to (s.discount.toIntOrNull() ?: 10),
                    "quota" to 20, "hours" to 24
                ))
                _state.value = s.copy(notice = "Promo dibuat")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminMarketScreen(onBack: () -> Unit, viewModel: AdminMarketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Pasar Admin", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Ringkasan", "Komisi", "Buku Besar", "Promo").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    state.summary?.let { s ->
                        TKCard {
                            Text("Promo aktif ${s.flashSales} · beli bersama ${s.openGroupBuys}", fontWeight = FontWeight.Bold)
                            Text("Penawaran menunggu ${s.pendingBids} · wishlist ${s.activeWishlists}")
                            Text("Alert terpicu ${s.triggeredAlerts} · saldo buku besar Rp${s.ledgerBalance}")
                        }
                    }
                }
                1 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.ruleCode, { viewModel.field("ruleCode", it) }, label = "Kode aturan")
                    TKOutlinedField(state.rulePercent, { viewModel.field("rulePercent", it) }, label = "Komisi persen")
                    TKButton("Simpan aturan", viewModel::saveRule, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.rules, key = { it.code }) { rule ->
                            Text("${rule.code} · ${rule.percent}% · ${if (rule.isActive) "aktif" else "nonaktif"}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.account, { viewModel.field("account", it) }, label = "Akun")
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKOutlinedField(state.debit, { viewModel.field("debit", it) }, label = "Debit", modifier = Modifier.weight(1f))
                        TKOutlinedField(state.credit, { viewModel.field("credit", it) }, label = "Kredit", modifier = Modifier.weight(1f))
                    }
                    TKButton("Tambah entri", viewModel::addLedger, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.ledger, key = { it.id }) { entry ->
                            Text("${entry.code} · ${entry.account} · D${entry.debit} K${entry.credit}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.serviceId, { viewModel.field("serviceId", it) }, label = "ID layanan")
                    TKOutlinedField(state.discount, { viewModel.field("discount", it) }, label = "Diskon persen")
                    TKButton("Buat flash sale", viewModel::createFlash, variant = TKButtonVariant.Tonal)
                }
            }
        }
    }
}
