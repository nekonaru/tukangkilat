package id.tukangkilat.app.ui.technician

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
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
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.data.repository.TechnicianRepository
import id.tukangkilat.app.ui.components.AvatarCircle
import id.tukangkilat.app.ui.components.ModeTukangChip
import id.tukangkilat.app.ui.components.RatingStars
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.TechnicianProfileDto
import id.tukangkilat.shared.ChangePasswordRequest
import id.tukangkilat.shared.TechnicianUpdateRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechAccountUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val profile: TechnicianProfileDto? = null,
    val radius: Int = 10,
    val skills: Set<String> = emptySet(),
    val saving: Boolean = false,
    val snackbar: String? = null,
    val reviews: List<id.tukangkilat.shared.ReviewDto> = emptyList()
)

@HiltViewModel
class TechAccountViewModel @Inject constructor(
    private val technicianRepository: TechnicianRepository,
    private val authRepository: AuthRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(TechAccountUiState())
    val state: StateFlow<TechAccountUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val profile = technicianRepository.profile()
                val reviews = runCatching { technicianRepository.reviews() }.getOrDefault(emptyList())
                _state.value = _state.value.copy(
                    isLoading = false,
                    profile = profile,
                    radius = profile.serviceRadiusKm,
                    skills = profile.skills.toSet(),
                    reviews = reviews,
                    error = null
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onRadius(value: Float) { _state.value = _state.value.copy(radius = value.toInt()) }
    fun toggleSkill(skill: String) {
        val s = _state.value
        val newSkills = if (skill in s.skills) s.skills - skill else s.skills + skill
        _state.value = s.copy(skills = newSkills)
    }

    fun saveProfile() {
        val s = _state.value
        viewModelScope.launch {
            _state.value = s.copy(saving = true)
            try {
                technicianRepository.updateProfile(TechnicianUpdateRequest(skills = s.skills.toList(), serviceRadiusKm = s.radius))
                _state.value = _state.value.copy(saving = false, snackbar = "Profil tersimpan")
                refresh()
            } catch (e: Exception) {
                _state.value = _state.value.copy(saving = false, snackbar = e.cleanApiError())
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

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    fun clearSnackbar() { _state.value = _state.value.copy(snackbar = null) }
}

private val SKILLS = listOf("LISTRIK" to "Listrik", "PIPA" to "Pipa", "AC" to "Servis AC")

@Composable
fun TechAccountScreen(
    onNotifications: () -> Unit,
    viewModel: TechAccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showPasswordDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let { snackbarHostState.showSnackbar(it); viewModel.clearSnackbar() }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.profile == null -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> {
            val profile = state.profile!!
            Scaffold(
                containerColor = TKColor.Ink,
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(TKColor.Ink)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Akun", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Spacer(Modifier.width(10.dp))
                        ModeTukangChip()
                    }

                    // profile header
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AvatarCircle(profile.fullName, size = 80)
                        Spacer(Modifier.height(10.dp))
                        Text(profile.fullName, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("@${profile.username} · ${profile.phone}", color = TKColor.Mist, fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        RatingStars(profile.ratingAvg)
                        Text(
                            "${profile.jobsCount} pekerjaan selesai",
                            color = TKColor.Mist,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        VerificationChip(profile.verificationStatus)
                    }
                    Spacer(Modifier.height(20.dp))

                    // Ulasan diterima
                    if (state.reviews.isNotEmpty()) {
                        Text(
                            "Ulasan saya (${state.reviews.size})",
                            color = TKColor.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            state.reviews.take(3).forEach { review ->
                                ReviewCard(review)
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // edit skills
                    Text("Keahlian", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SKILLS.forEach { (key, label) ->
                            val selected = key in state.skills
                            Text(
                                label,
                                color = if (selected) TKColor.White else TKColor.Mist,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) TKColor.Orange else TKColor.InkSoft)
                                    .clickable { viewModel.toggleSkill(key) }
                                    .padding(horizontal = 14.dp, vertical = 9.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))

                    Text("Radius layanan: ${state.radius} km", color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 20.dp))
                    Slider(
                        value = state.radius.toFloat(),
                        onValueChange = viewModel::onRadius,
                        valueRange = 1f..50f,
                        steps = 48,
                        modifier = Modifier.padding(horizontal = 20.dp),
                        colors = SliderDefaults.colors(thumbColor = TKColor.Orange, activeTrackColor = TKColor.Orange, inactiveTrackColor = TKColor.InkLine)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (state.saving) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TKColor.Orange)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.saveProfile() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TKColor.Orange)
                        ) {
                            Text("Simpan perubahan", fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))

                    AccountRow(Icons.Filled.Lock, "Ubah password") { showPasswordDialog = true }
                    AccountRow(Icons.Filled.Notifications, "Notifikasi") { onNotifications() }
                    Spacer(Modifier.height(8.dp))
                    AccountRow(Icons.Filled.Logout, "Keluar", danger = true) { viewModel.logout() }
                    Spacer(Modifier.height(28.dp))
                }
            }
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSubmit = { old, new -> viewModel.changePassword(old, new) { showPasswordDialog = false } }
        )
    }
}

@Composable
private fun ReviewCard(review: id.tukangkilat.shared.ReviewDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TKColor.InkSoft)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (i in 1..5) {
                    Icon(
                        if (i <= review.stars) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = null,
                        tint = TKColor.Orange,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(review.orderCode, color = TKColor.Mist, fontSize = 11.sp)
        }
        if (review.tags.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(review.tags.joinToString(" · "), color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        if (review.comment.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(review.comment, color = TKColor.White, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(review.createdAt.take(10), color = TKColor.Mist, fontSize = 10.sp)
    }
}

@Composable
private fun VerificationChip(status: String) {
    val (bg, fg, text) = when (status) {
        "TERVERIFIKASI" -> Triple(TKColor.GreenSoft, TKColor.GreenDeep, "Terverifikasi")
        "MENUNGGU_VERIFIKASI" -> Triple(TKColor.Cream, TKColor.CreamText, "Menunggu verifikasi")
        "DITOLAK" -> Triple(TKColor.RedSoft, TKColor.RedDeep, "Ditolak")
        else -> Triple(TKColor.RedSoft, TKColor.RedDeep, "Ditangguhkan")
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AccountRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, danger: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (danger) TKColor.Red else TKColor.Orange)
        Spacer(Modifier.width(16.dp))
        Text(label, color = if (danger) TKColor.Red else TKColor.White, fontWeight = FontWeight.Medium, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.ChevronRight, null, tint = TKColor.InkLine)
    }
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onSubmit: (String, String) -> Unit) {
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
                TextFieldDark(oldPassword, { oldPassword = it }, placeholder = "Password lama", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Spacer(Modifier.height(10.dp))
                TextFieldDark(newPassword, { newPassword = it }, placeholder = "Password baru (min 8)", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                Spacer(Modifier.height(10.dp))
                TextFieldDark(confirm, { confirm = it }, placeholder = "Ulangi password baru", visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
                error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = TKColor.Red, fontSize = 13.sp) }
            }
        }
    )
}
