package id.tukangkilat.app.ui.consumer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.map.MapMarker
import id.tukangkilat.app.ui.map.OsmMap
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.PaymentStatus
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class StatusUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val snackbar: String? = null,
    val isCancelling: Boolean = false
)

@HiltViewModel
class OrderStatusViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val wsManager: WsManager
) : ViewModel() {

    private val _state = MutableStateFlow(StatusUiState())
    val state: StateFlow<StatusUiState> = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = StatusUiState(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = StatusUiState(isLoading = false, error = e.cleanApiError())
            }
        }
        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event.type == WsEventType.ORDER_STATUS && event.orderId == orderId) {
                    refreshSilently(orderId)
                }
                if (event.type == WsEventType.TECHNICIAN_LOCATION && event.orderId == orderId) {
                    refreshSilently(orderId)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(6_000)
                refreshSilently(orderId)
            }
        }
    }

    private suspend fun refreshSilently(orderId: Long) {
        try {
            val order = orderRepository.detail(orderId)
            _state.value = _state.value.copy(order = order)
        } catch (_: Exception) {}
    }

    fun cancel(orderId: Long, reason: String) {
        if (reason.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isCancelling = true)
            try {
                orderRepository.cancel(orderId, reason)
                _state.value = _state.value.copy(isCancelling = false, snackbar = "Pesanan dibatalkan")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isCancelling = false, snackbar = e.cleanApiError())
            }
        }
    }
}

private val CANCELABLE = setOf(
    OrderStatus.MENUNGGU_PEMBAYARAN,
    OrderStatus.PEMBAYARAN_GAGAL,
    OrderStatus.MENCARI_TEKNISI,
    OrderStatus.TEKNISI_DITEMUKAN
)

@Composable
fun OrderStatusScreen(
    orderId: Long,
    onBack: () -> Unit,
    onOpenChat: () -> Unit,
    onBecomeSearching: () -> Unit,
    onWorkDone: () -> Unit,
    viewModel: OrderStatusViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showCancelDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(orderId) { viewModel.load(orderId) }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it) }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        else -> {
            val order = state.order!!
            Scaffold(
                containerColor = TKColor.Ink,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(TKColor.Ink)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Kembali", tint = TKColor.White) }
                        Spacer(Modifier.width(4.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Status pesanan", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                            Spacer(Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(order.code, color = TKColor.Mist, fontSize = 12.sp)
                                Spacer(Modifier.width(10.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (order.paymentStatus == PaymentStatus.LUNAS) TKColor.Green else TKColor.OrangeSoft)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (order.paymentStatus == PaymentStatus.LUNAS) "Sudah dibayar" else "Belum dibayar",
                                        color = TKColor.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                    // orange accent line like the design
                    Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(TKColor.Orange))

                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        when (order.status) {
                            OrderStatus.DIBATALKAN -> {
                                Spacer(Modifier.height(24.dp))
                                CancelledCard()
                                return@Column
                            }
                            OrderStatus.MENUNGGU_KONFIRMASI -> {
                                Spacer(Modifier.height(18.dp))
                                Text("Pekerjaan selesai!", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                                Spacer(Modifier.height(10.dp))
                                TKPrimaryButton("Lihat bukti pekerjaan", onClick = onWorkDone)
                                return@Column
                            }
                            OrderStatus.DILAPORKAN -> {
                                Spacer(Modifier.height(18.dp))
                                InfoBanner("Laporan Anda sedang kami proses. Admin akan menjadwalkan kunjungan ulang.")
                                return@Column
                            }
                            OrderStatus.KUNJUNGAN_ULANG -> {
                                Spacer(Modifier.height(18.dp))
                                InfoBanner("Kunjungan ulang dijadwalkan. Menunggu teknisi menerima tawaran.")
                                return@Column
                            }
                            else -> Unit
                        }

                        // map
                        OsmMap(
                            centerLat = order.technicianLat ?: order.addressLat,
                            centerLng = order.technicianLng ?: order.addressLng,
                            markers = buildList {
                                add(MapMarker(order.addressLat, order.addressLng, order.addressLabel, isHome = true))
                                val tLat = order.technicianLat
                                val tLng = order.technicianLng
                                if (tLat != null && tLng != null) {
                                    add(MapMarker(tLat, tLng, order.technician?.fullName ?: "Teknisi", isHome = false))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(150.dp)
                        )
                        Spacer(Modifier.height(12.dp))

                        // ETA
                        order.eta?.let { eta ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, null, tint = TKColor.Orange, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("Tiba sekitar ${eta.arriveAt}", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text(
                                        "Teknisi berjarak %.1f km · ±%d menit".format(eta.distanceKm, eta.minutes),
                                        color = TKColor.Mist,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // technician card
                        order.technician?.let { tech ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(TKColor.Card)
                                    .padding(14.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AvatarCircle(tech.fullName, size = 52)
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(tech.fullName, color = TKColor.Ink, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                        Text(
                                            "★ %.1f · %d pekerjaan".format(tech.ratingAvg, tech.jobsCount),
                                            color = TKColor.CreamText,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            tech.phone?.let { phone ->
                                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                            }
                                        },
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.Mist),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.Ink)
                                    ) {
                                        Icon(Icons.Filled.Phone, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Telepon", fontWeight = FontWeight.SemiBold)
                                    }
                                    OutlinedButton(
                                        onClick = onOpenChat,
                                        modifier = Modifier.weight(1f).height(46.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.Mist),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.Ink)
                                    ) {
                                        Icon(Icons.Filled.Chat, null, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Pesan", fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                        }

                        // timeline
                        TimelineCard(order = order)
                        Spacer(Modifier.height(16.dp))

                        if (order.status in CANCELABLE) {
                            OutlinedButton(
                                onClick = { showCancelDialog = true },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.Red),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.Red)
                            ) {
                                Text("Batalkan pesanan", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        CancelDialog(
            onDismiss = { showCancelDialog = false },
            isSubmitting = state.isCancelling,
            onConfirm = { reason -> viewModel.cancel(orderId, reason); showCancelDialog = false }
        )
    }
}

@Composable
private fun InfoBanner(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TKColor.Cream)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Info, null, tint = TKColor.Orange, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, color = TKColor.CreamText, fontSize = 13.sp)
    }
}

@Composable
private fun CancelledCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TKColor.RedSoft)
            .padding(18.dp)
    ) {
        Text("Pesanan dibatalkan", color = TKColor.RedDeep, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text("Dana yang sudah dibayar akan dikembalikan penuh jika applicable. Hubungi bantuan bila ada kendala.",
            color = TKColor.RedDeep, fontSize = 13.sp)
    }
}

@Composable
private fun TimelineCard(order: OrderDto) {
    val steps = listOf(
        OrderStatus.TEKNISI_DITEMUKAN to "Teknisi ditemukan",
        OrderStatus.DALAM_PERJALANAN to "Dalam perjalanan",
        OrderStatus.TIBA to "Tiba",
        OrderStatus.SEDANG_BEKERJA to "Sedang bekerja"
    )
    val eventTimes = order.events.associate { it.status to it.createdAt }
    val reached = steps.filter { (status, _) -> status in eventTimes || order.status.ordinal >= status.ordinal }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TKColor.Orange)
            .padding(16.dp)
    ) {
        steps.forEachIndexed { index, (status, label) ->
            val eventTime = eventTimes[status]
            val isDone = eventTime != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(if (isDone) TKColor.White else TKColor.White.copy(alpha = 0.4f))
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    label,
                    color = TKColor.White,
                    fontWeight = if (isDone) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )
                if (eventTime != null) {
                    Text(formatTime(eventTime), color = TKColor.White.copy(alpha = 0.9f), fontSize = 12.sp)
                }
            }
            if (index < steps.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .width(2.dp)
                        .height(18.dp)
                        .background(TKColor.White.copy(alpha = 0.5f))
                )
            }
        }
    }
}

private fun formatTime(iso: String): String =
    runCatching {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH.mm"))
    }.getOrDefault("")

@Composable
private fun CancelDialog(
    onDismiss: () -> Unit,
    isSubmitting: Boolean,
    onConfirm: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val quickReasons = listOf("Berubah pikiran", "Teknisi terlalu lama", "Menemukan teknisi lain")

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = reason.isNotBlank() && !isSubmitting,
                onClick = { onConfirm(reason.trim()) }
            ) { Text("Batalkan pesanan", color = TKColor.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Kembali", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Batalkan pesanan?", color = TKColor.White) },
        text = {
            Column {
                Text("Alasan pembatalan:", color = TKColor.Mist, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                quickReasons.forEach { quick ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { reason = quick }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = reason == quick,
                            onClick = { reason = quick },
                            colors = RadioButtonDefaults.colors(selectedColor = TKColor.Orange)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(quick, color = TKColor.White, fontSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextFieldDark(reason, { reason = it }, placeholder = "Tulis alasan lain (opsional)")
            }
        }
    )
}
