package id.tukangkilat.app.ui.technician

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import id.tukangkilat.app.ui.util.cleanApiError
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.TechnicianRepository
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.app.ui.components.ModeTukangChip
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.components.AvatarCircle
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.theme.tukangKilatColors
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OfferDto
import id.tukangkilat.shared.OrderSummaryDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.TechnicianProfileDto
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await

data class TechHomeUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val profile: TechnicianProfileDto? = null,
    val todayCount: Int = 0,
    val todayEarnings: Long = 0,
    val activeOrder: OrderSummaryDto? = null,
    val upcoming: List<OrderSummaryDto> = emptyList(),
    val offerCount: Int = 0,
    val snackbar: String? = null
)

@HiltViewModel
class TechHomeViewModel @Inject constructor(
    private val technicianRepository: TechnicianRepository,
    private val orderRepository: OrderRepository,
    private val wsManager: WsManager
) : ViewModel() {

    private val _state = MutableStateFlow(TechHomeUiState())
    val state: StateFlow<TechHomeUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event.type == WsEventType.OFFER_NEW || event.type == WsEventType.ORDER_STATUS) {
                    refresh()
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val profileDeferred = async { technicianRepository.profile() }
                val ordersDeferred = async { technicianRepository.myOrders() }
                val earningsDeferred = async { runCatching { technicianRepository.earnings() }.getOrNull() }
                val offersDeferred = async { runCatching { technicianRepository.offers().size }.getOrDefault(0) }
                val profile = profileDeferred.await()
                val orders = ordersDeferred.await()
                val today = java.time.LocalDate.now().toString()
                val todayOrders = orders.filter { it.scheduledDate == today }
                val earnings = earningsDeferred.await()
                val active = orders.firstOrNull {
                    it.status in setOf(
                        OrderStatus.TEKNISI_DITEMUKAN,
                        OrderStatus.DALAM_PERJALANAN,
                        OrderStatus.TIBA,
                        OrderStatus.SEDANG_BEKERJA
                    )
                }
                _state.value = _state.value.copy(
                    isLoading = false,
                    profile = profile,
                    todayCount = todayOrders.count { it.status == OrderStatus.SELESAI },
                    todayEarnings = earnings?.entries
                        ?.filter { it.finishedAt.take(10) == today }
                        ?.sumOf { it.net } ?: 0,
                    activeOrder = active,
                    upcoming = orders.filter {
                        it.status == OrderStatus.TEKNISI_DITEMUKAN && it.id != active?.id
                    }.sortedBy { it.scheduledDate + it.slotLabel }.take(5),
                    offerCount = offersDeferred.await(),
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun setOnline(online: Boolean) {
        viewModelScope.launch {
            try {
                technicianRepository.setOnline(online)
                _state.value = _state.value.copy(
                    profile = _state.value.profile?.copy(isOnline = online),
                    snackbar = if (online) "Kamu online, siap menerima tawaran" else "Kamu offline"
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun sendLocationNow(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val location = client.lastLocation.await()
                if (location != null) {
                    technicianRepository.sendLocation(location.latitude, location.longitude)
                }
            } catch (_: Exception) {}
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun TechHomeScreen(
    onOpenJob: (Long) -> Unit,
    onGoEarnings: () -> Unit,
    viewModel: TechHomeViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else viewModel.refresh()
    }
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.sendLocationNow(context) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }
    // PRD T-03: while online, refresh the technician's location every 15 seconds.
    LaunchedEffect(state.profile?.isOnline) {
        if (state.profile?.isOnline == true) {
            while (isActive) {
                viewModel.sendLocationNow(context)
                delay(15_000)
            }
        }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.profile == null -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> {
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
                    // header with Mode Tukang label
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("TukangKilat", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                        Spacer(Modifier.width(10.dp))
                        ModeTukangChip()
                    }

                    // online/offline bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (state.profile?.isOnline == true) TKColor.OrangeSoft else TKColor.InkSoft)
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(if (state.profile?.isOnline == true) tukangKilatColors().success else TKColor.Mist)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (state.profile?.isOnline == true) "Kamu Online" else "Kamu Offline",
                                color = TKColor.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                if (state.profile?.isOnline == true) "Menerima tawaran pekerjaan" else "Aktifkan untuk menerima tawaran",
                                color = TKColor.Mist,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = state.profile?.isOnline == true,
                            onCheckedChange = { online ->
                                if (online) {
                                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        viewModel.setOnline(true)
                                        viewModel.sendLocationNow(context)
                                    } else {
                                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                        viewModel.setOnline(true)
                                    }
                                } else {
                                    viewModel.setOnline(false)
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = TKColor.White,
                                checkedTrackColor = TKColor.Orange,
                                uncheckedThumbColor = TKColor.Mist,
                                uncheckedTrackColor = TKColor.InkLine
                            )
                        )
                    }
                    Spacer(Modifier.height(TKSpacing.Sm))

                    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
                        item {
                            SectionLabel("Ringkasan hari ini")
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                SummaryCard("Selesai", "${state.todayCount}", Modifier.weight(1f))
                                SummaryCard("Pendapatan hari ini", Format.rupiah(state.todayEarnings), Modifier.weight(1f))
                                SummaryCard("Tawaran", "${state.offerCount}", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(TKSpacing.Sm))
                        }

                        item {
                            SectionLabel("Pekerjaan aktif")
                            Spacer(Modifier.height(4.dp))
                        }
                        if (state.activeOrder == null) {
                            item {
                                Text(
                                    "Belum ada pekerjaan aktif. Tawaran baru akan muncul otomatis saat online.",
                                    color = TKColor.Mist,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = TKSpacing.Xs)
                                )
                            }
                        } else {
                            item {
                                state.activeOrder?.let { order ->
                                    ActiveJobCard(order = order, onClick = { onOpenJob(order.id) })
                                }
                            }
                        }

                        if (state.upcoming.isNotEmpty()) {
                            item {
                                Spacer(Modifier.height(4.dp))
                                SectionLabel("Jadwal berikutnya")
                                Spacer(Modifier.height(4.dp))
                            }
                            items(state.upcoming, key = { it.id }) { order ->
                                UpcomingJobCard(order = order, onClick = { onOpenJob(order.id) })
                            }
                        }
                        item { Spacer(Modifier.height(20.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Text(label, color = TKColor.Mist, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun ActiveJobCard(order: OrderSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TKShape.Card)
            .background(TKColor.OrangeSoft)
            .padding(TKSpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row {
            Text(order.code, color = TKColor.Mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(statusLabel(order.status), color = TKColor.Orange, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.Mist, fontSize = 12.sp)
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.align(Alignment.End).height(40.dp)
        ) {
            Text("Buka pekerjaan", fontWeight = FontWeight.Bold)
        }
    }
}

private fun statusLabel(status: OrderStatus): String = when (status) {
    OrderStatus.TEKNISI_DITEMUKAN -> "Terjadwal"
    OrderStatus.DALAM_PERJALANAN -> "Dalam perjalanan"
    OrderStatus.TIBA -> "Sudah tiba"
    OrderStatus.SEDANG_BEKERJA -> "Sedang bekerja"
    OrderStatus.MENUNGGU_KONFIRMASI -> "Menunggu konfirmasi"
    else -> status.name
}

@Composable
private fun UpcomingJobCard(order: OrderSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TKShape.Media)
            .background(TKColor.InkSoft)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row {
            Text(order.code, color = TKColor.Mist, fontSize = 11.sp)
            Spacer(Modifier.weight(1f))
            Text(order.slotLabel, color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(order.scheduledDate, color = TKColor.Mist, fontSize = 12.sp)
    }
}
