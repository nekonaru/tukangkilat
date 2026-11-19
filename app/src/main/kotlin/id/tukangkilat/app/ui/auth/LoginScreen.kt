package id.tukangkilat.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.ui.components.TKPrimaryButton
import id.tukangkilat.app.ui.components.TKTextButton
import id.tukangkilat.app.ui.components.TKWordmark
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val password: String = "",
    val passwordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val locked: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsername(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun onPassword(value: String) { _state.value = _state.value.copy(password = value, error = null) }
    fun togglePasswordVisibility() { _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible) }

    fun login(onRole: (Role) -> Unit) {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "Isi username dan password Anda")
            return
        }
        _state.value = current.copy(isLoading = true, error = null, locked = false)
        viewModelScope.launch {
            val result = authRepository.login(current.username.trim(), current.password)
            result.onSuccess { response ->
                _state.value = LoginUiState()
                onRole(response.role)
            }.onFailure { e ->
                val message = e.message.orEmpty()
                val locked = message.contains("dikunci", ignoreCase = true)
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = when {
                        message.contains("Username atau password salah") -> "Username atau password salah"
                        message.isNotBlank() -> e.cleanApiError()
                        else -> "Tidak bisa terhubung ke server. Periksa koneksi Anda"
                    },
                    locked = locked
                )
            }
        }
    }
}

@Composable
fun LoginScreen(
    onLoggedIn: (Role) -> Unit,
    onRegister: () -> Unit,
    onRegisterTechnician: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Bolt,
                    contentDescription = null,
                    tint = TKColor.Orange,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(Modifier.width(10.dp))
                TKWordmark(fontSize = 26.sp)
            }
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.VerifiedUser,
                    contentDescription = null,
                    tint = TKColor.Mist,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Ahlinya rumah, siap membantu",
                    color = TKColor.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Masuk untuk panggil teknisi tepercaya ke rumah Anda.",
                color = TKColor.Mist,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(34.dp))

            TextFieldDark(
                value = state.username,
                onValueChange = viewModel::onUsername,
                placeholder = "Username",
                isError = state.error != null
            )
            Spacer(Modifier.height(12.dp))
            Box {
                TextFieldDark(
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                    placeholder = "Password",
                    isError = state.error != null,
                    visualTransformation = if (state.passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        autoCorrect = false
                    )
                )
                IconButton(
                    onClick = viewModel::togglePasswordVisibility,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp)
                ) {
                    Icon(
                        if (state.passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = "Tampilkan password",
                        tint = TKColor.Ink.copy(alpha = 0.6f)
                    )
                }
            }

            if (state.error != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Error, null, tint = TKColor.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(state.error!!, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(16.dp))
            if (state.isLoading) {
                CircularProgressIndicator(color = TKColor.Orange)
            } else {
                TKPrimaryButton("LOGIN", onClick = { viewModel.login(onLoggedIn) })
            }

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Belum punya akun?", color = TKColor.Mist, fontSize = 13.sp)
                TKTextButton("Daftar", onClick = onRegister)
            }
            Spacer(Modifier.height(6.dp))
            TKTextButton("Daftar sebagai tukang", onClick = onRegisterTechnician)
        }
    }
}
