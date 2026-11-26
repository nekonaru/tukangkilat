package id.tukangkilat.app.ui.auth

import id.tukangkilat.app.ui.theme.TKSpacing

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
import id.tukangkilat.app.data.api.BaseUrlInterceptor
import id.tukangkilat.app.data.local.ServerConfigManager
import id.tukangkilat.app.data.repository.AuthRepository
import kotlinx.coroutines.flow.first
import id.tukangkilat.app.ui.components.tkScreenEnter
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
    val locked: Boolean = false,
    val fieldError: Boolean = false
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverConfig: ServerConfigManager
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    suspend fun serverUrl(): String = serverConfig.current()

    fun saveServerUrl(url: String) {
        viewModelScope.launch {
            serverConfig.save(url)
            BaseUrlInterceptor.invalidate()
        }
    }

    fun onUsername(value: String) { _state.value = _state.value.copy(username = value, error = null) }
    fun onPassword(value: String) { _state.value = _state.value.copy(password = value, error = null) }
    fun togglePasswordVisibility() { _state.value = _state.value.copy(passwordVisible = !_state.value.passwordVisible) }

    fun login(onRole: (Role) -> Unit) {
        val current = _state.value
        if (current.username.isBlank() || current.password.isBlank()) {
            _state.value = current.copy(error = "Isi username dan password kamu")
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
                val wrongCredentials = message.contains("Username atau password salah")
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = when {
                        wrongCredentials -> "Username atau password salah"
                        else -> e.cleanApiError()
                    },
                    locked = locked,
                    fieldError = wrongCredentials || locked
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
    var serverUrl by remember { mutableStateOf("") }
    var showServerDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { serverUrl = viewModel.serverUrl() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .tkScreenEnter()
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
                Spacer(Modifier.width(TKSpacing.Xs))
                Text(
                    "Ahlinya rumah, siap membantu",
                    color = TKColor.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Masuk untuk panggil teknisi tepercaya ke rumah kamu.",
                color = TKColor.Mist,
                fontSize = 13.sp
            )
            Spacer(Modifier.height(34.dp))

            TextFieldDark(
                value = state.username,
                onValueChange = viewModel::onUsername,
                placeholder = "Username",
                isError = state.fieldError
            )
            Spacer(Modifier.height(12.dp))
            Box {
                TextFieldDark(
                    value = state.password,
                    onValueChange = viewModel::onPassword,
                    placeholder = "Password",
                    isError = state.fieldError,
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
                    Icon(Icons.Filled.Error, "Galat", tint = TKColor.Red, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(TKSpacing.Xs))
                    Text(state.error!!, color = TKColor.Red, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(Modifier.height(TKSpacing.Sm))
            TKPrimaryButton(
                "LOGIN",
                onClick = { viewModel.login(onLoggedIn) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading,
                loading = state.isLoading
            )

            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Belum punya akun?", color = TKColor.Mist, fontSize = 13.sp)
                TKTextButton("Daftar", onClick = onRegister)
            }
            Spacer(Modifier.height(6.dp))
            TKTextButton("Daftar sebagai tukang", onClick = onRegisterTechnician)
            Spacer(Modifier.height(6.dp))
            TKTextButton("Atur alamat server", onClick = { showServerDialog = true })
        }
    }

    if (showServerDialog) {
        ServerDialog(
            initial = serverUrl,
            onDismiss = { showServerDialog = false },
            onSave = { url ->
                viewModel.saveServerUrl(url)
                serverUrl = url
                showServerDialog = false
            }
        )
    }
}

@Composable
private fun ServerDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TKColor.InkSoft,
        title = { Text("Alamat server", color = TKColor.White, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Isi alamat backend yang bisa dijangka perangkat ini. Contoh: http://192.168.1.5:8080",
                    color = TKColor.Mist,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(10.dp))
                TextFieldDark(value = value, onValueChange = { value = it }, placeholder = "http://alamat:8080")
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kosongkan untuk kembali ke alamat bawaan emulator.",
                    color = TKColor.Mist,
                    fontSize = 11.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(value.trim()) }) { Text("Simpan", color = TKColor.Orange) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal", color = TKColor.Mist) }
        }
    )
}
