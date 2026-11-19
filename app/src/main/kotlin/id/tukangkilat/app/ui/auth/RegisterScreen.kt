package id.tukangkilat.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.TKPrimaryButton
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.RegisterRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RegisterUiState(
    val fullName: String = "",
    val username: String = "",
    val phone: String = "",
    val address: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val done: Boolean = false
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(RegisterUiState())
    val state: StateFlow<RegisterUiState> = _state.asStateFlow()

    fun onChange(block: RegisterUiState.() -> RegisterUiState) {
        _state.value = _state.value.block().copy(error = null)
    }

    fun submit() {
        val s = _state.value
        when {
            s.fullName.isBlank() || s.username.isBlank() || s.phone.isBlank() ->
                _state.value = s.copy(error = "Nama, username, dan nomor HP wajib diisi")
            s.password.length < 8 ->
                _state.value = s.copy(error = "Password minimal 8 karakter")
            s.password != s.confirmPassword ->
                _state.value = s.copy(error = "Konfirmasi password tidak cocok")
            else -> {
                _state.value = s.copy(isLoading = true, error = null)
                viewModelScope.launch {
                    val result = authRepository.register(
                        RegisterRequest(
                            fullName = s.fullName.trim(),
                            username = s.username.trim(),
                            password = s.password,
                            phone = s.phone.trim(),
                            address = s.address.trim().ifBlank { null }
                        )
                    )
                    result.onSuccess {
                        _state.value = RegisterUiState(done = true)
                    }.onFailure { e ->
                        _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
                    }
                }
            }
        }
    }
}

@Composable
fun RegisterScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.done) {
        if (state.done) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader("Buat akun", onBack = onBack)
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(
                "Daftar untuk pesan teknisi tepercaya ke rumah Anda.",
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
            TextFieldDark(state.address, { v -> viewModel.onChange { copy(address = v) } }, placeholder = "Alamat awal (opsional)")
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
                TKPrimaryButton("Daftar", onClick = viewModel::submit)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
