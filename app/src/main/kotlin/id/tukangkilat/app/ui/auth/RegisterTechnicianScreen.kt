package id.tukangkilat.app.ui.auth

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.TKPrimaryButton
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.RegisterTechnicianRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import javax.inject.Inject

data class RegisterTechUiState(
    val fullName: String = "",
    val username: String = "",
    val phone: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val serviceRadiusKm: Int = 10,
    val skills: Set<String> = emptySet(),
    val ktpPhotoUrl: String? = null,
    val selfiePhotoUrl: String? = null,
    val uploadingKtp: Boolean = false,
    val uploadingSelfie: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class RegisterTechnicianViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterTechUiState())
    val state: StateFlow<RegisterTechUiState> = _state.asStateFlow()

    fun onChange(block: RegisterTechUiState.() -> RegisterTechUiState) {
        _state.value = _state.value.block().copy(error = null)
    }

    fun toggleSkill(skill: String) {
        val s = _state.value
        val newSkills = if (skill in s.skills) s.skills - skill else s.skills + skill
        _state.value = s.copy(skills = newSkills, error = null)
    }

    fun uploadPhoto(context: android.content.Context, uri: Uri, which: String) {
        viewModelScope.launch {
            try {
                if (which == "ktp") _state.value = _state.value.copy(uploadingKtp = true)
                else _state.value = _state.value.copy(uploadingSelfie = true)

                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Berkas tidak bisa dibaca")
                if (bytes.size > 5 * 1024 * 1024) throw IllegalStateException("Ukuran foto maksimal 5 MB")
                val body = MultipartBody.Part.createFormData(
                    "file",
                    if (which == "ktp") "ktp.jpg" else "selfie.jpg",
                    bytes.toRequestBody("image/jpeg".toMediaType())
                )
                val res = api.uploadPhoto(body)
                val url = res["url"] ?: throw IllegalStateException("Respon unggahan tidak valid")
                if (which == "ktp") _state.value = _state.value.copy(uploadingKtp = false, ktpPhotoUrl = url)
                else _state.value = _state.value.copy(uploadingSelfie = false, selfiePhotoUrl = url)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    uploadingKtp = if (which == "ktp") false else _state.value.uploadingKtp,
                    uploadingSelfie = if (which == "selfie") false else _state.value.uploadingSelfie,
                    error = e.cleanApiError()
                )
            }
        }
    }

    fun submit() {
        val s = _state.value
        when {
            s.fullName.isBlank() || s.username.isBlank() || s.phone.isBlank() ->
                _state.value = s.copy(error = "Nama, username, dan nomor HP wajib diisi")
            s.skills.isEmpty() ->
                _state.value = s.copy(error = "Pilih minimal satu keahlian")
            s.ktpPhotoUrl == null || s.selfiePhotoUrl == null ->
                _state.value = s.copy(error = "Foto KTP dan selfie wajib diunggah")
            s.password.length < 8 ->
                _state.value = s.copy(error = "Password minimal 8 karakter")
            s.password != s.confirmPassword ->
                _state.value = s.copy(error = "Konfirmasi password tidak cocok")
            else -> {
                _state.value = s.copy(isLoading = true, error = null)
                viewModelScope.launch {
                    val result = authRepository.registerTechnician(
                        RegisterTechnicianRequest(
                            fullName = s.fullName.trim(),
                            username = s.username.trim(),
                            password = s.password,
                            phone = s.phone.trim(),
                            skills = s.skills.toList(),
                            serviceRadiusKm = s.serviceRadiusKm,
                            ktpPhotoUrl = s.ktpPhotoUrl!!,
                            selfiePhotoUrl = s.selfiePhotoUrl!!
                        )
                    )
                    result.onSuccess {
                        _state.value = RegisterTechUiState(done = true)
                    }.onFailure { e ->
                        _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
                    }
                }
            }
        }
    }
}

private val SKILLS = listOf("LISTRIK" to "Listrik", "PIPA" to "Pipa", "AC" to "Servis AC")

@Composable
fun RegisterTechnicianScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterTechnicianViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    val ktpPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadPhoto(context, it, "ktp") } }
    val selfiePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { viewModel.uploadPhoto(context, it, "selfie") } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Daftar jadi tukang", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Isi data diri. Akun kamu diaktifkan setelah diverifikasi admin.",
                color = TKColor.Mist,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(20.dp))
            TextFieldDark(state.fullName, { v -> viewModel.onChange { copy(fullName = v) } }, placeholder = "Nama lengkap")
            Spacer(Modifier.height(12.dp))
            TextFieldDark(state.username, { v -> viewModel.onChange { copy(username = v) } }, placeholder = "Username")
            Spacer(Modifier.height(12.dp))
            TextFieldDark(state.phone, { v -> viewModel.onChange { copy(phone = v) } }, placeholder = "Nomor HP (08...)")
            Spacer(Modifier.height(12.dp))
            TextFieldDark(
                state.password,
                { v -> viewModel.onChange { copy(password = v) } },
                placeholder = "Password (min 8 karakter)",
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )
            Spacer(Modifier.height(12.dp))
            TextFieldDark(
                state.confirmPassword,
                { v -> viewModel.onChange { copy(confirmPassword = v) } },
                placeholder = "Ulangi password",
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
            )

            Spacer(Modifier.height(20.dp))
            Text("Keahlian", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(TKSpacing.Xs))
            SKILLS.forEach { (key, label) ->
                val checked = key in state.skills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(TKShape.Media)
                        .background(if (checked) TKColor.OrangeSoft else TKColor.InkSoft)
                        .clickable { viewModel.toggleSkill(key) }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (checked) TKColor.Orange else TKColor.InkLine),
                        contentAlignment = Alignment.Center
                    ) {
                        if (checked) Icon(Icons.Filled.Check, "Centang", tint = TKColor.White, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(label, color = TKColor.White, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(TKSpacing.Xs))
            }

            Spacer(Modifier.height(TKSpacing.Xs))
            Text("Radius layanan (km): ${state.serviceRadiusKm}", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Slider(
                value = state.serviceRadiusKm.toFloat(),
                onValueChange = { v -> viewModel.onChange { copy(serviceRadiusKm = v.toInt()) } },
                valueRange = 1f..50f,
                steps = 48,
                colors = SliderDefaults.colors(thumbColor = TKColor.Orange, activeTrackColor = TKColor.Orange, inactiveTrackColor = TKColor.InkLine)
            )

            Spacer(Modifier.height(TKSpacing.Xs))
            Text("Foto KTP", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(TKSpacing.Xs))
            PhotoUploadBox(
                onClick = { ktpPicker.launch("image/*") },
                uploading = state.uploadingKtp,
                done = state.ktpPhotoUrl != null,
                label = "Unggah foto KTP"
            )
            Spacer(Modifier.height(12.dp))
            Text("Selfie", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(Modifier.height(TKSpacing.Xs))
            PhotoUploadBox(
                onClick = { selfiePicker.launch("image/*") },
                uploading = state.uploadingSelfie,
                done = state.selfiePhotoUrl != null,
                label = "Unggah selfie"
            )

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.error!!, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.height(20.dp))
            if (state.isLoading) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TKColor.Orange)
                }
            } else {
                TKPrimaryButton("Kirim pendaftaran", onClick = viewModel::submit)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun PhotoUploadBox(onClick: () -> Unit, uploading: Boolean, done: Boolean, label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (done) TKColor.GreenSoft else TKColor.InkSoft)
            .clickable(onClick = onClick)
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        if (uploading) {
            CircularProgressIndicator(color = TKColor.Orange, modifier = Modifier.size(24.dp))
        } else if (done) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Check, "Centang", tint = TKColor.GreenDeep)
                Spacer(Modifier.width(TKSpacing.Xs))
                Text("Foto terunggah", color = TKColor.GreenDeep, fontWeight = FontWeight.Bold)
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CameraAlt, "Ambil foto", tint = TKColor.Orange)
                Spacer(Modifier.width(TKSpacing.Xs))
                Text(label, color = TKColor.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}
