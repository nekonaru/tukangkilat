package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKSpacing

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
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
import com.google.android.gms.location.LocationServices
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.local.FavoriteTechnician
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.data.repository.FavoriteRepository
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.components.AvatarCircle
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.AddressDto
import id.tukangkilat.shared.AddressUpsertRequest
import id.tukangkilat.shared.ChangePasswordRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale
import javax.inject.Inject

data class AccountUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val fullName: String = "",
    val username: String = "",
    val phone: String = "",
    val addresses: List<AddressDto> = emptyList(),
    val favorites: List<FavoriteTechnician> = emptyList(),
    val snackbar: String? = null
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val orderRepository: OrderRepository,
    private val favoriteRepository: FavoriteRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            authRepository.currentSession().collect { s ->
                s.user?.let { u ->
                    _state.value = _state.value.copy(
                        fullName = u.fullName,
                        username = u.username,
                        phone = u.phone.orEmpty()
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val addressesDeferred = async { orderRepository.addresses() }
                favoriteRepository.refresh()
                _state.value = _state.value.copy(
                    isLoading = false,
                    addresses = addressesDeferred.await(),
                    favorites = favoriteRepository.items.value
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
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

    fun saveAddress(
        existing: AddressDto?,
        label: String,
        fullAddress: String,
        lat: Double,
        lng: Double
    ) {
        viewModelScope.launch {
            try {
                val request = AddressUpsertRequest(
                    label.trim(),
                    fullAddress.trim(),
                    lat,
                    lng,
                    isPrimary = existing?.isPrimary ?: _state.value.addresses.isEmpty()
                )
                if (existing == null) orderRepository.addAddress(request)
                else orderRepository.updateAddress(existing.id, request)
                refresh()
                _state.value = _state.value.copy(snackbar = if (existing == null) "Alamat ditambahkan" else "Alamat diperbarui")
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

    fun removeFavorite(userId: Long) {
        viewModelScope.launch {
            favoriteRepository.remove(userId)
            _state.value = _state.value.copy(favorites = favoriteRepository.items.value)
        }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

@Composable
fun AccountScreen(
    onNotifications: () -> Unit,
    onSettings: () -> Unit = {},
    onWallet: () -> Unit = {},
    onBrowseTechnicians: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showAddressEditor by remember { mutableStateOf(false) }
    var addressBeingEdited by remember { mutableStateOf<AddressDto?>(null) }
    var showFavorites by remember { mutableStateOf(false) }

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
            state.error?.let { message ->
                Text(
                    message,
                    color = TKColor.Red,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = TKSpacing.Sm)
                )
                TextButton(onClick = { viewModel.refresh() }) { Text("Coba lagi", color = TKColor.Orange) }
            }
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = TKSpacing.Sm, vertical = TKSpacing.Xs)
                            .background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
                            .padding(TKSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AvatarCircle(state.fullName.ifBlank { "U" }, size = 56)
                        Spacer(Modifier.width(TKSpacing.Sm))
                        Column(Modifier.weight(1f)) {
                            Text(state.fullName.ifBlank { "Pengguna" }, style = MaterialTheme.typography.titleMedium)
                            Text("@${state.username}", style = MaterialTheme.typography.bodySmall, color = TKColor.Mist)
                            if (state.phone.isNotBlank()) {
                                Text(state.phone, style = MaterialTheme.typography.bodySmall, color = TKColor.Mist)
                            }
                        }
                    }
                    Spacer(Modifier.height(TKSpacing.Sm))
                }
                item {
                    SectionRow(Icons.Filled.AccountBalanceWallet, "Dompet & langganan", onClick = onWallet)
                    SectionLabel("Akun")
                    SectionRow(Icons.Filled.Lock, "Ubah password") { showPasswordDialog = true }
                    SectionRow(Icons.Filled.Place, "Kelola alamat (${state.addresses.size})") { showAddressDialog = true }
                    SectionRow(Icons.Filled.Notifications, "Notifikasi", onClick = onNotifications)
                    SectionRow(Icons.Filled.Settings, "Pengaturan", onClick = onSettings)
                    SectionRow(Icons.Filled.Info, "Tentang TukangKilat", onClick = onSettings)

                    Spacer(Modifier.height(TKSpacing.Sm))
                    SectionLabel("Teknisi")
                    SectionRow(Icons.Filled.Star, "Teknisi favorit (${state.favorites.size})") { showFavorites = true }
                    SectionRow(Icons.Filled.Search, "Cari teknisi terverifikasi", onClick = onBrowseTechnicians)

                    Spacer(Modifier.height(TKSpacing.Sm))
                    SectionRow(Icons.Filled.Logout, "Keluar", danger = true) { viewModel.logout() }
                    Spacer(Modifier.height(TKSpacing.Md))
                }
            }
        }
    }

    if (showFavorites) {
        AlertDialog(
            onDismissRequest = { showFavorites = false },
            containerColor = TKColor.InkSoft,
            title = { Text("Teknisi favorit", color = TKColor.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    if (state.favorites.isEmpty()) {
                        Text("Belum ada teknisi favorit. Simpan teknisi dari struk pesanan selesai.", color = TKColor.Mist, fontSize = 13.sp)
                    } else {
                        state.favorites.forEach { fav ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(fav.fullName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("Rating ${fav.ratingAvg} · ${fav.jobsCount} pekerjaan", color = TKColor.Mist, fontSize = 11.sp)
                                }
                                TextButton(onClick = { viewModel.removeFavorite(fav.userId) }) {
                                    Text("Hapus", color = TKColor.Red, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFavorites = false }) { Text("Tutup", color = TKColor.Orange) } }
        )
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
            onDelete = viewModel::deleteAddress,
            onAdd = {
                addressBeingEdited = null
                showAddressEditor = true
            },
            onEdit = { address ->
                addressBeingEdited = address
                showAddressEditor = true
            }
        )
    }

    if (showAddressEditor) {
        AddressEditorDialog(
            existing = addressBeingEdited,
            onDismiss = { showAddressEditor = false },
            onSave = { label, fullAddress, lat, lng ->
                viewModel.saveAddress(addressBeingEdited, label, fullAddress, lat, lng)
                showAddressEditor = false
            }
        )
    }
}

@Composable
private fun AddressEditorDialog(
    existing: AddressDto?,
    onDismiss: () -> Unit,
    onSave: (String, String, Double, Double) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(existing?.label ?: "Rumah") }
    var fullAddress by remember { mutableStateOf(existing?.fullAddress ?: "") }
    var latText by remember { mutableStateOf(existing?.lat?.toString().orEmpty()) }
    var lngText by remember { mutableStateOf(existing?.lng?.toString().orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionGranted = it
        if (!it) error = "Izin lokasi ditolak. Isi titik lokasi Manual atau izinkan di pengaturan."
    }

    fun useCurrentLocation() {
        if (!permissionGranted) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        scope.launch {
            try {
                val location = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
                if (location == null) {
                    error = "Lokasi belum tersedia. Coba lagi atau isi manual."
                    return@launch
                }
                latText = location.latitude.toString()
                lngText = location.longitude.toString()
                if (fullAddress.isBlank()) {
                    val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val geocoder = Geocoder(context, Locale.forLanguageTag("id"))
                        @Suppress("DEPRECATION")
                        try {
                            geocoder.getFromLocation(location.latitude, location.longitude, 1)
                                ?.firstOrNull()?.getAddressLine(0)
                        } catch (e: Exception) { null }
                    }
                    fullAddress = text ?: "Lokasi saya (${location.latitude}, ${location.longitude})"
                }
                error = null
            } catch (e: Exception) {
                error = e.cleanApiError()
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TKColor.InkSoft,
        title = {
            Text(if (existing == null) "Tambah alamat" else "Ubah alamat", color = TKColor.White, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                TextFieldDark(label, { label = it }, placeholder = "Label, misalnya Rumah")
                Spacer(Modifier.height(TKSpacing.Xs))
                TextFieldDark(fullAddress, { fullAddress = it }, placeholder = "Alamat lengkap")
                Spacer(Modifier.height(TKSpacing.Xs))
                TextButton(onClick = { useCurrentLocation() }) {
                    Text("Gunakan lokasi saya", color = TKColor.Orange, fontSize = 12.sp)
                }
                Text(
                    "Titik lokasi: ${if (latText.isBlank() || lngText.isBlank()) "belum diambil" else "$latText, $lngText"}",
                    color = TKColor.Mist,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(4.dp))
                TextFieldDark(latText, { latText = it }, placeholder = "Lintang")
                TextFieldDark(lngText, { lngText = it }, placeholder = "Bujur")
                error?.let { Text(it, color = TKColor.Red, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedLat = latText.toDoubleOrNull()
                val parsedLng = lngText.toDoubleOrNull()
                error = when {
                    label.isBlank() -> "Label wajib diisi"
                    fullAddress.isBlank() -> "Alamat wajib diisi"
                    parsedLat == null || parsedLng == null -> "Titik lokasi harus berupa angka"
                    parsedLat !in -90.0..90.0 || parsedLng !in -180.0..180.0 -> "Titik lokasi tidak valid"
                    parsedLat == 0.0 && parsedLng == 0.0 -> "Ambil lokasi atau isi titik lokasi"
                    else -> {
                        onSave(label, fullAddress, parsedLat, parsedLng)
                        null
                    }
                }
            }) { Text("Simpan", color = TKColor.Orange) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) } }
    )
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
        Icon(icon, label, tint = if (danger) TKColor.Red else TKColor.Orange)
        Spacer(Modifier.width(TKSpacing.Sm))
        Text(
            label,
            color = if (danger) TKColor.Red else TKColor.White,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.Filled.ChevronRight, "Lanjut", tint = TKColor.InkLine)
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
                error?.let { Spacer(Modifier.height(TKSpacing.Xs)); Text(it, color = TKColor.Red, fontSize = 13.sp) }
            }
        }
    )
}

@Composable
private fun AddressDialog(
    addresses: List<AddressDto>,
    onDismiss: () -> Unit,
    onSetPrimary: (AddressDto) -> Unit,
    onDelete: (AddressDto) -> Unit,
    onAdd: () -> Unit,
    onEdit: (AddressDto) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(horizontalArrangement = Arrangement.Start) {
                TextButton(onClick = onAdd) { Text("Tambah", color = TKColor.Orange) }
                TextButton(onClick = onDismiss) { Text("Tutup", color = TKColor.Mist) }
            }
        },
        containerColor = TKColor.InkSoft,
        title = { Text("Kelola alamat", color = TKColor.White) },
        text = {
            LazyColumn {
                items(addresses, key = { it.id }) { address ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = TKSpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(address.label, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (address.isPrimary) {
                                    Spacer(Modifier.width(TKSpacing.Xs))
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
                        IconButton(onClick = { onEdit(address) }) {
                            Icon(Icons.Filled.Edit, "Ubah alamat", tint = TKColor.Mist)
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
