package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.CreditCardOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.OrderStatus
import id.tukangkilat.shared.PaymentMethod
import id.tukangkilat.shared.PaymentStatus
import id.tukangkilat.shared.PayResponse
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class PaymentUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val selectedMethod: PaymentMethod = PaymentMethod.TRANSFER,
    val promoCode: String = "",
    val isSubmitting: Boolean = false,
    val payment: PayResponse? = null,
    val payFailed: Boolean = false,
    val snackbar: String? = null,
    val secondsLeft: Long = 0
)

@HiltViewModel
class PaymentViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val wsManager: WsManager
) : ViewModel() {

    private val _state = MutableStateFlow(PaymentUiState())
    val state: StateFlow<PaymentUiState> = _state.asStateFlow()

    private var pollJob: kotlinx.coroutines.Job? = null

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = _state.value.copy(isLoading = false, order = order)
                when (order.status) {
                    OrderStatus.PEMBAYARAN_GAGAL -> _state.value = _state.value.copy(payFailed = true)
                    else -> Unit
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }

        // realtime: refetch on ORDER_STATUS for this order
        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event.type == WsEventType.ORDER_STATUS && event.orderId == orderId) {
                    refreshSilently(orderId)
                }
            }
        }
    }

    private suspend fun refreshSilently(orderId: Long) {
        try {
            val order = orderRepository.detail(orderId)
            when (order.status) {
                OrderStatus.PEMBAYARAN_GAGAL ->
                    _state.value = _state.value.copy(order = order, payFailed = true, payment = null)
                OrderStatus.MENCARI_TEKNISI,
                OrderStatus.TEKNISI_DITEMUKAN ->
                    _state.value = _state.value.copy(order = order, payFailed = false, payment = null)
                else -> _state.value = _state.value.copy(order = order)
            }
        } catch (_: Exception) {
            // keep last known state
        }
    }

    fun selectMethod(method: PaymentMethod) { _state.value = _state.value.copy(selectedMethod = method) }
    fun onPromo(value: String) { _state.value = _state.value.copy(promoCode = value) }

    fun pay(orderId: Long, onPaid: () -> Unit) {
        val s = _state.value
        val method = s.selectedMethod
        viewModelScope.launch {
            _state.value = s.copy(isSubmitting = true, error = null)
            try {
                val response = orderRepository.pay(orderId, method, s.promoCode.trim().uppercase().ifBlank { null })
                if (method == PaymentMethod.TUNAI) {
                    onPaid()
                    return@launch
                }
                _state.value = _state.value.copy(
                    isSubmitting = false,
                    payment = response,
                    secondsLeft = secondsUntil(response.expiresAt)
                )
                startPoll(orderId, onPaid)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, error = e.cleanApiError())
            }
        }
    }

    private fun secondsUntil(expiresAt: String?): Long {
        if (expiresAt == null) return 0
        return try {
            val instant = Instant.parse(expiresAt)
            (instant.epochSecond - Instant.now().epochSecond).coerceAtLeast(0)
        } catch (e: Exception) { 0 }
    }

    private fun startPoll(orderId: Long, onPaid: () -> Unit) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            val deadline = System.currentTimeMillis() + 30 * 60_000L
            while (System.currentTimeMillis() < deadline) {
                delay(4_000)
                _state.value = _state.value.copy(
                    secondsLeft = (_state.value.secondsLeft - 4).coerceAtLeast(0)
                )
                val order = try { orderRepository.detail(orderId) } catch (e: Exception) { null } ?: continue
                when (order.status) {
                    OrderStatus.MENCARI_TEKNISI, OrderStatus.TEKNISI_DITEMUKAN -> {
                        onPaid()
                        return@launch
                    }
                    OrderStatus.PEMBAYARAN_GAGAL -> {
                        _state.value = _state.value.copy(payFailed = true, payment = null, order = order)
                        return@launch
                    }
                    else -> Unit
                }
            }
            // expired locally
            _state.value = _state.value.copy(
                payFailed = true,
                payment = null,
                snackbar = "Waktu pembayaran habis"
            )
        }
    }

    /** Try again with the same method (order is PEMBAYARAN_GAGAL, goes back to MENUNGGU_PEMBAYARAN). */
    fun tryAgain(orderId: Long, onPaid: () -> Unit) {
        _state.value = _state.value.copy(payFailed = false, snackbar = null)
        pay(orderId, onPaid)
    }

    /** Change payment method: back to the selection state. */
    fun changeMethod() {
        _state.value = _state.value.copy(payFailed = false, payment = null, snackbar = null)
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun PaymentScreen(
    orderId: Long,
    onPaid: () -> Unit,
    onBack: () -> Unit,
    viewModel: PaymentViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(orderId) { viewModel.load(orderId) }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        state.payFailed -> PaymentFailedContent(
            state = state,
            onTryAgain = { viewModel.tryAgain(orderId, onPaid) },
            onChangeMethod = { viewModel.changeMethod() },
            onBack = onBack
        )
        state.payment != null -> PaymentPendingContent(
            state = state,
            onPaid = onPaid,
            onBack = onBack
        )
        else -> PaymentSelectContent(
            state = state,
            onSelectMethod = viewModel::selectMethod,
            onPromo = viewModel::onPromo,
            onPay = { viewModel.pay(orderId, onPaid) },
            onBack = onBack,
            onDismissSnackbar = viewModel::clearSnackbar
        )
    }
}

@Composable
private fun PaymentSelectContent(
    state: PaymentUiState,
    onSelectMethod: (PaymentMethod) -> Unit,
    onPromo: (String) -> Unit,
    onPay: () -> Unit,
    onBack: () -> Unit,
    onDismissSnackbar: () -> Unit
) {
    val order = state.order!!
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.error, state.snackbar) {
        val msg = state.snackbar ?: state.error
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            onDismissSnackbar()
        }
    }

    Scaffold(
        containerColor = TKColor.Ink,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ScreenHeader("Pembayaran", onBack = onBack)
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                // summary card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TKShape.Card)
                        .background(TKColor.Orange)
                        .padding(TKSpacing.Sm)
                ) {
                    Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(order.description, color = TKColor.White.copy(alpha = 0.9f), fontSize = 13.sp, maxLines = 2)
                    Spacer(Modifier.height(6.dp))
                    Text("${order.scheduledDate} · ${order.slotLabel}", color = TKColor.White.copy(alpha = 0.8f), fontSize = 12.sp)
                }
                Spacer(Modifier.height(TKSpacing.Sm))

                FeeRow("Biaya jasa", Format.rupiah(order.serviceFee))
                FeeRow("Biaya kunjungan", Format.rupiah(order.visitFee))
                if (order.discount > 0) FeeRow("Diskon", "-${Format.rupiah(order.discount)}")
                HorizontalDivider(color = TKColor.InkLine, modifier = Modifier.padding(vertical = 10.dp))
                Row {
                    Text("Total pembayaran", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text(Format.rupiah(order.total), color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                }
                Spacer(Modifier.height(18.dp))

                Text("Metode pembayaran", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(10.dp))
                MethodCard(
                    selected = state.selectedMethod == PaymentMethod.TRANSFER,
                    title = "Transfer bank",
                    subtitle = "BCA · Nomor pembayaran otomatis",
                    icon = { Icon(Icons.Filled.AccountBalance, "Transfer bank", tint = if (state.selectedMethod == PaymentMethod.TRANSFER) TKColor.Orange else TKColor.Ink) },
                    onClick = { onSelectMethod(PaymentMethod.TRANSFER) }
                )
                Spacer(Modifier.height(10.dp))
                MethodCard(
                    selected = state.selectedMethod == PaymentMethod.DOMPET_DIGITAL,
                    title = "Dompet digital",
                    subtitle = "GoPay, OVO, atau DANA",
                    icon = { Icon(Icons.Filled.Wallet, "Dompet digital", tint = if (state.selectedMethod == PaymentMethod.DOMPET_DIGITAL) TKColor.Orange else TKColor.Ink) },
                    onClick = { onSelectMethod(PaymentMethod.DOMPET_DIGITAL) }
                )
                Spacer(Modifier.height(10.dp))
                MethodCard(
                    selected = state.selectedMethod == PaymentMethod.TUNAI,
                    title = "Tunai",
                    subtitle = "Bayar setelah pekerjaan selesai",
                    icon = { Icon(Icons.Filled.LocalAtm, "Pembayaran tunai", tint = if (state.selectedMethod == PaymentMethod.TUNAI) TKColor.Orange else TKColor.Ink) },
                    onClick = { onSelectMethod(PaymentMethod.TUNAI) }
                )
                Spacer(Modifier.height(14.dp))
                TextFieldDark(
                    value = state.promoCode,
                    onValueChange = onPromo,
                    placeholder = "Kode promo (opsional)"
                )
                Text(
                    "Kode promo divalidasi saat menekan Bayar sekarang.",
                    color = TKColor.Mist,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
                Spacer(Modifier.height(TKSpacing.Sm))
                if (state.isSubmitting) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = TKColor.Orange)
                    }
                } else {
                    TKPrimaryButton("Bayar sekarang", onClick = onPay)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun FeeRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = TKColor.Mist, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TKColor.White, fontSize = 13.sp)
    }
}

@Composable
private fun MethodCard(
    selected: Boolean,
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) TKColor.Cream else TKColor.Card)
            .then(
                if (selected) Modifier.border(2.dp, TKColor.Orange, RoundedCornerShape(14.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(TKShape.Media)
                .background(TKColor.Cream),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TKColor.Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = TKColor.CreamText, fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(2.dp, if (selected) TKColor.Orange else TKColor.Mist, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(TKColor.Orange)
                )
            }
        }
    }
}

@Composable
private fun PaymentPendingContent(state: PaymentUiState, onPaid: () -> Unit, onBack: () -> Unit) {
    val payment = state.payment!!
    val mm = state.secondsLeft / 60
    val ss = state.secondsLeft % 60

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Pembayaran", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(18.dp))
            Icon(
                Icons.Filled.AccountBalance,
                "Transfer bank",
                tint = TKColor.Orange,
                modifier = Modifier.size(56.dp)
            )
            Spacer(Modifier.height(TKSpacing.Sm))
            Text("Selesaikan pembayaran", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Transfer sebelum waktu habis. Admin akan mengonfirmasi pembayaran kamu.",
                color = TKColor.Mist,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(TKShape.Card)
                    .background(TKColor.Card)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Nomor pembayaran", color = TKColor.CreamText, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    payment.referenceNo.orEmpty(),
                    color = TKColor.Ink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 26.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = TKColor.Mist.copy(alpha = 0.4f))
                Spacer(Modifier.height(14.dp))
                FeeRow("Total yang ditransfer", Format.rupiah(payment.amount))
                Spacer(Modifier.height(6.dp))
                Text("Kode pesanan dibuat otomatis saat transfer", color = TKColor.CreamText, fontSize = 11.sp)
            }
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CalendarMonth, "Tanggal", tint = TKColor.Orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(TKSpacing.Xs))
                Text(
                    "Sisa waktu: %02d:%02d".format(mm, ss),
                    color = TKColor.Orange,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Status pembayaran: Menunggu konfirmasi",
                modifier = Modifier.semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = "Status pembayaran: Menunggu konfirmasi"
                },
                color = TKColor.CreamText,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Setelah transfer, status akan berubah otomatis. Halaman ini memantau pembayaran secara langsung.",
                color = TKColor.Mist,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PaymentFailedContent(
    state: PaymentUiState,
    onTryAgain: () -> Unit,
    onChangeMethod: () -> Unit,
    onBack: () -> Unit
) {
    val order = state.order
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Pembayaran", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(TKColor.RedSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CreditCardOff, "Pembayaran gagal", tint = TKColor.Red, modifier = Modifier.size(52.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Pembayaran belum berhasil", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
            Spacer(Modifier.height(TKSpacing.Xs))
            Text(
                "Pembayaran kamu belum terkonfirmasi. Coba lagi atau gunakan metode pembayaran lain.",
                color = TKColor.Mist,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            if (order != null) {
                Spacer(Modifier.height(18.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TKShape.Card)
                        .background(TKColor.Card)
                        .padding(TKSpacing.Sm)
                ) {
                    InfoRow("Nomor pesanan", order.code)
                    InfoRow("Metode", when (order.paymentMethod) {
                        PaymentMethod.TRANSFER -> "Transfer BCA"
                        PaymentMethod.DOMPET_DIGITAL -> "Dompet digital"
                        PaymentMethod.TUNAI -> "Tunai"
                        null -> "-"
                    })
                    HorizontalDivider(color = TKColor.Mist.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 10.dp))
                    InfoRow("Total pembayaran", Format.rupiah(order.total), bold = true)
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Jika saldo sudah terpotong, dana akan kembali dalam 1-3 hari kerja.",
                    color = TKColor.Mist,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(22.dp))
            TKPrimaryButton("Coba lagi", onClick = onTryAgain)
            Spacer(Modifier.height(12.dp))
            TKSecondaryButton("Ganti metode pembayaran", onClick = onChangeMethod)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, bold: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = TKColor.CreamText, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(
            value,
            color = TKColor.Ink,
            fontSize = 13.sp,
            fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium
        )
    }
}
