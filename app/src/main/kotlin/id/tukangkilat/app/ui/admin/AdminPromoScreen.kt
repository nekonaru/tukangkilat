package id.tukangkilat.app.ui.admin

import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalActivity
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
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
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.PromoCodeDto
import id.tukangkilat.shared.PromoUpsertRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminPromoUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val promos: List<PromoCodeDto> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class AdminPromoViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminPromoUiState())
    val state: StateFlow<AdminPromoUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val promos = adminRepository.promoCodes()
                _state.value = _state.value.copy(isLoading = false, promos = promos, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun toggle(promo: PromoCodeDto) {
        save(
            PromoUpsertRequest(
                code = promo.code,
                type = promo.type,
                value = promo.value,
                validUntil = promo.validUntil,
                quota = promo.quota,
                isActive = !promo.isActive
            )
        )
    }

    fun save(req: PromoUpsertRequest) {
        viewModelScope.launch {
            try {
                adminRepository.upsertPromoCode(req)
                _state.value = _state.value.copy(snackbar = "Kode promo tersimpan")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun AdminPromoScreen(
    onBack: () -> Unit,
    viewModel: AdminPromoViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAdd by remember { mutableStateOf(false) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.promos.isEmpty() -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
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
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, "Kembali", tint = TKColor.White) }
                    Text(
                        "Kelola promo",
                        color = TKColor.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showAdd = true }) {
                        Icon(Icons.Filled.Add, "Tambah promo", tint = TKColor.Orange)
                    }
                }
                if (state.promos.isEmpty()) {
                    EmptyView(
                        title = "Belum ada promo",
                        subtitle = "Tekan tombol + untuk membuat kode promo pertama.",
                        icon = { Icon(Icons.Filled.LocalActivity, null, tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(state.promos, key = { it.code }) { promo ->
                            PromoRow(promo = promo, onToggle = { viewModel.toggle(promo) })
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        PromoFormDialog(
            onDismiss = { showAdd = false },
            onSave = { req ->
                viewModel.save(req)
                showAdd = false
            }
        )
    }
}

@Composable
private fun PromoRow(promo: PromoCodeDto, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(promo.code, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        if (promo.isActive) "Aktif" else "Nonaktif",
                        color = if (promo.isActive) TKColor.Green else TKColor.Mist,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (promo.isActive) TKColor.GreenSoft else TKColor.InkLine)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    if (promo.type == "PERSEN") "Diskon ${promo.value}%" else "Diskon ${Format.rupiah(promo.value)}",
                    color = TKColor.Mist,
                    fontSize = 13.sp
                )
                Text("Kuota ${promo.quota} tersisa · Berlaku sampai ${promo.validUntil.take(10)}", color = TKColor.Mist, fontSize = 11.sp)
            }
            Switch(
                checked = promo.isActive,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = TKColor.White,
                    checkedTrackColor = TKColor.Orange,
                    uncheckedThumbColor = TKColor.Mist,
                    uncheckedTrackColor = TKColor.InkLine
                )
            )
        }
    }
}

@Composable
private fun PromoFormDialog(
    onDismiss: () -> Unit,
    onSave: (PromoUpsertRequest) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("NOMINAL") }
    var value by remember { mutableStateOf("") }
    var quota by remember { mutableStateOf("10") }
    var validUntil by remember { mutableStateOf(java.time.LocalDate.now().plusMonths(6).toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val v = value.toLongOrNull()
                val q = quota.toIntOrNull()
                when {
                    code.isBlank() -> error = "Kode wajib diisi"
                    v == null || v <= 0 -> error = "Nilai harus > 0"
                    type == "PERSEN" && v > 100 -> error = "Persen maksimal 100"
                    q == null || q < 0 -> error = "Kuota tidak valid"
                    validUntil.isBlank() -> error = "Tanggal wajib diisi"
                    else -> onSave(
                        PromoUpsertRequest(
                            code = code.trim().uppercase(),
                            type = type,
                            value = v,
                            validUntil = validUntil.trim() + "T23:59:59Z",
                            quota = q,
                            isActive = true
                        )
                    )
                }
            }) { Text("Simpan", color = TKColor.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Kode promo baru", color = TKColor.White) },
        text = {
            Column {
                TextFieldDark(code, { code = it; error = null }, placeholder = "Kode promo (mis. HEMAT25)")
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Diskon:", color = TKColor.Mist, fontSize = 12.sp)
                    Spacer(Modifier.width(12.dp))
                    FilterChip(
                        selected = type == "NOMINAL",
                        onClick = { type = "NOMINAL" },
                        label = { Text("Rupiah") }
                    )
                    Spacer(Modifier.width(6.dp))
                    FilterChip(
                        selected = type == "PERSEN",
                        onClick = { type = "PERSEN" },
                        label = { Text("Persen") }
                    )
                }
                Spacer(Modifier.height(10.dp))
                TextFieldDark(
                    value,
                    { value = it; error = null },
                    placeholder = if (type == "PERSEN") "Persentase (1-100)" else "Nominal (Rp)",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(Modifier.height(10.dp))
                TextFieldDark(
                    quota,
                    { quota = it; error = null },
                    placeholder = "Kuota",
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(Modifier.height(10.dp))
                TextFieldDark(validUntil, { validUntil = it; error = null }, placeholder = "Berlaku sampai (YYYY-MM-DD)")
                error?.let { Spacer(Modifier.height(TKSpacing.Xs)); Text(it, color = TKColor.Red, fontSize = 13.sp) }
            }
        }
    )
}
