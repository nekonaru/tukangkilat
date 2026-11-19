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
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.ServiceDto
import id.tukangkilat.shared.ServiceUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminServicesUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val services: List<ServiceDto> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class AdminServicesViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminServicesUiState())
    val state: StateFlow<AdminServicesUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val services = adminRepository.services()
                _state.value = _state.value.copy(isLoading = false, services = services, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun update(serviceId: Long, req: ServiceUpdateRequest) {
        viewModelScope.launch {
            try {
                adminRepository.updateService(serviceId, req)
                _state.value = _state.value.copy(snackbar = "Layanan tersimpan")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun toggleActive(service: ServiceDto) {
        update(service.id, ServiceUpdateRequest(isActive = !service.isActive))
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun AdminServicesScreen(
    onBack: () -> Unit,
    viewModel: AdminServicesViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editTarget by remember { mutableStateOf<ServiceDto?>(null) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.services.isEmpty() -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> Scaffold(
            containerColor = TKColor.Ink,
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(TKColor.Ink)
            ) {
                ScreenHeader("Layanan", onBack = onBack)
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.services, key = { it.id }) { service ->
                        ServiceRow(
                            service = service,
                            onEdit = { editTarget = service },
                            onToggleActive = { viewModel.toggleActive(service) }
                        )
                    }
                }
            }
        }
    }

    editTarget?.let { service ->
        EditServiceDialog(
            service = service,
            onDismiss = { editTarget = null },
            onSave = { req ->
                viewModel.update(service.id, req)
                editTarget = null
            }
        )
    }
}

@Composable
private fun ServiceRow(
    service: ServiceDto,
    onEdit: () -> Unit,
    onToggleActive: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(service.name, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    "Mulai ${Format.rupiah(service.basePrice)} · Kunjungan ${Format.rupiah(service.visitFee)}",
                    color = TKColor.Mist,
                    fontSize = 12.sp
                )
            }
            Switch(
                checked = service.isActive,
                onCheckedChange = { onToggleActive() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TKColor.White,
                    checkedTrackColor = TKColor.Orange,
                    uncheckedThumbColor = TKColor.Mist,
                    uncheckedTrackColor = TKColor.InkLine
                )
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.height(40.dp),
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.InkLine),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.White)
            ) {
                Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Ubah", fontSize = 13.sp)
            }
            Text(
                if (service.isActive) "Aktif" else "Nonaktif",
                color = if (service.isActive) TKColor.Green else TKColor.Mist,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun EditServiceDialog(
    service: ServiceDto,
    onDismiss: () -> Unit,
    onSave: (ServiceUpdateRequest) -> Unit
) {
    var name by remember { mutableStateOf(service.name) }
    var basePrice by remember { mutableStateOf(service.basePrice.toString()) }
    var visitFee by remember { mutableStateOf(service.visitFee.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val price = basePrice.toLongOrNull()
                val fee = visitFee.toLongOrNull()
                when {
                    name.isBlank() -> error = "Nama wajib diisi"
                    price == null || price < 0 -> error = "Harga tidak valid"
                    fee == null || fee < 0 -> error = "Biaya kunjungan tidak valid"
                    else -> onSave(ServiceUpdateRequest(name = name.trim(), basePrice = price, visitFee = fee))
                }
            }) { Text("Simpan", color = TKColor.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Ubah layanan", color = TKColor.White) },
        text = {
            Column {
                TextFieldDark(name, { name = it; error = null }, placeholder = "Nama layanan")
                Spacer(Modifier.height(10.dp))
                TextFieldDark(
                    basePrice,
                    { basePrice = it; error = null },
                    placeholder = "Harga mulai (Rupiah)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(Modifier.height(10.dp))
                TextFieldDark(
                    visitFee,
                    { visitFee = it; error = null },
                    placeholder = "Biaya kunjungan (Rupiah)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = TKColor.Red, fontSize = 13.sp) }
            }
        }
    )
}
