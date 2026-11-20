package id.tukangkilat.app.ui.consumer

import android.net.Uri
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
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
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.components.TKPrimaryButton
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.IssueReason
import id.tukangkilat.shared.OrderDto
import id.tukangkilat.shared.ReportOrderRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

data class ReportUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null,
    val reason: IssueReason = IssueReason.BELUM_TERATASI,
    val description: String = "",
    val photos: List<String> = emptyList(),
    val uploadingCount: Int = 0,
    val isSubmitting: Boolean = false
)

@HiltViewModel
class ReportViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(ReportUiState())
    val state: StateFlow<ReportUiState> = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = _state.value.copy(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onReason(reason: IssueReason) { _state.value = _state.value.copy(reason = reason, error = null) }
    fun onDescription(value: String) { _state.value = _state.value.copy(description = value, error = null) }

    fun uploadPhotos(uris: List<Uri>, context: android.content.Context) {
        val remaining = 3 - _state.value.photos.size
        val toUpload = uris.take(remaining)
        viewModelScope.launch {
            _state.value = _state.value.copy(uploadingCount = _state.value.uploadingCount + toUpload.size)
            for (uri in toUpload) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Berkas tidak bisa dibaca")
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val ext = if (mime.contains("png")) "png" else "jpg"
                    val body = MultipartBody.Part.createFormData(
                        "file",
                        "laporan.$ext",
                        bytes.toRequestBody(mime.toMediaType())
                    )
                    val res = api.uploadPhoto(body)
                    val url = res["url"] ?: throw IllegalStateException("Respon unggahan tidak valid")
                    _state.value = _state.value.copy(photos = _state.value.photos + url, uploadingCount = _state.value.uploadingCount - 1)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(uploadingCount = _state.value.uploadingCount - 1, error = e.message)
                }
            }
        }
    }

    fun removePhoto(url: String) { _state.value = _state.value.copy(photos = _state.value.photos - url) }

    fun submit(orderId: Long, onDone: () -> Unit) {
        val s = _state.value
        if (s.description.isBlank()) {
            _state.value = s.copy(error = "Keterangan masalah wajib diisi")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isSubmitting = true, error = null)
            try {
                orderRepository.report(orderId, ReportOrderRequest(s.reason, s.description.trim(), s.photos))
                onDone()
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSubmitting = false, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun ReportScreen(
    orderId: Long,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ReportViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(orderId) { viewModel.load(orderId) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(3)
    ) { uris -> if (uris.isNotEmpty()) viewModel.uploadPhotos(uris, context) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Lapor masalah", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            state.order?.let { order ->
                Text("Kami bantu sampai beres", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Laporkan kendala pada pesanan ${order.code} agar teknisi dapat menanganinya kembali.",
                    color = TKColor.Mist,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
            }

            Text("Apa kendalanya?", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            IssueReason.entries.forEach { reason ->
                val selected = reason == state.reason
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) TKColor.Cream else TKColor.Card)
                        .then(if (selected) Modifier.border(1.dp, TKColor.Orange, RoundedCornerShape(12.dp)) else Modifier)
                        .clickable { viewModel.onReason(reason) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selected,
                        onClick = { viewModel.onReason(reason) },
                        colors = RadioButtonDefaults.colors(selectedColor = TKColor.Orange, unselectedColor = TKColor.CreamText)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(reason.label, color = TKColor.Ink, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(6.dp))
            Text("Keterangan masalah", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            TextFieldDark(
                value = state.description,
                onValueChange = viewModel::onDescription,
                placeholder = "Contoh: Stopkontak ruang tamu masih tidak menyala saat digunakan untuk lampu.",
                minLines = 3,
                singleLine = false,
                isError = state.error == "Keterangan masalah wajib diisi"
            )

            Spacer(Modifier.height(16.dp))
            Text("Foto kondisi saat ini", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.photos) { url ->
                    Box {
                        AsyncImage(
                            model = BuildConfig.BASE_URL + url,
                            contentDescription = "Foto laporan",
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(TKColor.InkSoft)
                        )
                        IconButton(onClick = { viewModel.removePhoto(url) }, modifier = Modifier.align(Alignment.TopEnd)) {
                            Icon(Icons.Filled.Cancel, "Hapus", tint = TKColor.White)
                        }
                    }
                }
            }
            if (state.photos.size < 3) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TKColor.InkSoft)
                        .drawBehind {
                            drawRoundRect(
                                color = TKColor.Orange,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(
                                    width = 1.5.dp.toPx(),
                                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 9f))
                                )
                            )
                        }
                        .clickable {
                            photoPicker.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (state.uploadingCount > 0) Icons.Filled.Check else Icons.Filled.AddAPhoto,
                            null,
                            tint = TKColor.Orange,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                if (state.uploadingCount > 0) "Mengunggah..." else "Unggah foto",
                                color = TKColor.Orange,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Maksimal 3 foto · JPG atau PNG",
                                color = TKColor.Mist,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(TKColor.GreenSoft)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Info, null, tint = TKColor.GreenDeep, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "Tim kami akan menghubungi Anda untuk memastikan jadwal kunjungan ulang.",
                    color = TKColor.GreenDeep,
                    fontSize = 12.sp
                )
            }
            state.error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(16.dp))
            if (state.isSubmitting) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TKColor.Orange)
                }
            } else {
                TKPrimaryButton("Minta kunjungan ulang", onClick = { viewModel.submit(orderId, onDone) })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
