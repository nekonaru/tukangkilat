package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
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
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WalletUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val wallet: WalletDto? = null,
    val loyalty: LoyaltyDto = LoyaltyDto(0, 0),
    val subscriptions: List<SubscriptionDto> = emptyList(),
    val notice: String? = null,
    val tab: Int = 0
)

@HiltViewModel
class WalletViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(WalletUiState())
    val state: StateFlow<WalletUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val wallet = api.wallet()
                val loyalty = api.loyalty()
                val subs = api.subscriptions()
                _state.value = WalletUiState(isLoading = false, wallet = wallet, loyalty = loyalty, subscriptions = subs)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun topUp(amount: Long) {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(wallet = api.topUp(TopUpRequest(amount)), notice = "Saldo ditambahkan")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.cleanApiError())
            }
        }
    }

    fun redeem() {
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(loyalty = api.redeemPoints(mapOf("points" to 100)), notice = "100 poin ditukar")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }

    fun addSubscription(serviceId: Long, addressId: Long, interval: Int, date: String) {
        viewModelScope.launch {
            try {
                api.addSubscription(CreateSubscriptionRequest(serviceId, addressId, interval, date))
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.cleanApiError())
            }
        }
    }

    fun cancelSubscription(id: Long) {
        viewModelScope.launch {
            try { api.cancelSubscription(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(error = e.cleanApiError()) }
        }
    }

    fun subscriptionAction(id: Long, action: String) {
        viewModelScope.launch {
            try {
                when (action) {
                    "pause" -> api.pauseSubscription(id)
                    "resume" -> api.resumeSubscription(id)
                    "skip" -> api.skipSubscription(id)
                }
                _state.value = _state.value.copy(notice = "Langganan diperbarui")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@Composable
fun WalletScreen(onBack: () -> Unit, promoCode: String? = null, viewModel: WalletViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Dompet & Langganan", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            promoCode?.takeIf { it.isNotBlank() }?.let { code ->
                TKCard {
                    Text("Kode promo dari tautan: $code", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Text("Gunakan kode ini saat memesan layanan.", style = MaterialTheme.typography.bodySmall)
                }
            }
            TabRow(selectedTabIndex = state.tab) {
                Tab(selected = state.tab == 0, onClick = { viewModel.tab(0) }, text = { Text("Dompet") })
                Tab(selected = state.tab == 1, onClick = { viewModel.tab(1) }, text = { Text("Langganan") })
            }
            when {
                state.isLoading -> SkeletonCard()
                state.error != null && state.wallet == null -> ErrorView(message = state.error ?: "Ada kendala sebentar", onRetry = viewModel::refresh)
                state.tab == 0 -> Column(
                    Modifier.fillMaxSize().padding(TKSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        shape = TKShape.Card,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.padding(TKSpacing.Md)) {
                            Text("Saldo", style = MaterialTheme.typography.bodySmall)
                            Text(
                                Format.rupiah(state.wallet?.balance ?: 0),
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text("Top up simulasi", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                        TKButton("Rp50.000", { viewModel.topUp(50_000) }, modifier = Modifier.weight(1f), variant = TKButtonVariant.Tonal)
                        TKButton("Rp100.000", { viewModel.topUp(100_000) }, modifier = Modifier.weight(1f), variant = TKButtonVariant.Tonal)
                    }
                    Text(
                        "Poin kamu: ${state.loyalty.points} (total ${state.loyalty.lifetime})",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TKButton("Tukar 100 poin", viewModel::redeem, variant = TKButtonVariant.Outlined)
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)
                ) {
                    items(state.subscriptions, key = { it.id }) { sub ->
                        Row(
                            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card).padding(TKSpacing.Sm),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(sub.serviceName, style = MaterialTheme.typography.titleSmall)
                                Text("Tiap ${sub.intervalDays} hari · ${sub.nextDate} · ${sub.status}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (sub.status == "AKTIF") {
                                TextButton(onClick = { viewModel.subscriptionAction(sub.id, "pause") }) { Text("Jeda") }
                                TextButton(onClick = { viewModel.subscriptionAction(sub.id, "skip") }) { Text("Lewati") }
                            } else if (sub.status == "DIJEDA") {
                                TextButton(onClick = { viewModel.subscriptionAction(sub.id, "resume") }) { Text("Lanjut") }
                            }
                            TextButton(onClick = { viewModel.cancelSubscription(sub.id) }) { Text("Hentikan") }
                        }
                    }
                    item {
                        Text("Langganan baru memakai layanan dan alamat utama. Gunakan form pemesanan untuk memilih layanan.", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
