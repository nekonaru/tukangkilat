package id.tukangkilat.app.ui.technician

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import id.tukangkilat.app.ui.components.ModeTukangChip
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.EarningEntryDto
import id.tukangkilat.shared.EarningsDto
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.WithdrawalDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechEarningsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val earnings: EarningsDto? = null,
    val withdrawals: List<WithdrawalDto> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class TechEarningsViewModel @Inject constructor(
    private val technicianRepository: TechnicianRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TechEarningsUiState())
    val state: StateFlow<TechEarningsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() = viewModelScope.launch {
        try {
            val earningsDeferred = async { technicianRepository.earnings() }
            val withdrawalsDeferred = async { runCatching { technicianRepository.withdrawals() }.getOrDefault(emptyList()) }
            _state.value = TechEarningsUiState(
                isLoading = false,
                earnings = earningsDeferred.await(),
                withdrawals = withdrawalsDeferred.await()
            )
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
        }
    }

    fun requestPayout() {
        val amount = _state.value.earnings?.balance ?: 0
        if (amount <= 0) {
            _state.value = _state.value.copy(snackbar = "Saldo tidak cukup untuk ditarik")
            return
        }
        viewModelScope.launch {
            try {
                technicianRepository.withdraw(amount)
                refresh().join()
                _state.value = _state.value.copy(snackbar = "Penarikan ${Format.rupiah(amount)} berhasil (simulasi)")
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun TechEarningsScreen(
    viewModel: TechEarningsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.earnings == null -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> {
            val earnings = state.earnings!!
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
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pendapatan", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        ModeTukangChip()
                    }
                    Spacer(Modifier.height(14.dp))

                    // balance card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(TKColor.Orange)
                            .padding(18.dp)
                    ) {
                        Text("Saldo tersedia", color = TKColor.White.copy(alpha = 0.9f), fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            Format.rupiah(earnings.balance),
                            color = TKColor.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 32.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.requestPayout() },
                            colors = ButtonDefaults.buttonColors(containerColor = TKColor.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Tarik saldo", color = TKColor.Ink, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    SectionLabel("Riwayat pendapatan (komisi platform ${(earnings.commissionRate * 100).toInt()}%)", modifier = Modifier.padding(horizontal = 18.dp))
                    Spacer(Modifier.height(4.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(earnings.entries, key = { it.orderId }) { entry ->
                            EarningRow(entry)
                        }
                        if (state.withdrawals.isNotEmpty()) {
                            item { SectionLabel("Riwayat penarikan", modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)) }
                            items(state.withdrawals, key = { it.id }) { w ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TKColor.InkSoft)
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Penarikan saldo", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(w.createdAt.take(16), color = TKColor.Mist, fontSize = 11.sp)
                                    }
                                    Text("-${Format.rupiah(w.amount)}", color = TKColor.Orange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                        if (earnings.entries.isEmpty()) {
                            item {
                                Text(
                                    "Belum ada pendapatan. Selesaikan pekerjaan untuk mulai menghasilkan.",
                                    color = TKColor.Mist,
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EarningRow(entry: EarningEntryDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.serviceName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(entry.orderCode, color = TKColor.Mist, fontSize = 11.sp)
            }
            Text(
                "+${Format.rupiah(entry.net)}",
                color = TKColor.Green,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = TKColor.InkLine.copy(alpha = 0.4f))
        Spacer(Modifier.height(8.dp))
        FeeLine("Biaya jasa", Format.rupiah(entry.serviceFee))
        FeeLine("Komisi platform", "-${Format.rupiah(entry.commission)}")
        FeeLine("Biaya kunjungan", "+${Format.rupiah(entry.visitFee)}")
    }
}

@Composable
private fun FeeLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = TKColor.Mist, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = TKColor.Mist, fontSize = 12.sp)
    }
}
