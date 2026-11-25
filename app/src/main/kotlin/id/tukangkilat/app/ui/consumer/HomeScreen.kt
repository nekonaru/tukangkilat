package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.util.cleanApiError
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.R
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.tkPressScale
import id.tukangkilat.app.ui.components.tkScreenEnter
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.TKWordmark
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.OrderSummaryDto
import id.tukangkilat.shared.ServiceDto
import id.tukangkilat.shared.AddressDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val services: List<ServiceDto> = emptyList(),
    val query: String = "",
    val primaryAddress: AddressDto? = null,
    val activeOrder: OrderSummaryDto? = null
) {
    val filteredServices: List<ServiceDto>
        get() = if (query.isBlank()) services
        else services.filter { it.name.contains(query, ignoreCase = true) }
}

private val ACTIVE_ORDER_STATUSES = setOf(
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

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onQuery(value: String) { _state.value = _state.value.copy(query = value) }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // cache first for instant render
                val cached = orderRepository.cachedOrders()
                val cachedActive = cached.firstOrNull { it.status in ACTIVE_ORDER_STATUSES }
                if (cachedActive != null) {
                    _state.value = _state.value.copy(activeOrder = cachedActive)
                }
                val cachedServices = orderRepository.cachedServices()
                val cachedAddresses = orderRepository.cachedAddresses()
                if (cachedServices.isNotEmpty() || cachedAddresses.isNotEmpty()) {
                    _state.value = _state.value.copy(
                        services = cachedServices,
                        primaryAddress = cachedAddresses.firstOrNull { it.isPrimary } ?: cachedAddresses.firstOrNull()
                    )
                }
                val servicesDeferred = async { orderRepository.services() }
                val addressesDeferred = async { orderRepository.addresses() }
                val ordersDeferred = async { orderRepository.myOrders() }
                val services = servicesDeferred.await()
                val addresses = addressesDeferred.await()
                val orders = ordersDeferred.await()
                _state.value = _state.value.copy(
                    isLoading = false,
                    services = services,
                    primaryAddress = addresses.firstOrNull { it.isPrimary } ?: addresses.firstOrNull(),
                    activeOrder = orders.firstOrNull { it.status in ACTIVE_ORDER_STATUSES }
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }
}

private fun serviceIcon(icon: String) = when (icon) {
    "water_drop", "pipa" -> Icons.Filled.WaterDrop
    "ac_unit", "ac" -> Icons.Filled.AcUnit
    else -> Icons.Filled.ElectricBolt
}

internal fun statusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.MENUNGGU_PEMBAYARAN -> "Menunggu pembayaran"
    OrderStatus.PEMBAYARAN_GAGAL -> "Pembayaran gagal"
    OrderStatus.MENCARI_TEKNISI -> "Mencari teknisi"
    OrderStatus.TEKNISI_DITEMUKAN -> "Teknisi ditemukan"
    OrderStatus.DALAM_PERJALANAN -> "Dalam perjalanan"
    OrderStatus.TIBA -> "Teknisi tiba"
    OrderStatus.SEDANG_BEKERJA -> "Sedang bekerja"
    OrderStatus.MENUNGGU_KONFIRMASI -> "Menunggu konfirmasi"
    OrderStatus.DILAPORKAN -> "Dilaporkan"
    OrderStatus.KUNJUNGAN_ULANG -> "Kunjungan ulang"
    OrderStatus.SELESAI -> "Selesai"
    OrderStatus.DIBATALKAN -> "Dibatalkan"
}

@Composable
fun HomeScreen(
    onOrder: (Long) -> Unit,
    onActiveOrder: (Long, OrderStatus) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    when {
        state.isLoading && state.services.isEmpty() && state.error == null -> LoadingView()
        state.error != null && state.services.isEmpty() ->
            ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TKColor.Ink)
                    .padding(horizontal = 18.dp)
                    .tkScreenEnter()
            ) {
                item {
                    Row(
                        modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_brand),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        TKWordmark(fontSize = 20.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Place, null, tint = TKColor.Orange, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            state.primaryAddress?.let { addr ->
                                listOfNotNull(addr.label.takeIf { it.isNotBlank() }, addr.fullAddress.takeIf { it.isNotBlank() })
                                    .joinToString(" · ")
                            } ?: "Atur alamat di tab Akun",
                            color = TKColor.White,
                            fontSize = 13.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // search
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(TKColor.Orange)
                            .clickable { }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, null, tint = TKColor.White)
                        Spacer(Modifier.width(10.dp))
                        androidx.compose.foundation.text.BasicTextField(
                            value = state.query,
                            onValueChange = viewModel::onQuery,
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = TKColor.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            decorationBox = { inner ->
                                if (state.query.isEmpty()) {
                                    Text("Butuh bantuan apa hari ini?", color = TKColor.White.copy(alpha = 0.85f), fontSize = 15.sp)
                                }
                                inner()
                            },
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }

                items(state.filteredServices, key = { it.id }) { service ->
                    ServiceCard(service = service, onClick = { onOrder(service.id) })
                    Spacer(Modifier.height(12.dp))
                }

                if (state.filteredServices.isEmpty()) {
                    item {
                        EmptyView(
                            title = "Layanan tidak ditemukan",
                            subtitle = "Coba kata kunci lain, misalnya \"listrik\", \"pipa\", atau \"AC\"."
                        )
                    }
                }

                state.activeOrder?.let { order ->
                    item {
                        SectionLabel("Pesanan aktif")
                        ActiveOrderCard(order = order, onClick = { onActiveOrder(order.id, order.status) })
                        Spacer(Modifier.height(18.dp))
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun ServiceCard(service: ServiceDto, onClick: () -> Unit) {
    val pressSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, TKColor.InkLine, RoundedCornerShape(16.dp))
            .background(TKColor.InkSoft)
            .clickable(onClick = onClick, interactionSource = pressSource)
            .tkPressScale(pressSource)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TKColor.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(serviceIcon(service.icon), null, tint = TKColor.Orange, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(service.name, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(2.dp))
            Text("Mulai ${Format.rupiah(service.basePrice)}", color = TKColor.Mist, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ActiveOrderCard(order: OrderSummaryDto, onClick: () -> Unit) {
    val pressSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TKColor.Cream)
            .clickable(onClick = onClick, interactionSource = remember { MutableInteractionSource() })
            .padding(16.dp)
    ) {
        Row {
            Text(order.code, color = TKColor.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
            Text(statusLabel(order.status), color = TKColor.Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(order.serviceName, color = TKColor.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        Spacer(Modifier.height(4.dp))
        Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.CreamText, fontSize = 13.sp)
    }
}
