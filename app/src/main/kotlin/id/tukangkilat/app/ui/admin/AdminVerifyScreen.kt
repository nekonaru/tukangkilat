package id.tukangkilat.app.ui.admin

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import id.tukangkilat.shared.AdminTechnicianDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AdminVerifyUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val technicians: List<AdminTechnicianDto> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class AdminVerifyViewModel @Inject constructor(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _state = MutableStateFlow(AdminVerifyUiState())
    val state: StateFlow<AdminVerifyUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val technicians = adminRepository.technicians("MENUNGGU_VERIFIKASI")
                _state.value = AdminVerifyUiState(isLoading = false, technicians = technicians)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun approve(userId: Long) {
        viewModelScope.launch {
            try {
                adminRepository.approveTechnician(userId)
                _state.value = _state.value.copy(snackbar = "Tukang disetujui")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun reject(userId: Long, reason: String) {
        viewModelScope.launch {
            try {
                adminRepository.rejectTechnician(userId, reason)
                _state.value = _state.value.copy(snackbar = "Pendaftaran ditolak")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun AdminVerifyScreen(
    onBack: () -> Unit,
    viewModel: AdminVerifyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var rejectTarget by remember { mutableStateOf<AdminTechnicianDto?>(null) }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.technicians.isEmpty() -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
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
                ScreenHeader("Verifikasi tukang", onBack = onBack)
                if (state.technicians.isEmpty()) {
                    EmptyView(
                        title = "Tidak ada antrean",
                        subtitle = "Semua pendaftaran tukang sudah diverifikasi.",
                        icon = { Icon(Icons.Filled.VerifiedUser, null, tint = TKColor.Mist, modifier = Modifier.size(44.dp)) }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.technicians, key = { it.userId }) { tech ->
                            VerificationCard(
                                tech = tech,
                                onApprove = { viewModel.approve(tech.userId) },
                                onReject = { rejectTarget = tech }
                            )
                        }
                    }
                }
            }
        }
    }

    rejectTarget?.let { tech ->
        RejectDialog(
            onDismiss = { rejectTarget = null },
            onConfirm = { reason ->
                viewModel.reject(tech.userId, reason)
                rejectTarget = null
            }
        )
    }
}

@Composable
private fun VerificationCard(
    tech: AdminTechnicianDto,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(TKShape.Card)
            .background(TKColor.InkSoft)
            .padding(TKSpacing.Sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(tech.fullName, size = 52)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(tech.fullName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("@${tech.username} · ${tech.phone}", color = TKColor.Mist, fontSize = 12.sp)
                Text(
                    "Keahlian: ${tech.skills.joinToString(", ")}",
                    color = TKColor.Mist,
                    fontSize = 12.sp
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            tech.ktpPhotoUrl?.let { url ->
                VerificationPhoto(url = BuildConfig.BASE_URL + url, label = "KTP")
            }
            tech.selfiePhotoUrl?.let { url ->
                VerificationPhoto(url = BuildConfig.BASE_URL + url, label = "Selfie")
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = onReject,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = TKShape.Media,
                border = androidx.compose.foundation.BorderStroke(1.dp, TKColor.Red),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TKColor.Red)
            ) { Text("Tolak", fontWeight = FontWeight.Bold) }
            Button(
                onClick = onApprove,
                modifier = Modifier.weight(1f).height(46.dp),
                shape = TKShape.Media,
                colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange)
            ) { Text("Setujui", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun VerificationPhoto(url: String, label: String) {
    var zoom by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = url,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(120.dp)
                .clip(TKShape.Media)
                .background(TKColor.Ink)
                .clickable { zoom = true }
        )
        Spacer(Modifier.height(4.dp))
        Text(label, color = TKColor.Mist, fontSize = 11.sp)
    }
    if (zoom) {
        Dialog(onDismissRequest = { zoom = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            AsyncImage(
                model = url,
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .background(androidx.compose.ui.graphics.Color.Black)
                    .clickable { zoom = false }
            )
        }
    }
}

@Composable
private fun RejectDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var reason by remember { mutableStateOf("Data tidak valid") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(reason) }) { Text("Tolak", color = TKColor.Red) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Alasan penolakan", color = TKColor.White) },
        text = {
            Column {
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    singleLine = false,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TKColor.White,
                        unfocusedTextColor = TKColor.White,
                        focusedBorderColor = TKColor.Orange,
                        unfocusedBorderColor = TKColor.InkLine
                    )
                )
            }
        }
    )
}
