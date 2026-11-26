package id.tukangkilat.app.ui.admin

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.AdminRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderSummaryDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.PaymentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminOrdersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val orders: List<OrderSummaryDto> = emptyList(),
    val filter: String? = null,
    val snackbar: String? = null
)

@HiltViewModel
class AdminOrdersViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminOrdersUiState())
    val state: StateFlow<AdminOrdersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val orders = adminRepository.orders(_state.value.filter)
                _state.value = _state.value.copy(isLoading = false, orders = orders, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun setFilter(filter: String?) {
        _state.value = _state.value.copy(filter = filter, isLoading = true)
        refresh()
    }

    fun simulateSuccess(orderId: Long) {
        viewModelScope.launch {
            try {
                adminRepository.simulatePaymentSuccess(orderId)
                _state.value = _state.value.copy(snackbar = "Pembayaran ditandai berhasil")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun simulateFail(orderId: Long) {
        viewModelScope.launch {
            try {
                adminRepository.simulatePaymentFail(orderId)
                _state.value = _state.value.copy(snackbar = "Pembayaran ditandai gagal")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

private val FILTERS: List<Pair<String?, String>> = listOf(
    null to "Semua",
    "MENUNGGU_PEMBAYARAN" to "Menunggu bayar",
    "PEMBAYARAN_GAGAL" to "Gagal bayar",
    "MENCARI_TEKNISI" to "Mencari teknisi",
    "TEKNISI_DITEMUKAN" to "Ada teknisi",
    "DALAM_PERJALANAN" to "Dalam perjalanan",
    "TIBA" to "Tiba",
    "SEDANG_BEKERJA" to "Bekerja",
    "MENUNGGU_KONFIRMASI" to "Menunggu konfirmasi",
    "SELESAI" to "Selesai",
    "DIBATALKAN" to "Batal"
)

@Composable
fun AdminOrdersScreen(
    onBack: () -> Unit,
    viewModel: AdminOrdersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    Scaffold(
        containerColor = TKColor.Ink,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TKColor.Ink)
        ) {
            ScreenHeader("Semua pesanan", onBack = onBack)
            // filter chips
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier.padding(horizontal = 14.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(FILTERS) { (value, label) ->
                    val selected = state.filter == value
                    Text(
                        label,
                        color = if (selected) TKColor.White else TKColor.Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(TKShape.Card)
                            .background(if (selected) TKColor.Orange else TKColor.InkSoft)
                            .clickable { viewModel.setFilter(value) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
            when {
                state.isLoading -> LoadingView()
                state.error != null && state.orders.isEmpty() -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
                state.orders.isEmpty() -> EmptyView(
                    title = "Tidak ada pesanan",
                    subtitle = "Tidak ada pesanan untuk filter ini.",
                    icon = { Icon(Icons.Filled.ReceiptLong, "Struk", tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                    contentPadding = PaddingValues(vertical = 10.dp, horizontal = 0.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.orders, key = { it.id }) { order ->
                        AdminOrderRow(
                            order = order,
                            onSimulateSuccess = { viewModel.simulateSuccess(order.id) },
                            onSimulateFail = { viewModel.simulateFail(order.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminOrderRow(
    order: OrderSummaryDto,
    onSimulateSuccess: () -> Unit,
    onSimulateFail: () -> Unit
) {
    val isWaitingPayment = order.status == OrderStatus.MENUNGGU_PEMBAYARAN &&
        order.paymentStatus == PaymentStatus.MENUNGGU

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Row {
            Text(order.code, color = TKColor.Mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(statusLabel(order.status), color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("${order.scheduledDate} · ${order.slotLabel} · ${Format.rupiah(order.total)}", color = TKColor.Mist, fontSize = 12.sp)
        if (isWaitingPayment) {
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onSimulateFail,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.Red),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.Red)
                ) { Text("Simulasikan gagal", fontSize = 12.sp) }
                Button(
                    onClick = onSimulateSuccess,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange)
                ) { Text("Simulasikan berhasil", fontSize = 12.sp) }
            }
        }
    }
}

private fun statusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.MENUNGGU_PEMBAYARAN -> "Menunggu bayar"
    OrderStatus.PEMBAYARAN_GAGAL -> "Gagal bayar"
    OrderStatus.MENCARI_TEKNISI -> "Mencari teknisi"
    OrderStatus.TEKNISI_DITEMUKAN -> "Ada teknisi"
    OrderStatus.DALAM_PERJALANAN -> "Dalam perjalanan"
    OrderStatus.TIBA -> "Tiba"
    OrderStatus.SEDANG_BEKERJA -> "Sedang bekerja"
    OrderStatus.MENUNGGU_KONFIRMASI -> "Menunggu konfirmasi"
    OrderStatus.SELESAI -> "Selesai"
    OrderStatus.DILAPORKAN -> "Dilaporkan"
    OrderStatus.KUNJUNGAN_ULANG -> "Kunjungan ulang"
    OrderStatus.DIBATALKAN -> "Batal"
}
