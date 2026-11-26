package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import android.Manifest
import android.location.Geocoder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.map.MapMarker
import id.tukangkilat.app.ui.map.OsmMap
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.tasks.await
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType

data class OrderFormUiState(
    val isLoadingServices: Boolean = true,
    val error: String? = null,
    val services: List<ServiceDto> = emptyList(),
    val selectedServiceId: Long? = null,
    val description: String = "",
    val photos: List<String> = emptyList(),
    val uploadingCount: Int = 0,
    val date: String = "",
    val slots: List<SlotDto> = emptyList(),
    val selectedSlotId: Long? = null,
    val isLoadingSlots: Boolean = false,
    val addresses: List<AddressDto> = emptyList(),
    val selectedAddressId: Long? = null,
    val isLocating: Boolean = false,
    val isSubmitting: Boolean = false,
    val showSlotError: Boolean = false
) {
    val selectedService: ServiceDto? get() = services.firstOrNull { it.id == selectedServiceId }
    val selectedAddress: AddressDto? get() = addresses.firstOrNull { it.id == selectedAddressId }
    val canSubmit: Boolean
        get() = selectedServiceId != null && description.isNotBlank() && date.isNotBlank() &&
            selectedSlotId != null && selectedAddressId != null && uploadingCount == 0 && !isSubmitting
}

@HiltViewModel
class OrderFormViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(OrderFormUiState(date = tomorrowString()))
    val state: StateFlow<OrderFormUiState> = _state.asStateFlow()

    fun load(preselectedServiceId: Long?) {
        viewModelScope.launch {
            try {
                val servicesDeferred = async { orderRepository.services() }
                val addressesDeferred = async { orderRepository.addresses() }
                val services = servicesDeferred.await()
                val addresses = addressesDeferred.await()
                _state.value = _state.value.copy(
                    isLoadingServices = false,
                    services = services,
                    selectedServiceId = preselectedServiceId?.takeIf { id -> services.any { it.id == id } } ?: services.firstOrNull()?.id,
                    addresses = addresses,
                    selectedAddressId = addresses.firstOrNull { it.isPrimary }?.id ?: addresses.firstOrNull()?.id
                )
                loadSlots()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingServices = false, error = e.cleanApiError())
            }
        }
    }

    fun loadSlots() {
        val date = _state.value.date
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingSlots = true, selectedSlotId = null)
            try {
                val slots = orderRepository.slots(date)
                _state.value = _state.value.copy(isLoadingSlots = false, slots = slots)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoadingSlots = false, error = e.cleanApiError())
            }
        }
    }

    fun selectService(id: Long) { _state.value = _state.value.copy(selectedServiceId = id) }
    fun onDescription(value: String) { _state.value = _state.value.copy(description = value) }
    fun onDate(value: String) { _state.value = _state.value.copy(date = value, selectedSlotId = null); loadSlots() }
    fun selectSlot(id: Long) { _state.value = _state.value.copy(selectedSlotId = id, showSlotError = false) }
    fun selectAddress(id: Long) { _state.value = _state.value.copy(selectedAddressId = id) }

    fun uploadPhotos(uris: List<Uri>, context: android.content.Context) {
        val s = _state.value
        val remaining = 3 - s.photos.size
        val toUpload = uris.take(remaining)
        if (toUpload.isEmpty()) {
            _state.value = s.copy(error = "Maksimal 3 foto")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadingCount = _state.value.uploadingCount + toUpload.size, error = null)
            for (uri in toUpload) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Berkas tidak bisa dibaca")
                    if (bytes.size > 5 * 1024 * 1024) throw IllegalStateException("Ukuran foto maksimal 5 MB")
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val body = okhttp3.MultipartBody.Part.createFormData(
                        "file",
                        "masalah.$ext",
                        bytes.toRequestBody(mime.toMediaType())
                    )
                    val res = api.uploadPhoto(body)
                    val url = res["url"] ?: throw IllegalStateException("Respon unggahan tidak valid")
                    _state.value = _state.value.copy(photos = _state.value.photos + url, uploadingCount = _state.value.uploadingCount - 1)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(uploadingCount = _state.value.uploadingCount - 1, error = e.cleanApiError())
                }
            }
        }
    }

    fun removePhoto(url: String) {
        _state.value = _state.value.copy(photos = _state.value.photos - url)
    }

    fun useCurrentLocation(context: android.content.Context) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLocating = true, error = null)
            try {
                val client = LocationServices.getFusedLocationProviderClient(context)
                val location = client.lastLocation.await()
                if (location == null) {
                    _state.value = _state.value.copy(isLocating = false, error = "Lokasi tidak tersedia. Coba lagi di tempat terbuka.")
                    return@launch
                }
                val addressText = try {
                    val geocoder = Geocoder(context, Locale.forLanguageTag("id"))
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()?.getAddressLine(0)
                } catch (e: Exception) { null } ?: "Lokasi saya (${location.latitude}, ${location.longitude})"

                val address = orderRepository.addAddress(
                    AddressUpsertRequest(
                        label = "Lokasi saya",
                        fullAddress = addressText,
                        lat = location.latitude,
                        lng = location.longitude,
                        isPrimary = true
                    )
                )
                _state.value = _state.value.copy(
                    isLocating = false,
                    addresses = _state.value.addresses.map { it.copy(isPrimary = false) } + address,
                    selectedAddressId = address.id
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLocating = false, error = e.cleanApiError())
            }
        }
    }

    fun submit(onDone: (Long) -> Unit) {
        val s = _state.value
        if (s.isSubmitting) return
        if (s.description.isBlank()) { _state.value = s.copy(error = "Ceritakan masalahnya"); return }
        if (s.selectedSlotId == null) { _state.value = s.copy(showSlotError = true); return }
        val serviceId = s.selectedServiceId ?: return
        val addressId = s.selectedAddressId ?: return
        _state.value = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            try {
                val order = orderRepository.createOrder(
                    CreateOrderRequest(
                        serviceId = serviceId,
                        description = s.description.trim(),
                        addressId = addressId,
                        scheduledDate = s.date,
                        slotId = s.selectedSlotId,
                        photoUrls = s.photos
                    )
                )
                onDone(order.id)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, error = e.cleanApiError())
            }
        }
    }
}

private fun tomorrowString(): String =
    java.time.LocalDate.now().plusDays(1).toString()

@Composable
fun OrderFormScreen(
    preselectedServiceId: Long?,
    onDone: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: OrderFormViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(preselectedServiceId) { viewModel.load(preselectedServiceId) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris -> if (uris.isNotEmpty()) viewModel.uploadPhotos(uris, context) }

    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { viewModel.uploadPhotos(listOf(it), context) }
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.useCurrentLocation(context) }

    if (state.isLoadingServices) {
        LoadingView()
        return
    }
    if (state.error != null && state.services.isEmpty()) {
        ErrorView(message = state.error!!, onRetry = { viewModel.load(preselectedServiceId) })
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
    ) {
        ScreenHeader("Pemesanan", onBack = onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            // selected service card
            state.selectedService?.let { service ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TKShape.Card)
                        .background(TKColor.Orange)
                        .padding(TKSpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (service.icon) {
                            "water_drop" -> Icons.Filled.WaterDrop
                            "ac_unit" -> Icons.Filled.AcUnit
                            else -> Icons.Filled.ElectricBolt
                        },
                        null,
                        tint = TKColor.White,
                        modifier = Modifier.size(30.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(service.name, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("Biaya jasa ${Format.rupiah(service.basePrice)}", color = TKColor.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            // service picker (compact)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.services.forEach { service ->
                    val selected = service.id == state.selectedServiceId
                    Text(
                        service.name,
                        color = if (selected) TKColor.White else TKColor.Mist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) TKColor.Orange else TKColor.InkSoft)
                            .clickable { viewModel.selectService(service.id) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("Ceritakan masalahnya", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(TKSpacing.Xs))
            TextFieldDark(
                value = state.description,
                onValueChange = viewModel::onDescription,
                placeholder = "Contoh: Stopkontak di ruang tamu tidak menyala.",
                minLines = 3,
                singleLine = false,
                isError = state.error == "Ceritakan masalahnya"
            )
            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Foto masalah (opsional)", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    val file = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    cameraUri = uri
                    cameraLauncher.launch(uri)
                }) { Text("Ambil foto", color = TKColor.Orange, fontSize = 12.sp) }
            }
            Spacer(Modifier.height(4.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.photos) { url ->
                    Box {
                        AsyncImage(
                            model = url.let { "${id.tukangkilat.app.BuildConfig.BASE_URL}$it" },
                            contentDescription = "Foto masalah",
                            modifier = Modifier
                                .size(96.dp)
                                .clip(TKShape.Media)
                                .background(TKColor.InkSoft)
                        )
                        IconButton(
                            onClick = { viewModel.removePhoto(url) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Filled.Cancel, "Hapus", tint = TKColor.White)
                        }
                    }
                }
                if (state.photos.size < 3) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(TKShape.Media)
                                .border(1.5.dp, TKColor.Orange, TKShape.Media)
                                .background(TKColor.InkSoft)
                                .clickable {
                                    photoPicker.launch(
                                        androidx.activity.result.PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly
                                        )
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (state.uploadingCount > 0) {
                                CircularProgressIndicator(color = TKColor.Orange, modifier = Modifier.size(24.dp))
                            } else {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Filled.AddAPhoto, null, tint = TKColor.Orange)
                                    Text("Tambah", color = TKColor.Orange, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Text("Tanggal dan waktu", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(TKSpacing.Xs))
            DateSelector(date = state.date, onDate = viewModel::onDate)
            Spacer(Modifier.height(10.dp))
            if (state.isLoadingSlots) {
                LinearProgressIndicator(color = TKColor.Orange, modifier = Modifier.fillMaxWidth())
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.slots.forEach { slot ->
                        val selected = slot.id == state.selectedSlotId
                        val enabled = slot.available
                        Text(
                            slot.label,
                            color = when {
                                selected -> TKColor.Orange
                                !enabled -> TKColor.Mist.copy(alpha = 0.5f)
                                else -> TKColor.Ink
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    when {
                                        selected -> TKColor.Card
                                        !enabled -> TKColor.InkSoft.copy(alpha = 0.4f)
                                        else -> TKColor.Card
                                    }
                                )
                                .border(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) TKColor.Orange else TKColor.InkLine,
                                    RoundedCornerShape(22.dp)
                                )
                                .clickable(enabled = enabled) { viewModel.selectSlot(slot.id) }
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                    if (state.showSlotError) {
                        Text("Pilih slot waktu", color = TKColor.Red, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    }
                }
            }
            Spacer(Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Alamat kunjungan", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                AddressPickerDialog(
                    addresses = state.addresses,
                    selectedId = state.selectedAddressId,
                    onSelect = viewModel::selectAddress
                )
            }
            Spacer(Modifier.height(TKSpacing.Xs))
            state.selectedAddress?.let { address ->
                Text(address.label, color = TKColor.Mist, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(address.fullAddress, color = TKColor.White, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                OsmMap(
                    centerLat = address.lat,
                    centerLng = address.lng,
                    markers = listOf(MapMarker(address.lat, address.lng, address.label, isHome = true)),
                    modifier = Modifier.fillMaxWidth().height(130.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            TextButton(
                onClick = { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                enabled = !state.isLocating
            ) {
                Icon(Icons.Filled.MyLocation, null, tint = TKColor.Orange)
                Spacer(Modifier.width(TKSpacing.Xs))
                Text(
                    if (state.isLocating) "Mencari lokasi..." else "Gunakan lokasi saya",
                    color = TKColor.Orange,
                    fontWeight = FontWeight.SemiBold
                )
            }

            state.error?.let {
                Spacer(Modifier.height(TKSpacing.Xs))
                Text(it, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(14.dp))
            if (state.isSubmitting) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TKColor.Orange)
                }
            } else {
                TKPrimaryButton(
                    "Lanjut ke pembayaran",
                    onClick = { viewModel.submit(onDone) },
                    enabled = state.canSubmit
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DateSelector(date: String, onDate: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val parsed = runCatching { java.time.LocalDate.parse(date) }.getOrDefault(java.time.LocalDate.now())
    val label = parsed.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.forLanguageTag("id")))

    if (showPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = parsed.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    val d = java.time.Instant.ofEpochMilli(utcTimeMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    return !d.isBefore(java.time.LocalDate.now())
                }
            }
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val d = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        onDate(d.toString())
                    }
                    showPicker = false
                }) { Text("Pilih", color = TKColor.Orange) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Batal", color = TKColor.Mist) }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.Orange)
            .clickable { showPicker = true }
            .padding(TKSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.CalendarMonth, null, tint = TKColor.White)
        Spacer(Modifier.width(12.dp))
        Text(label.replaceFirstChar { it.uppercase() }, color = TKColor.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

@Composable
private fun AddressPickerDialog(
    addresses: List<AddressDto>,
    selectedId: Long?,
    onSelect: (Long) -> Unit
) {
    var show by remember { mutableStateOf(false) }
    Text(
        "Ubah",
        color = TKColor.Orange,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        modifier = Modifier
            .clickable { show = true }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
    if (show) {
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { show = false }) { Text("Tutup", color = TKColor.Orange) } },
            containerColor = TKColor.InkSoft,
            title = { Text("Pilih alamat", color = TKColor.White) },
            text = {
                Column {
                    addresses.forEach { address ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(TKShape.Media)
                                .clickable { onSelect(address.id); show = false }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = address.id == selectedId,
                                onClick = { onSelect(address.id); show = false },
                                colors = RadioButtonDefaults.colors(selectedColor = TKColor.Orange, unselectedColor = TKColor.Mist)
                            )
                            Spacer(Modifier.width(TKSpacing.Xs))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(address.label, color = TKColor.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text(address.fullAddress, color = TKColor.Mist, fontSize = 12.sp, maxLines = 2)
                            }
                        }
                    }
                    if (addresses.isEmpty()) {
                        Text("Belum ada alamat tersimpan. Gunakan \"Gunakan lokasi saya\".", color = TKColor.Mist, fontSize = 13.sp)
                    }
                }
            }
        )
    }
}
