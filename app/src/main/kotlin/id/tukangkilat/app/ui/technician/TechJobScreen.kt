package id.tukangkilat.app.ui.technician

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.repository.TechnicianRepository
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.map.MapMarker
import id.tukangkilat.app.ui.map.OsmMap
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.PhotoKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechJobUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val isActing: Boolean = false,
    val snackbar: String? = null
)

@HiltViewModel
class TechJobViewModel @Inject constructor(
    private val technicianRepository: TechnicianRepository,
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TechJobUiState())
    val state: StateFlow<TechJobUiState> = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = TechJobUiState(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = TechJobUiState(isLoading = false, error = e.cleanApiError())
            }
        }
    }


    /** PRD T-07: report the technician's position while travelling to the consumer. */
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
    fun action(orderId: Long, action: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isActing = true, error = null)
            try {
                val updated = when (action) {
                    "depart" -> technicianRepository.depart(orderId)
                    "arrive" -> technicianRepository.arrive(orderId)
                    "start" -> technicianRepository.start(orderId)
                    else -> throw IllegalStateException("Aksi tidak dikenal")
                }
                _state.value = _state.value.copy(isActing = false, order = updated)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isActing = false, snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun TechJobScreen(
    orderId: Long,
    onFinish: () -> Unit,
    onOpenChat: () -> Unit,
    onBack: () -> Unit,
    viewModel: TechJobViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(orderId) { viewModel.load(orderId) }
    // PRD T-07: live tracking uses a 5 second update cadence during DALAM_PERJALANAN.
    val inTransit = state.order?.status == OrderStatus.DALAM_PERJALANAN
    LaunchedEffect(inTransit) {
        if (inTransit) {
            while (isActive) {
                viewModel.sendLocationNow(context)
                delay(5_000)
            }
        }
    }
    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        else -> {
            val order = state.order!!
            Scaffold(
                containerColor = TKColor.Ink,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    ActionBar(
                        status = order.status,
                        isActing = state.isActing,
                        onAction = { action ->
                            when (action) {
                                "finish" -> onFinish()
                                else -> viewModel.action(orderId, action)
                            }
                        }
                    )
                }
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
                        Column {
                            Text("Detail pekerjaan", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
                            Text(order.code, color = TKColor.Mist, fontSize = 11.sp)
                        }
                    }

                    Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                        // consumer info
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(order.addressLabel.ifBlank { "Konsumen" }, size = 48)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Konsumen", color = TKColor.Mist, fontSize = 11.sp)
                                Text(order.addressLabel, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            // chat & phone
                            IconButton(onClick = onOpenChat) {
                                Icon(Icons.Filled.Chat, "Pesan", tint = TKColor.Orange)
                            }
                        }
                        Spacer(Modifier.height(14.dp))

                        // address card
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(TKColor.InkSoft)
                                .padding(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Icon(Icons.Filled.Place, "Lokasi", tint = TKColor.Orange, modifier = Modifier.padding(top = 2.dp))
                                Spacer(Modifier.width(TKSpacing.Xs))
                                Text(order.addressFull, color = TKColor.White, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.Mist, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(12.dp))

                        // map with route button
                        OsmMap(
                            centerLat = order.addressLat,
                            centerLng = order.addressLng,
                            markers = listOf(MapMarker(order.addressLat, order.addressLng, order.addressLabel, isHome = true)),
                            modifier = Modifier.fillMaxWidth().height(150.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = {
                                val uri = Uri.parse("geo:${order.addressLat},${order.addressLng}?q=${order.addressLat},${order.addressLng}(${Uri.encode(order.addressFull)})")
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = TKShape.Media,
                            border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.InkLine),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.White)
                        ) {
                            Icon(Icons.Filled.Navigation, "Arah", modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(TKSpacing.Xs))
                            Text("Buka navigasi", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(14.dp))

                        // problem description
                        SectionLabel("Masalah")
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(TKColor.Card)
                                .padding(14.dp)
                        ) {
                            Text(order.description, color = TKColor.Ink, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(12.dp))

                        // problem photos
                        val problemPhotos = order.photos.filter { it.kind == PhotoKind.MASALAH }
                        if (problemPhotos.isNotEmpty()) {
                            SectionLabel("Foto masalah")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(problemPhotos) { photo ->
                                    AsyncImage(
                                        model = BuildConfig.BASE_URL + photo.url,
                                        contentDescription = "Foto masalah",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(TKShape.Media)
                                            .background(TKColor.InkSoft)
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    Spacer(Modifier.height(90.dp))
                }
            }
        }
    }
}

@Composable
private fun ActionBar(
    status: OrderStatus,
    isActing: Boolean,
    onAction: (String) -> Unit
) {
    val spec = when (status) {
        OrderStatus.TEKNISI_DITEMUKAN -> Triple("Berangkat", "depart", Icons.Filled.DirectionsRun)
        OrderStatus.DALAM_PERJALANAN -> Triple("Saya sudah tiba", "arrive", Icons.Filled.WhereToVote)
        OrderStatus.TIBA -> Triple("Mulai bekerja", "start", Icons.Filled.Build)
        OrderStatus.SEDANG_BEKERJA -> Triple("Selesaikan pekerjaan", "finish", Icons.Filled.TaskAlt)
        else -> null
    }
    Surface(
        color = TKColor.Ink,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().background(TKColor.Ink).padding(TKSpacing.Sm)) {
            if (spec == null) {
                Text(
                    "Status: ${status.name.replace('_', ' ')}",
                    color = TKColor.Mist,
                    modifier = Modifier.padding(vertical = TKSpacing.Xs)
                )
            } else if (isActing) {
                Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TKColor.Orange)
                }
            } else {
                Button(
                    onClick = { onAction(spec.second) },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = TKShape.Card,
                    colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange)
                ) {
                    Icon(spec.third, null)
                    Spacer(Modifier.width(10.dp))
                    Text(spec.first, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
            }
        }
    }
}
