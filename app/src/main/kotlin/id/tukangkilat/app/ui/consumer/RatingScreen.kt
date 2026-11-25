package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.FavoriteRepository
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.PaymentMethod
import id.tukangkilat.shared.PaymentStatus
import id.tukangkilat.shared.RatingRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RatingUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val stars: Int = 5,
    val tags: Set<String> = emptySet(),
    val comment: String = "",
    val isSubmitting: Boolean = false,
    val submitted: Boolean = false,
    val isFavorite: Boolean = false
)

@HiltViewModel
class RatingViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RatingUiState())
    val state: StateFlow<RatingUiState> = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                val favorite = order.technician?.let { favoriteRepository.isFavorite(it.id) } ?: false
                _state.value = _state.value.copy(isLoading = false, order = order, isFavorite = favorite)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun toggleFavorite() {
        val technician = _state.value.order?.technician ?: return
        viewModelScope.launch {
            favoriteRepository.toggle(technician)
            _state.value = _state.value.copy(isFavorite = favoriteRepository.isFavorite(technician.id))
        }
    }

    fun onStars(stars: Int) { _state.value = _state.value.copy(stars = stars) }
    fun toggleTag(tag: String) {
        val s = _state.value
        val newTags = if (tag in s.tags) s.tags - tag else s.tags + tag
        _state.value = s.copy(tags = newTags)
    }
    fun onComment(value: String) { _state.value = _state.value.copy(comment = value) }

    fun submit(orderId: Long) {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(isSubmitting = true, error = null)
            try {
                orderRepository.rate(orderId, RatingRequest(s.stars, s.tags.toList(), s.comment.trim()))
                _state.value = _state.value.copy(isSubmitting = false, submitted = true)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, error = e.cleanApiError())
            }
        }
    }
}

private val QUICK_TAGS = listOf("Tepat waktu", "Ramah", "Rapi")

@Composable
fun RatingScreen(
    orderId: Long,
    onDone: () -> Unit,
    viewModel: RatingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(orderId) { viewModel.load(orderId) }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        state.submitted -> ReceiptContent(order = state.order!!, onBack = onDone, isFavorite = state.isFavorite, onToggleFavorite = { viewModel.toggleFavorite() })
        else -> RatingForm(
            state = state,
            onStars = viewModel::onStars,
            onToggleTag = viewModel::toggleTag,
            onComment = viewModel::onComment,
            onSubmit = { viewModel.submit(orderId) },
            onBack = onDone
        )
    }
}

@Composable
private fun RatingForm(
    state: RatingUiState,
    onStars: (Int) -> Unit,
    onToggleTag: (String) -> Unit,
    onComment: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit
) {
    val order = state.order!!
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Penilaian", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(8.dp))
            order.technician?.let { tech ->
                AvatarCircle(tech.fullName, size = 64)
                Spacer(Modifier.height(10.dp))
                Text(tech.fullName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "★ %.1f · %d pekerjaan".format(tech.ratingAvg, tech.jobsCount),
                    color = TKColor.Mist,
                    fontSize = 13.sp
                )
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (i in 1..5) {
                    IconButton(onClick = { onStars(i) }) {
                        Icon(
                            if (i <= state.stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                            null,
                            tint = TKColor.Orange,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
            Text(
                when (state.stars) {
                    5 -> "5 dari 5 · Sangat puas"
                    4 -> "4 dari 5 · Puas"
                    3 -> "3 dari 5 · Cukup"
                    2 -> "2 dari 5 · Kurang"
                    else -> "1 dari 5 · Sangat kecewa"
                },
                color = TKColor.Orange,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QUICK_TAGS.forEach { tag ->
                    val selected = tag in state.tags
                    Text(
                        tag,
                        color = if (selected) TKColor.Ink else TKColor.Orange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) TKColor.Orange else TKColor.Card)
                            .border(1.dp, TKColor.Orange, RoundedCornerShape(20.dp))
                            .clickable { onToggleTag(tag) }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            TextFieldDark(
                value = state.comment,
                onValueChange = onComment,
                placeholder = "Tulis ulasan (opsional)"
            )
            Spacer(Modifier.height(16.dp))
            if (state.isSubmitting) {
                CircularProgressIndicator(color = TKColor.Orange)
            } else {
                TKPrimaryButton("Kirim penilaian", onClick = onSubmit)
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Struk ringkas (layar 08 bagian bawah). Juga dipakai untuk pesanan SELESAI. */
@Composable
internal fun ReceiptContent(order: OrderDto, onBack: () -> Unit, isFavorite: Boolean = false, onToggleFavorite: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Pesanan selesai", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TKColor.GreenSoft)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = TKColor.Green, modifier = Modifier.size(36.dp))
                Spacer(Modifier.height(8.dp))
                Text("Pesanan selesai", color = TKColor.GreenDeep, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Struk ${order.code} · ${order.scheduledDate}",
                    color = TKColor.CreamText,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "${order.addressLabel} · ${order.serviceName}",
                    color = TKColor.Ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(order.addressFull, color = TKColor.CreamText, fontSize = 12.sp, maxLines = 2)
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = TKColor.Mist.copy(alpha = 0.4f))
                Spacer(Modifier.height(12.dp))
                ReceiptRow("Biaya jasa", Format.rupiah(order.serviceFee))
                ReceiptRow("Biaya kunjungan", Format.rupiah(order.visitFee))
                if (order.discount > 0) ReceiptRow("Diskon", "-${Format.rupiah(order.discount)}")
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = TKColor.Mist.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))
                Row {
                    Text(
                        "Lunas · ${methodLabel(order)}",
                        color = TKColor.GreenDeep,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        Format.rupiah(order.total),
                        color = TKColor.Ink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            ShareReceiptButton(order)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onToggleFavorite,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.InkLine),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.White)
            ) {
                Icon(
                    if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    null,
                    tint = TKColor.Orange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(if (isFavorite) "Teknisi favorit" else "Simpan teknisi favorit", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            TKPrimaryButton("Kembali ke Beranda", onClick = onBack)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShareReceiptButton(order: OrderDto) {
    val context = androidx.compose.ui.platform.LocalContext.current
    OutlinedButton(
        onClick = { id.tukangkilat.app.ui.util.ReceiptPdf.createAndShare(context, order) },
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.InkLine),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.White)
    ) {
        Icon(Icons.Filled.Send, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text("Bagikan struk PDF", fontWeight = FontWeight.SemiBold)
    }
}

private fun methodLabel(order: OrderDto): String = when {
    order.paymentStatus == PaymentStatus.BELUM_DIBAYAR && order.paymentMethod == PaymentMethod.TUNAI -> "Tunai (dibayar di tempat)"
    order.paymentMethod == PaymentMethod.TRANSFER -> "Transfer BCA"
    order.paymentMethod == PaymentMethod.DOMPET_DIGITAL -> "Dompet digital"
    order.paymentMethod == PaymentMethod.TUNAI -> "Tunai"
    else -> "-"
}

@Composable
private fun ReceiptRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = TKColor.CreamText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TKColor.Ink, fontSize = 13.sp)
    }
}
