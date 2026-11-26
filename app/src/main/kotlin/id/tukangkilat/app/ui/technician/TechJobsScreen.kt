package id.tukangkilat.app.ui.technician

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TaskAlt
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
import id.tukangkilat.app.data.repository.TechnicianRepository
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.app.ui.components.AvatarCircle
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.components.ModeTukangChip
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OfferDto
import id.tukangkilat.shared.OrderSummaryDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.OffsetDateTime

data class TechJobsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val tab: Int = 0,
    val offers: List<OfferDto> = emptyList(),
    val active: List<OrderSummaryDto> = emptyList(),
    val history: List<OrderSummaryDto> = emptyList(),
    val now: Long = System.currentTimeMillis(),
    val snackbar: String? = null
)

@HiltViewModel
class TechJobsViewModel @Inject constructor(
    private val technicianRepository: TechnicianRepository,
    private val wsManager: WsManager
) : ViewModel() {

    private val _state = MutableStateFlow(TechJobsUiState())
    val state: StateFlow<TechJobsUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event.type == WsEventType.OFFER_NEW || event.type == WsEventType.ORDER_STATUS || event.type == WsEventType.OFFER_EXPIRED) {
                    refresh()
                }
            }
        }
    }

    fun secondsLeft(offer: OfferDto): Long =
        runCatching {
            (OffsetDateTime.parse(offer.expiresAt).toEpochSecond() - OffsetDateTime.now().toEpochSecond()).coerceAtLeast(0)
        }.getOrDefault(0)

    fun refresh() {
        viewModelScope.launch {
            try {
                val offers = technicianRepository.offers()
                val orders = technicianRepository.myOrders()
                _state.value = _state.value.copy(
                    isLoading = false,
                    offers = offers,
                    active = orders.filter {
                        it.status in setOf(
                            OrderStatus.TEKNISI_DITEMUKAN,
                            OrderStatus.DALAM_PERJALANAN,
                            OrderStatus.TIBA,
                            OrderStatus.SEDANG_BEKERJA
                        )
                    },
                    history = orders.filter {
                        it.status in setOf(OrderStatus.SELESAI, OrderStatus.DIBATALKAN)
                    },
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onTab(tab: Int) { _state.value = _state.value.copy(tab = tab) }

    fun acceptOffer(offerId: Long, onAccepted: (Long) -> Unit) {
        viewModelScope.launch {
            try {
                val order = technicianRepository.acceptOffer(offerId)
                _state.value = _state.value.copy(snackbar = "Tawaran diterima")
                refresh()
                onAccepted(order.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun rejectOffer(offerId: Long, reason: String) {
        viewModelScope.launch {
            try {
                technicianRepository.rejectOffer(offerId, reason)
                _state.value = _state.value.copy(snackbar = "Tawaran ditolak")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun TechJobsScreen(
    onOpenJob: (Long) -> Unit,
    viewModel: TechJobsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else viewModel.refresh()
    }
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
            ScreenHeader("Pekerjaan")
            TabRow(
                selectedTabIndex = state.tab,
                containerColor = TKColor.Ink,
                contentColor = TKColor.Orange
            ) {
                listOf("Tawaran (${state.offers.size})", "Terjadwal (${state.active.size})", "Riwayat (${state.history.size})").forEachIndexed { index, title ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.onTab(index) },
                        text = { Text(title, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            when (state.tab) {
                0 -> OffersTab(state, viewModel::acceptOffer, viewModel::rejectOffer, onOpenJob)
                1 -> ActiveJobsTab(state.active, onOpenJob)
                else -> HistoryJobsTab(state.history, onOpenJob)
            }
        }
    }
}

@Composable
private fun OffersTab(
    state: TechJobsUiState,
    onAccept: (Long, (Long) -> Unit) -> Unit,
    onReject: (Long, String) -> Unit,
    onOpenJob: (Long) -> Unit
) {
    var rejectTarget by remember { mutableStateOf<OfferDto?>(null) }

    if (state.offers.isEmpty()) {
        EmptyView(
            title = "Belum ada tawaran",
            subtitle = "Tawaran pekerjaan akan muncul otomatis di sini selama kamu online.",
            icon = { Icon(Icons.Filled.TaskAlt, "Selesai", tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(state.offers, key = { it.id }) { offer ->
            val nowLocal by produceState(initialValue = System.currentTimeMillis()) {
                while (true) {
                    delay(1_000)
                    value = System.currentTimeMillis()
                }
            }
            OfferCard(
                offer = offer,
                secondsLeft = { OfferSeconds(offer, nowLocal) },
                onAccept = { onAccept(offer.id, onOpenJob) },
                onReject = { rejectTarget = offer }
            )
        }
        item { Spacer(Modifier.height(20.dp)) }
    }

    rejectTarget?.let { offer ->
        RejectDialog(
            onDismiss = { rejectTarget = null },
            onConfirm = { reason ->
                onReject(offer.id, reason)
                rejectTarget = null
            }
        )
    }
}

private fun OfferSeconds(offer: OfferDto, now: Long): Long =
    runCatching {
        (OffsetDateTime.parse(offer.expiresAt).toEpochSecond() * 1000 - now) / 1000
    }.getOrDefault(0).coerceAtLeast(0)

@Composable
private fun OfferCard(
    offer: OfferDto,
    secondsLeft: () -> Long,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val seconds = secondsLeft()
    val mm = seconds / 60
    val ss = seconds % 60

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TKShape.Card)
            .background(TKColor.Cream)
            .padding(TKSpacing.Sm)
    ) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(offer.serviceName, color = TKColor.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                if (offer.isRevisit) {
                    Text("Kunjungan ulang", color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Box(
                modifier = Modifier
                    .clip(TKShape.Field)
                    .background(if (seconds <= 15) TKColor.Red else TKColor.OrangeSoft)
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    "%02d:%02d".format(mm, ss),
                    color = if (seconds <= 15) TKColor.White else TKColor.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp
                )
            }
        }
        Spacer(Modifier.height(TKSpacing.Xs))
        Text(offer.description, color = TKColor.CreamText, fontSize = 13.sp, maxLines = 3)
        Spacer(Modifier.height(10.dp))
        OfferRow(Icons.Filled.Place, "%.1f km dari lokasi kamu".format(offer.distanceKm))
        OfferRow(Icons.Filled.Schedule, "${offer.scheduledDate} · ${offer.slotLabel}")
        OfferRow(Icons.Filled.ElectricBolt, "Estimasi pendapatan ${Format.rupiah(offer.estimatedPayout)}")
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f).heightIn(min = TKSpacing.MinTouch),
                shape = TKShape.Media,
                border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.CreamText),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.Ink)
            ) { Text("Tolak", fontWeight = FontWeight.Bold) }
            Button(
                onClick = onAccept,
                modifier = Modifier.weight(1.5f).heightIn(min = TKSpacing.MinTouch),
                shape = TKShape.Media,
                colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange)
            ) { Text("Terima tawaran", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun OfferRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
        Icon(icon, "Detail", tint = TKColor.CreamText, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(TKSpacing.Xs))
        Text(text, color = TKColor.Ink, fontSize = 13.sp)
    }
}

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("Jadwal bentrok") }
    val reasons = listOf("Jadwal bentrok", "Terlalu jauh", "Sedang sibuk", "Bukan keahlian saya")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) { Text("Tolak tawaran", color = TKColor.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Tolak tawaran", color = TKColor.White) },
        text = {
            Column {
                reasons.forEach { r ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reason = r }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == r,
                            onClick = { reason = r },
                            colors = RadioButtonDefaults.colors(selectedColor = TKColor.Orange)
                        )
                        Spacer(Modifier.width(TKSpacing.Xs))
                        Text(r, color = TKColor.White, fontSize = 14.sp)
                    }
                }
            }
        }
    )
}

@Composable
private fun ActiveJobsTab(orders: List<OrderSummaryDto>, onOpenJob: (Long) -> Unit) {
    if (orders.isEmpty()) {
        EmptyView(
            title = "Belum ada pekerjaan terjadwal",
            subtitle = "Terima tawaran di tab Tawaran untuk mulai bekerja.",
            icon = { Icon(Icons.Filled.TaskAlt, "Selesai", tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(orders, key = { it.id }) { order ->
            JobListCard(order = order, onClick = { onOpenJob(order.id) })
        }
    }
}

@Composable
private fun HistoryJobsTab(orders: List<OrderSummaryDto>, onOpenJob: (Long) -> Unit) {
    if (orders.isEmpty()) {
        EmptyView(
            title = "Belum ada riwayat",
            subtitle = "Pekerjaan yang selesai akan tampil di sini.",
            icon = { Icon(Icons.Filled.TaskAlt, "Selesai", tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
        contentPadding = PaddingValues(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(orders, key = { it.id }) { order ->
            JobListCard(order = order, onClick = { onOpenJob(order.id) })
        }
    }
}

@Composable
private fun JobListCard(order: OrderSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .clickable(onClick = onClick)
            .padding(TKSpacing.Sm)
    ) {
        Row {
            Text(order.code, color = TKColor.Mist, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Text(order.status.name.replace('_', ' '), color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.Mist, fontSize = 12.sp)
    }
}
