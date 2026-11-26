package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.OrderEventDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.PhotoKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class DoneUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null
)

@HiltViewModel
class DoneViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(DoneUiState())
    val state = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = DoneUiState(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = DoneUiState(isLoading = false, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun DoneScreen(
    orderId: Long,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    viewModel: DoneViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var zoomPhoto by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(orderId) { viewModel.load(orderId) }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        else -> {
            val order = state.order!!
            val context = LocalContext.current
            val proofPhotos = order.photos.filter { it.kind == PhotoKind.BUKTI }
            val selesaiAt = order.events.lastOrNull { it.status == OrderStatus.MENUNGGU_KONFIRMASI }?.createdAt

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TKColor.Ink)
                    .verticalScroll(rememberScrollState())
            ) {
                ScreenHeader("Pekerjaan selesai", onBack = onBack)
                Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                    // success banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TKShape.Card)
                            .background(TKColor.GreenSoft)
                            .padding(TKSpacing.Sm)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, "Pilih", tint = TKColor.Green, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Pekerjaan selesai!", color = TKColor.GreenDeep, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                Text(order.serviceName, color = TKColor.GreenDeep, fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    // technician
                    order.technician?.let { tech ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(tech.fullName, size = 46)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(tech.fullName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(
                                    "★ %.1f · %d pekerjaan".format(tech.ratingAvg, tech.jobsCount),
                                    color = TKColor.Mist,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(TKSpacing.Sm))

                    SectionLabel("Bukti hasil kerja")
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(proofPhotos) { photo ->
                            val url = BuildConfig.BASE_URL + photo.url
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                                contentDescription = "Bukti pekerjaan",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(TKColor.InkSoft)
                                    .clickable { zoomPhoto = url }
                            )
                        }
                        if (proofPhotos.isEmpty()) {
                            item {
                                Text("Tidak ada foto bukti", color = TKColor.Mist, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    selesaiAt?.let { iso ->
                        Text(
                            "${prettyDate(iso)} · Selesai pukul ${prettyTime(iso)}",
                            color = TKColor.Mist,
                            fontSize = 12.sp
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    // technician note
                    order.finishNote?.takeIf { it.isNotBlank() }?.let { note ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(TKColor.Card)
                                .padding(TKSpacing.Sm)
                        ) {
                            Text("Catatan ${order.technician?.fullName?.split(" ")?.firstOrNull() ?: "teknisi"}",
                                color = TKColor.Ink,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp)
                            Spacer(Modifier.height(TKSpacing.Xs))
                            Text(note, color = TKColor.Ink, fontSize = 14.sp)
                        }
                        Spacer(Modifier.height(TKSpacing.Sm))
                    }

                    TKPrimaryButton("Konfirmasi hasil pekerjaan", onClick = onConfirm)
                    Spacer(Modifier.height(24.dp))
                }
            }

            zoomPhoto?.let { url ->
                Dialog(
                    onDismissRequest = { zoomPhoto = null },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.92f))
                            .clickable { zoomPhoto = null },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context).data(url).crossfade(true).build(),
                            contentDescription = "Bukti pekerjaan",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight()
                        )
                        IconButton(
                            onClick = { zoomPhoto = null },
                            modifier = Modifier.align(Alignment.TopEnd).padding(TKSpacing.Sm)
                        ) {
                            Icon(Icons.Filled.Close, "Tutup", tint = androidx.compose.ui.graphics.Color.White)
                        }
                    }
                }
            }
        }
    }
}

internal fun prettyDate(iso: String): String =
    runCatching {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.forLanguageTag("id")))
    }.getOrDefault(iso)

internal fun prettyTime(iso: String): String =
    runCatching {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH.mm"))
    }.getOrDefault(iso)
