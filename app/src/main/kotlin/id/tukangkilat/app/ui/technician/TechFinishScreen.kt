package id.tukangkilat.app.ui.technician

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.BuildConfig
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.TechnicianRepository
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.FinishWorkRequest
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.PaymentMethod
import id.tukangkilat.shared.PaymentStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class TechFinishUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val note: String = "",
    val photos: List<String> = emptyList(),
    val uploadingCount: Int = 0,
    val cashReceived: Boolean = false,
    val isSubmitting: Boolean = false
) {
    val canSubmit: Boolean
        get() = note.isNotBlank() && photos.isNotEmpty() &&
            (order?.paymentMethod != PaymentMethod.TUNAI || cashReceived) &&
            uploadingCount == 0 && !isSubmitting
}

@HiltViewModel
class TechFinishViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val technicianRepository: TechnicianRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(TechFinishUiState())
    val state: StateFlow<TechFinishUiState> = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = TechFinishUiState(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = TechFinishUiState(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onNote(value: String) { _state.value = _state.value.copy(note = value, error = null) }
    fun onCashReceived(value: Boolean) { _state.value = _state.value.copy(cashReceived = value) }

    fun uploadPhotos(uris: List<Uri>, context: android.content.Context) {
        val remaining = 5 - _state.value.photos.size
        val toUpload = uris.take(remaining)
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadingCount = _state.value.uploadingCount + toUpload.size, error = null)
            for (uri in toUpload) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Berkas tidak bisa dibaca")
                    if (bytes.size > 5 * 1024 * 1024) throw IllegalStateException("Ukuran foto maksimal 5 MB")
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val body = MultipartBody.Part.createFormData(
                        "file",
                        "bukti.$ext",
                        bytes.toRequestBody(mime.toMediaType())
                    )
                    val res = api.uploadPhoto(body)
                    val url = res["url"] ?: throw IllegalStateException("Respon unggahan tidak valid")
                    _state.value = _state.value.copy(photos = _state.value.photos + url, uploadingCount = _state.value.uploadingCount - 1)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(uploadingCount = _state.value.uploadingCount - 1, error = e.message ?: "Gagal mengunggah foto")
                }
            }
        }
    }

    fun removePhoto(url: String) { _state.value = _state.value.copy(photos = _state.value.photos - url) }

    fun submit(orderId: Long, onDone: () -> Unit) {
        val s = _state.value
        if (s.note.isBlank()) { _state.value = s.copy(error = "Catatan pekerjaan wajib diisi"); return }
        if (s.photos.isEmpty()) { _state.value = s.copy(error = "Minimal 1 foto bukti"); return }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                technicianRepository.finish(
                    orderId,
                    FinishWorkRequest(
                        note = s.note.trim(),
                        photoUrls = s.photos,
                        cashReceived = s.cashReceived
                    )
                )
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun TechFinishScreen(
    orderId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: TechFinishViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(orderId) { viewModel.load(orderId) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris -> if (uris.isNotEmpty()) viewModel.uploadPhotos(uris, context) }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        else -> {
            val order = state.order!!
            val isCash = order.paymentMethod == PaymentMethod.TUNAI
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TKColor.Ink)
                    .verticalScroll(rememberScrollState())
            ) {
                ScreenHeader("Selesaikan pekerjaan", onBack = onBack)
                Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                    Text(order.serviceName, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                    Text(order.code, color = TKColor.Mist, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))

                    Text("Foto bukti (1-5)", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.photos) { url ->
                            Box {
                                AsyncImage(
                                    model = BuildConfig.BASE_URL + url,
                                    contentDescription = "Foto bukti",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(TKColor.InkSoft)
                                )
                                IconButton(onClick = { viewModel.removePhoto(url) }, modifier = Modifier.align(Alignment.TopEnd)) {
                                    Icon(Icons.Filled.Cancel, "Hapus", tint = TKColor.White)
                                }
                            }
                        }
                        if (state.photos.size < 5) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.5.dp, TKColor.Orange, RoundedCornerShape(12.dp))
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
                                            Text("Tambah", color = TKColor.Orange, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(18.dp))

                    Text("Catatan pekerjaan", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(8.dp))
                    TextFieldDark(
                        value = state.note,
                        onValueChange = viewModel::onNote,
                        placeholder = "Contoh: Stopkontak sudah diganti dan diuji berfungsi.",
                        minLines = 3,
                        singleLine = false,
                        isError = state.error == "Catatan pekerjaan wajib diisi"
                    )

                    if (isCash) {
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (state.cashReceived) TKColor.OrangeSoft else TKColor.InkSoft)
                                .clickable { viewModel.onCashReceived(!state.cashReceived) }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (state.cashReceived) TKColor.Orange else TKColor.InkLine),
                                contentAlignment = Alignment.Center
                            ) {
                                if (state.cashReceived) {
                                    Icon(Icons.Filled.Check, null, tint = TKColor.White, modifier = Modifier.size(18.dp))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Uang tunai ${Format.rupiah(order.total)} sudah diterima",
                                color = TKColor.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 14.sp
                            )
                        }
                    }

                    state.error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(18.dp))
                    if (state.isSubmitting) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TKColor.Orange)
                        }
                    } else {
                        TKPrimaryButton("Selesaikan pekerjaan", onClick = { viewModel.submit(orderId, onDone) }, enabled = state.canSubmit)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
