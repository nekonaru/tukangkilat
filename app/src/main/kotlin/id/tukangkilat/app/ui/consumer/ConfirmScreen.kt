package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
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
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.OrderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConfirmUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class ConfirmViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ConfirmUiState())
    val state = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = ConfirmUiState(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = ConfirmUiState(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun confirm(orderId: Long, onDone: () -> Unit) {
        if (_state.value.isSubmitting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                orderRepository.confirm(orderId)
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun ConfirmScreen(
    orderId: Long,
    onDone: () -> Unit,
    onReport: () -> Unit,
    onBack: () -> Unit,
    viewModel: ConfirmViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(orderId) { viewModel.load(orderId) }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        else -> {
            val order = state.order!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TKColor.Ink)
                    .verticalScroll(rememberScrollState())
            ) {
                ScreenHeader("Konfirmasi hasil", onBack = onBack)
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .background(TKColor.Cream),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.AssignmentTurnedIn, null, tint = TKColor.Orange, modifier = Modifier.size(56.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Apakah masalah sudah\nteratasi?",
                        color = TKColor.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Coba gunakan ${order.serviceName.lowercase()} kamu. Pastikan semuanya berfungsi dengan baik sebelum konfirmasi.",
                        color = TKColor.Mist,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(18.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(TKColor.Card)
                            .padding(TKSpacing.Sm)
                    ) {
                        Text("Perbaikan Listrik".takeIf { order.serviceName.isEmpty() } ?: order.serviceName,
                            color = TKColor.Ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(order.description, color = TKColor.CreamText, fontSize = 13.sp)
                        Spacer(Modifier.height(TKSpacing.Xs))
                        Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.CreamText, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(TKSpacing.Sm))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(TKShape.Media)
                            .background(TKColor.GreenSoft)
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Info, null, tint = TKColor.GreenDeep, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Belum beres? Laporkan masalah agar kami bantu atur kunjungan ulang.",
                            color = TKColor.GreenDeep,
                            fontSize = 12.sp
                        )
                    }
                    Spacer(Modifier.height(TKSpacing.Sm))
                    if (state.isSubmitting) {
                        CircularProgressIndicator(color = TKColor.Orange)
                    } else {
                        TKPrimaryButton("Sudah beres", onClick = { viewModel.confirm(orderId, onDone) })
                    }
                    Spacer(Modifier.height(10.dp))
                    TKSecondaryButton("Belum, lapor masalah", onClick = onReport)
                    state.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
