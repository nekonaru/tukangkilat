package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.components.AvatarCircle
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.AddressDto
import id.tukangkilat.shared.AddressUpsertRequest
import id.tukangkilat.shared.ChangePasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val fullName: String = "",
    val username: String = "",
    val phone: String = "",
    val addresses: List<AddressDto> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val session = authRepository.currentSession()
                // wait for the first emission
                val addresses = orderRepository.addresses()
                _state.value = _state.value.copy(
                    isLoading = false,
                    addresses = addresses
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
        viewModelScope.launch {
            authRepository.currentSession().collect { s ->
                s.user?.let { u ->
                    _state.value = _state.value.copy(fullName = u.fullName, username = u.username, phone = u.phone.orEmpty())
                }
            }
        }
    }

    fun changePassword(oldPassword: String, newPassword: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                api.changePassword(ChangePasswordRequest(oldPassword, newPassword))
                _state.value = _state.value.copy(snackbar = "Password berhasil diubah")
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun setPrimary(address: AddressDto) {
        viewModelScope.launch {
            try {
                orderRepository.updateAddress(
                    address.id,
                    AddressUpsertRequest(address.label, address.fullAddress, address.lat, address.lng, isPrimary = true)
                )
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun deleteAddress(address: AddressDto) {
        viewModelScope.launch {
            try {
                orderRepository.deleteAddress(address.id)
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(snackbar = e.cleanApiError())
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun AccountScreen(
    onNotifications: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }

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
            ScreenHeader("Akun")
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarCircle(state.fullName.ifBlank { "U" }, size = 84)
                        Spacer(Modifier.height(12.dp))
                        Text(state.fullName.ifBlank { "Pengguna" }, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("@${state.username}", color = TKColor.Mist, fontSize = 13.sp)
                        if (state.phone.isNotBlank()) Text(state.phone, color = TKColor.Mist, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(14.dp))
                }
                item {
                    SectionRow(Icons.Filled.Lock, "Ubah password") { showPasswordDialog = true }
                    SectionRow(Icons.Filled.Place, "Kelola alamat (${state.addresses.size})") { showAddressDialog = true }
                    SectionRow(Icons.Filled.Notifications, "Notifikasi", onClick = onNotifications)
                    Spacer(Modifier.height(8.dp))
                    SectionRow(Icons.Filled.Info, "Tentang TukangKilat") { }
                    Spacer(Modifier.height(8.dp))
                    SectionRow(Icons.Filled.Logout, "Keluar", danger = true) { viewModel.logout() }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (showPasswordDialog) {
        ChangePasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSubmit = { old, new -> viewModel.changePassword(old, new) { showPasswordDialog = false } }
        )
    }
    if (showAddressDialog) {
        AddressDialog(
            addresses = state.addresses,
            onDismiss = { showAddressDialog = false },
            onSetPrimary = viewModel::setPrimary,
            onDelete = viewModel::deleteAddress
        )
    }
}

@Composable
private fun SectionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) TKColor.Red else TKColor.Orange)
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            color = if (danger) TKColor.Red else TKColor.White,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ChevronRight, null, tint = TKColor.InkLine)
    }
}

@Composable
private fun ChangePasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                when {
                    oldPassword.isBlank() || newPassword.isBlank() -> error = "Isi semua kolom"
                    newPassword.length < 8 -> error = "Password baru minimal 8 karakter"
                    newPassword != confirm -> error = "Konfirmasi tidak cocok"
                    else -> onSubmit(oldPassword, newPassword)
                }
            }) { Text("Simpan", color = TKColor.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Ubah password", color = TKColor.White) },
        text = {
            Column {
                TextFieldDark(oldPassword, { oldPassword = it }, placeholder = "Password lama", visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(10.dp))
                TextFieldDark(newPassword, { newPassword = it }, placeholder = "Password baru (min 8)", visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(10.dp))
                TextFieldDark(confirm, { confirm = it }, placeholder = "Ulangi password baru", visualTransformation = PasswordVisualTransformation())
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = TKColor.Red, fontSize = 13.sp) }
            }
        }
    )
}

@Composable
private fun AddressDialog(
    addresses: List<AddressDto>,
    onDismiss: () -> Unit,
    onSetPrimary: (AddressDto) -> Unit,
    onDelete: (AddressDto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup", color = TKColor.Orange) }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Kelola alamat", color = TKColor.White) },
        text = {
            LazyColumn {
                items(addresses, key = { it.id }) { address ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(address.label, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (address.isPrimary) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(TKColor.Orange)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) { Text("UTAMA", color = TKColor.White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold) }
                                }
                            }
                            Text(address.fullAddress, color = TKColor.Mist, fontSize = 12.sp, maxLines = 2)
                        }
                        if (!address.isPrimary) {
                            IconButton(onClick = { onSetPrimary(address) }) {
                                Icon(Icons.Filled.CheckCircle, "Jadikan utama", tint = TKColor.Orange)
                            }
                        }
                        IconButton(onClick = { onDelete(address) }) {
                            Icon(Icons.Filled.Delete, "Hapus", tint = TKColor.Red)
                        }
                    }
                    HorizontalDivider(color = TKColor.InkLine.copy(alpha = 0.4f))
                }
                if (addresses.isEmpty()) {
                    item {
                        Text("Belum ada alamat tersimpan.", color = TKColor.Mist, fontSize = 13.sp)
                    }
                }
            }
        }
    )
}
