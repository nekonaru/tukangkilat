package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

data class CommerceUiState(
    val isLoading: Boolean = true,
    val tab: Int = 0,
    val wallet: WalletDto? = null,
    val settings: WalletSettingsDto? = null,
    val tiers: List<TierDto> = emptyList(),
    val vouchers: List<VoucherDto> = emptyList(),
    val referrals: List<ReferralDto> = emptyList(),
    val installments: List<InstallmentDto> = emptyList(),
    val refunds: List<RefundRequestDto> = emptyList(),
    val referralCode: String = "",
    val notice: String? = null
)

@HiltViewModel
class CommerceViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(CommerceUiState())
    val state: StateFlow<CommerceUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _state.value = CommerceUiState(
                    isLoading = false,
                    wallet = runCatching { api.wallet() }.getOrNull(),
                    settings = runCatching { api.walletSettings() }.getOrNull(),
                    tiers = runCatching { api.tiers() }.getOrDefault(emptyList()),
                    vouchers = runCatching { api.vouchers() }.getOrDefault(emptyList()),
                    referrals = runCatching { api.referrals() }.getOrDefault(emptyList()),
                    installments = runCatching { api.installments() }.getOrDefault(emptyList()),
                    refunds = runCatching { api.refunds() }.getOrDefault(emptyList())
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }

    fun onReferralCode(value: String) { _state.value = _state.value.copy(referralCode = value) }

    fun saveSettings(req: SaveWalletSettingsRequest) {
        viewModelScope.launch {
            try {
                api.saveWalletSettings(req)
                _state.value = _state.value.copy(notice = "Pengaturan dompet disimpan")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun redeemReferral() {
        val code = _state.value.referralCode.trim()
        if (code.isBlank()) { _state.value = _state.value.copy(notice = "Masukkan kode referral"); return }
        viewModelScope.launch {
            try {
                api.redeemReferral(RedeemReferralRequest(code))
                _state.value = _state.value.copy(notice = "Kode referral berhasil", referralCode = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun payInstallment(id: Long) {
        viewModelScope.launch {
            try {
                api.payInstallment(id)
                _state.value = _state.value.copy(notice = "Cicilan dibayar")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommerceScreen(onBack: () -> Unit, viewModel: CommerceViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Dompet & Manfaat", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Dompet", "Manfaat", "Voucher", "Cicilan", "Refund").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    state.wallet?.let { TKCard { Text("Saldo Rp${it.balance}", fontWeight = FontWeight.Bold) } }
                    state.settings?.let { s ->
                        var auto by remember(s.autoTopupEnabled) { mutableStateOf(s.autoTopupEnabled) }
                        var amount by remember(s.autoTopupAmount) { mutableStateOf(s.autoTopupAmount.toString()) }
                        var threshold by remember(s.autoTopupThreshold) { mutableStateOf(s.autoTopupThreshold.toString()) }
                        var limit by remember(s.dailyLimit) { mutableStateOf(s.dailyLimit.toString()) }
                        var cashback by remember(s.cashbackPercent) { mutableStateOf(s.cashbackPercent.toString()) }
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(checked = auto, onCheckedChange = { auto = it })
                            Text("Top up otomatis")
                        }
                        TKOutlinedField(amount, { amount = it }, label = "Nominal top up otomatis")
                        TKOutlinedField(threshold, { threshold = it }, label = "Ambang saldo")
                        TKOutlinedField(limit, { limit = it }, label = "Batas harian")
                        TKOutlinedField(cashback, { cashback = it }, label = "Cashback persen (0-20)")
                        TKButton("Simpan pengaturan", {
                            viewModel.saveSettings(SaveWalletSettingsRequest(
                                auto, amount.toLongOrNull() ?: 100_000, threshold.toLongOrNull() ?: 50_000,
                                limit.toLongOrNull() ?: 500_000, (cashback.toIntOrNull() ?: 0).coerceIn(0, 20)
                            ))
                        })
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    item { SectionLabel("Tier keanggotaan") }
                    items(state.tiers, key = { it.code }) { tier ->
                        TKCard {
                            Text("${tier.code} ${if (tier.current) "(aktif)" else ""}", fontWeight = FontWeight.Bold)
                            Text("Minimal ${tier.minLifetimePoints} poin · diskon ${tier.discountPercent} persen")
                            Text(tier.benefits, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    item { SectionLabel("Referral") }
                    item {
                        state.referrals.firstOrNull()?.let { Text("Kode kamu: ${it.code}", fontWeight = FontWeight.Bold) }
                        TKOutlinedField(state.referralCode, viewModel::onReferralCode, label = "Kode teman")
                        TKButton("Pakai kode referral", viewModel::redeemReferral, variant = TKButtonVariant.Tonal)
                    }
                }
                2 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.vouchers, key = { it.id }) { voucher ->
                        TKCard {
                            Text("${voucher.code} · Rp${voucher.amount}")
                            Text("Status ${voucher.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                3 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.installments, key = { it.id }) { plan ->
                        TKCard {
                            Text("Pesanan ${plan.orderId} · ${plan.months} bulan")
                            Text("Rp${plan.monthly}/bulan · sudah ${plan.paidMonths} · ${plan.status}")
                            if (plan.status == "AKTIF") {
                                TKButton("Bayar cicilan", { viewModel.payInstallment(plan.id) }, variant = TKButtonVariant.Tonal)
                            }
                        }
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.refunds, key = { it.id }) { refund ->
                        TKCard {
                            Text("Pesanan ${refund.orderId} · Rp${refund.amount}")
                            Text("${refund.status} · ${refund.reason}")
                            if (refund.note.isNotBlank()) Text(refund.note, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
