package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.shared.SlotDto
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.TKPrimaryButton
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.components.rememberReduceMotion
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchingUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val isRescheduling: Boolean = false,
    val snackbar: String? = null,
    val slots: List<SlotDto> = emptyList(),
    val slotsLoading: Boolean = false
)

@HiltViewModel
class SearchingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val wsManager: WsManager
) : ViewModel() {

    fun loadSlots(date: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(slotsLoading = true, slots = emptyList())
            try {
                val slots = orderRepository.slots(date)
                _state.value = _state.value.copy(slotsLoading = false, slots = slots)
            } catch (e: Exception) {
                _state.value = _state.value.copy(slotsLoading = false)
            }
        }
    }

    private val _state = MutableStateFlow(SearchingUiState())
    val state: StateFlow<SearchingUiState> = _state.asStateFlow()

    fun load(orderId: Long, onFound: () -> Unit) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = _state.value.copy(isLoading = false, order = order)
                if (order.status == OrderStatus.TEKNISI_DITEMUKAN) {
                    onFound()
                    return@launch
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }

        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event.type == WsEventType.ORDER_STATUS && event.orderId == orderId) {
                    try {
                        val order = orderRepository.detail(orderId)
                        if (order.status == OrderStatus.TEKNISI_DITEMUKAN) {
                            onFound()
                        } else {
                            _state.value = _state.value.copy(order = order)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        // polling fallback, 5 seconds
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                val order = try { orderRepository.detail(orderId) } catch (e: Exception) { continue }
                if (order.status == OrderStatus.TEKNISI_DITEMUKAN) {
                    onFound()
                    return@launch
                } else {
                    _state.value = _state.value.copy(order = order)
                }
            }
        }
    }

    fun reschedule(orderId: Long, date: String, slotId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRescheduling = true, error = null)
            try {
                val order = orderRepository.reschedule(orderId, date, slotId)
                _state.value = _state.value.copy(isRescheduling = false, order = order, snackbar = "Jadwal berhasil diubah")
            } catch (e: Exception) {
                _state.value = _state.value.copy(isRescheduling = false, error = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun SearchingScreen(
    orderId: Long,
    onFound: () -> Unit,
    onBack: () -> Unit,
    viewModel: SearchingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showReschedule by remember { mutableStateOf(false) }

    LaunchedEffect(orderId) { viewModel.load(orderId, onFound) }

    val order = state.order
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSnackbar()
        }
    }

    Box(Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Pencarian teknisi", onBack = onBack)

        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))
            PulsingBolt()
            Spacer(Modifier.height(18.dp))
            Text("Mencari teknisi lain", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Kami sedang mencari teknisi pengganti di sekitar lokasi kamu.",
                color = TKColor.Mist,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))

            if (order != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TKShape.Card)
                        .background(TKColor.Card)
                        .padding(TKSpacing.Sm)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ElectricBolt, "Listrik", tint = TKColor.Ink)
                        Spacer(Modifier.width(TKSpacing.Xs))
                        Column {
                            Text(order.serviceName, color = TKColor.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(order.description, color = TKColor.CreamText, fontSize = 12.sp, maxLines = 2)
                        }
                    }
                    Spacer(Modifier.height(TKSpacing.Xs))
                    HorizontalDivider(color = TKColor.Mist.copy(alpha = 0.4f))
                    Spacer(Modifier.height(TKSpacing.Xs))
                    Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(TKSpacing.Sm))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(TKColor.Cream)
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, "Jadwal", tint = TKColor.Orange, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Biasanya kurang dari 3 menit", color = TKColor.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "Jadwal dan total ${order?.total?.let { Format.rupiah(it) } ?: ""} tidak berubah.",
                            color = TKColor.CreamText,
                            fontSize = 12.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, "Informasi", tint = TKColor.Mist, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(TKSpacing.Xs))
                Text(
                    "Kami akan memberi kabar begitu teknisi ditemukan.",
                    color = TKColor.Mist,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(18.dp))
            TKPrimaryButton("Jadwal ulang", onClick = { showReschedule = true })
            Spacer(Modifier.height(10.dp))
            state.error?.let {
                Text(it, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showReschedule && order != null) {
            RescheduleDialog(
                date = java.time.LocalDate.now().plusDays(1).toString(),
                slots = state.slots,
                slotsLoading = state.slotsLoading,
                onLoadSlots = viewModel::loadSlots,
                onDismiss = { showReschedule = false },
                onConfirm = { date, slotId ->
                    showReschedule = false
                    viewModel.reschedule(orderId, date, slotId)
                }
            )
        }
    }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun PulsingBolt() {
    val reduceMotion = rememberReduceMotion()
    val transition = rememberInfiniteTransition(label = "pulse")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (reduceMotion) 0f else 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val trackColor = TKColor.InkLine
    val progressColor = TKColor.Orange
    Box(modifier = Modifier.size(130.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(130.dp)) {
            val stroke = 10f
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = progressColor,
                startAngle = rotation,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(TKColor.InkSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.ElectricBolt, "Listrik", tint = TKColor.Orange, modifier = Modifier.size(34.dp))
        }
    }
}

@Composable
private fun RescheduleDialog(
    date: String,
    slots: List<SlotDto>,
    slotsLoading: Boolean,
    onLoadSlots: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (String, Long) -> Unit
) {
    var currentDate by remember { mutableStateOf(date) }
    var selectedSlot by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(currentDate) {
        selectedSlot = null
        onLoadSlots(currentDate)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = selectedSlot != null,
                onClick = { selectedSlot?.let { onConfirm(currentDate, it) } }
            ) { Text("Simpan", color = TKColor.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Jadwal ulang", color = TKColor.White) },
        text = {
            Column {
                Text("Tanggal baru (YYYY-MM-DD):", color = TKColor.Mist, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = currentDate,
                    onValueChange = { currentDate = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TKColor.White,
                        unfocusedTextColor = TKColor.White,
                        focusedBorderColor = TKColor.Orange,
                        unfocusedBorderColor = TKColor.InkLine
                    )
                )
                Spacer(Modifier.height(12.dp))
                Text("Pilih slot:", color = TKColor.Mist, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                if (slotsLoading) {
                    CircularProgressIndicator(color = TKColor.Orange, modifier = Modifier.size(18.dp))
                } else {
                    slots.filter { it.available }.forEach { slot ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedSlot = slot.id }
                                .padding(vertical = TKSpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedSlot == slot.id,
                                onClick = { selectedSlot = slot.id },
                                colors = RadioButtonDefaults.colors(selectedColor = TKColor.Orange)
                            )
                            Spacer(Modifier.width(TKSpacing.Xs))
                            Text(slot.label, color = TKColor.White)
                        }
                    }
                }
            }
        }
    )
}
