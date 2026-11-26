package id.tukangkilat.app.ui.admin

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.repository.AdminRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.IssueReportDto
import id.tukangkilat.shared.IssueStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminReportsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val reports: List<IssueReportDto> = emptyList(),
    val tab: Int = 0,
    val snackbar: String? = null
)

@HiltViewModel
class AdminReportsViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminReportsUiState())
    val state: StateFlow<AdminReportsUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val status = if (_state.value.tab == 0) "TERBUKA" else null
                val reports = adminRepository.reports(status)
                _state.value = _state.value.copy(isLoading = false, reports = reports, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onTab(tab: Int) {
        _state.value = _state.value.copy(tab = tab)
        refresh()
    }

    fun revisit(reportId: Long) {
        viewModelScope.launch {
            try {
                adminRepository.revisitReport(reportId)
                _state.value = _state.value.copy(snackbar = "Kunjungan ulang dijadwalkan")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun close(reportId: Long) {
        viewModelScope.launch {
            try {
                adminRepository.closeReport(reportId)
                _state.value = _state.value.copy(snackbar = "Laporan ditutup")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun AdminReportsScreen(
    onBack: () -> Unit,
    viewModel: AdminReportsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

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
            ScreenHeader("Laporan masalah", onBack = onBack)
            TabRow(
                selectedTabIndex = state.tab,
                containerColor = TKColor.Ink,
                contentColor = TKColor.Orange
            ) {
                listOf("Terbuka", "Semua").forEachIndexed { index, title ->
                    Tab(
                        selected = state.tab == index,
                        onClick = { viewModel.onTab(index) },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }
            when {
                state.isLoading -> LoadingView()
                state.error != null && state.reports.isEmpty() -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
                state.reports.isEmpty() -> EmptyView(
                    title = "Tidak ada laporan",
                    subtitle = "Semua masalah sudah ditangani.",
                    icon = { Icon(Icons.Filled.CheckCircle, "Pilih", tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.reports, key = { it.id }) { report ->
                        ReportCard(
                            report = report,
                            onRevisit = { viewModel.revisit(report.id) },
                            onClose = { viewModel.close(report.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportCard(
    report: IssueReportDto,
    onRevisit: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TKShape.Card)
            .background(TKColor.InkSoft)
            .padding(TKSpacing.Sm)
    ) {
        Row {
            Text(report.orderCode, color = TKColor.Mist, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (report.status == IssueStatus.TERBUKA) TKColor.OrangeSoft else TKColor.GreenSoft)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    report.status.name.replace('_', ' '),
                    color = if (report.status == IssueStatus.TERBUKA) TKColor.Orange else TKColor.GreenDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(report.reason.label, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        Spacer(Modifier.height(4.dp))
        Text(report.description, color = TKColor.Mist, fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        if (report.photoUrls.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                report.photoUrls.take(3).forEach { url ->
                    AsyncImage(
                        model = BuildConfig.BASE_URL + url,
                        contentDescription = "Foto laporan",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TKColor.Ink)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        if (report.status == IssueStatus.TERBUKA) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f).height(44.dp),
                    shape = TKShape.Media,
                    border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.InkLine),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.White)
                ) { Text("Tutup", fontWeight = FontWeight.Bold) }
                Button(
                    onClick = onRevisit,
                    modifier = Modifier.weight(1.5f).height(44.dp),
                    shape = TKShape.Media,
                    colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange)
                ) { Text("Jadwalkan kunjungan ulang", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
