package id.tukangkilat.app.ui.admin

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.AdminRepository
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.AdminSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: AdminSummaryDto? = null
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val summary = adminRepository.summary()
                _state.value = AdminUiState(isLoading = false, summary = summary)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }
}

@Composable
fun AdminSummaryScreen(
    onVerify: () -> Unit,
    onReports: () -> Unit,
    onOrders: () -> Unit,
    onServices: () -> Unit,
    onPromo: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.summary == null -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> {
            val summary = state.summary!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TKColor.Ink)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Panel Admin", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("Mode Admin", color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Filled.Logout, "Keluar", tint = TKColor.Red)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Pesanan hari ini", "${summary.ordersToday}", Modifier.weight(1f))
                            StatCard("Tukang online", "${summary.techniciansOnline}", Modifier.weight(1f))
                            StatCard("Laporan terbuka", "${summary.openReports}", Modifier.weight(1f))
                        }
                    }
                    item { SectionLabel("Kelola") }
                    item { AdminMenuCard(Icons.Filled.VerifiedUser, "Verifikasi tukang", "Setujui atau tolak pendaftaran tukang", onVerify) }
                    item { AdminMenuCard(Icons.Filled.ReportProblem, "Laporan masalah", "Tinjau dan jadwalkan kunjungan ulang", onReports) }
                    item { AdminMenuCard(Icons.Filled.ReceiptLong, "Semua pesanan", "Filter status dan simulasi pembayaran", onOrders) }
                    item { AdminMenuCard(Icons.Filled.Build, "Layanan", "Ubah harga, biaya kunjungan, dan status", onServices) }
                    item { AdminMenuCard(Icons.Filled.LocalActivity, "Kode promo", "Buat, aktifkan, dan nonaktifkan kode promo", onPromo) }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Text(label, color = TKColor.Mist, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
    }
}

@Composable
private fun AdminMenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(TKColor.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = TKColor.Orange)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = TKColor.Mist, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = TKColor.InkLine)
    }
}
