package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import id.tukangkilat.app.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.BaseUrlInterceptor
import id.tukangkilat.app.data.repository.SystemRepository
import id.tukangkilat.app.data.local.ServerConfigManager
import id.tukangkilat.app.data.local.UiPreferencesManager
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val serverUrl: String = "",
    val message: String? = null,
    val connection: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val uiPreferences: UiPreferencesManager,
    private val serverConfig: ServerConfigManager,
    private val systemRepository: SystemRepository
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            uiPreferences.themeMode.collect { mode -> _state.value = _state.value.copy(themeMode = mode) }
        }
        viewModelScope.launch {
            serverConfig.serverUrl.collect { url -> _state.value = _state.value.copy(serverUrl = url) }
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { uiPreferences.setThemeMode(mode) }

    fun setServer(url: String) = viewModelScope.launch {
        serverConfig.save(url)
        BaseUrlInterceptor.invalidate()
        _state.value = _state.value.copy(message = "Alamat server disimpan")
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun checkConnection() = viewModelScope.launch {
        _state.value = _state.value.copy(connection = "Memeriksa...")
        _state.value = _state.value.copy(
            connection = runCatching { systemRepository.health() }
                .map { "Terhubung: $it" }
                .getOrDefault("Tidak terhubung ke server")
        )
    }
}

@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var serverInput by remember(state.serverUrl) { mutableStateOf(state.serverUrl) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface)
        ) {
            TKTopAppBar("Pengaturan", onBack = onBack, showBell = false)
            Column(Modifier.padding(TKSpacing.Sm), verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                SectionLabel("Tampilan")
                ThemeOption("Mengikuti sistem", ThemeMode.System, state.themeMode) { viewModel.setTheme(it) }
                ThemeOption("Terang", ThemeMode.Light, state.themeMode) { viewModel.setTheme(it) }
                ThemeOption("Gelap", ThemeMode.Dark, state.themeMode) { viewModel.setTheme(it) }

                Spacer(Modifier.height(TKSpacing.Sm))
                SectionLabel("Server")
                TKOutlinedField(
                    value = serverInput,
                    onValueChange = { serverInput = it },
                    label = "Alamat server",
                    placeholder = "Kosongkan untuk backend di dalam aplikasi"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                    TKButton("Simpan", { viewModel.setServer(serverInput.trim()) }, modifier = Modifier.weight(1f))
                    TKButton(
                        "Pakai lokal",
                        { serverInput = ""; viewModel.setServer("") },
                        modifier = Modifier.weight(1f),
                        variant = TKButtonVariant.Tonal
                    )
                }

                Spacer(Modifier.height(TKSpacing.Xs))
                TKButton("Cek koneksi server", { viewModel.checkConnection() }, variant = TKButtonVariant.Outlined)
                state.connection?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(Modifier.height(TKSpacing.Sm))
                SectionLabel("Bantuan")
                FaqItem("Bagaimana cara memesan teknisi?", "Pilih layanan di Beranda, isi detail dan alamat, lalu bayar. Teknisi akan diispatch otomatis.")
                FaqItem("Teknisi tidak ditemukan?", "Pastikan HP teknisi online, izin lokasi aktif, dan kedua perangkat memakai server yang sama.")
                FaqItem("Bagaimana memakai server LAN?", "Jalankan start-lan-server.bat di laptop, lalu isi alamat http://IP_LAPTOP:8080 di layar ini.")

                Spacer(Modifier.height(TKSpacing.Sm))
                SectionLabel("Tentang")
                Text(
                    "Tukang Kilat v${BuildConfig.VERSION_NAME}. Tampilan mengikuti design system Figma.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(TKSpacing.Md))
            }
        }
    }
}


@Composable
private fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch)
            .background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
            .clickable { expanded = !expanded }.padding(horizontal = TKSpacing.Sm, vertical = TKSpacing.Xs)
    ) {
        Text(question, style = MaterialTheme.typography.titleSmall)
        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Text(answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Spacer(Modifier.height(TKSpacing.Xs))
}

@Composable
private fun ThemeOption(label: String, mode: ThemeMode, selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch)
            .background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
            .clickable { onSelect(mode) }.padding(horizontal = TKSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = mode == selected, onClick = { onSelect(mode) })
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (mode == selected) {
            Icon(Icons.Filled.Check, "Terpilih", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
