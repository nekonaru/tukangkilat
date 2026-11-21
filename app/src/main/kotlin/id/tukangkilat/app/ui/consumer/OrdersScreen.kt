package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.util.cleanApiError
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
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
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.OrderSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OrdersUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val orders: List<OrderSummaryDto> = emptyList(),
    val tab: Int = 0
) {
    private val activeStatuses = setOf(
        OrderStatus.MENUNGGU_PEMBAYARAN,
        OrderStatus.PEMBAYARAN_GAGAL,
        OrderStatus.MENCARI_TEKNISI,
        OrderStatus.TEKNISI_DITEMUKAN,
        OrderStatus.DALAM_PERJALANAN,
        OrderStatus.TIBA,
        OrderStatus.SEDANG_BEKERJA,
        OrderStatus.MENUNGGU_KONFIRMASI,
        OrderStatus.KUNJUNGAN_ULANG,
        OrderStatus.DILAPORKAN
    )
    val active: List<OrderSummaryDto> get() = orders.filter { it.status in activeStatuses }
    val history: List<OrderSummaryDto> get() = orders.filter { it.status !in activeStatuses }
}

@HiltViewModel
class OrdersViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OrdersUiState())
    val state: StateFlow<OrdersUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val cached = orderRepository.cachedOrders()
                if (cached.isNotEmpty()) {
                    _state.value = _state.value.copy(orders = cached, isLoading = false)
                }
                val orders = orderRepository.myOrders()
                _state.value = OrdersUiState(isLoading = false, orders = orders, tab = _state.value.tab)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onTab(tab: Int) { _state.value = _state.value.copy(tab = tab) }
}

@Composable
fun OrdersScreen(
    onOpen: (Long, OrderStatus) -> Unit,
    viewModel: OrdersViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }
    val shown = if (state.tab == 0) state.active else state.history

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
    ) {
        ScreenHeader("Pesanan")
        TabRow(
            selectedTabIndex = state.tab,
            containerColor = TKColor.Ink,
            contentColor = TKColor.Orange
        ) {
            listOf("Aktif (${state.active.size})", "Riwayat (${state.history.size})").forEachIndexed { index, title ->
                Tab(
                    selected = state.tab == index,
                    onClick = { viewModel.onTab(index) },
                    text = { Text(title, fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        when {
            state.isLoading -> LoadingView()
            state.error != null && state.orders.isEmpty() -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
            shown.isEmpty() -> EmptyView(
                title = if (state.tab == 0) "Belum ada pesanan aktif" else "Belum ada riwayat",
                subtitle = if (state.tab == 0) "Pesanan yang menunggu pembayaran atau sedang berjalan akan tampil di sini." else "Pesanan yang selesai akan tampil di sini.",
                icon = { Icon(Icons.Filled.ReceiptLong, null, tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(shown, key = { it.id }) { order ->
                    OrderListCard(order = order, onClick = { onOpen(order.id, order.status) })
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun OrderListCard(order: OrderSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row {
            Text(order.code, color = TKColor.Mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(statusLabel(order.status), color = TKColor.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.Mist, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Text(Format.rupiah(order.total), color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
    }
}
