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
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.shared.ContentDto
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
    val connection: String? = null,
    val serverInfo: Map<String, String> = emptyMap(),
    val faq: ContentDto? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val uiPreferences: UiPreferencesManager,
    private val serverConfig: ServerConfigManager,
    private val systemRepository: SystemRepository,
    private val api: ApiService
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
        _state.value = _state.value.copy(
            message = "Alamat server disimpan", serverInfo = emptyMap(), faq = null, connection = null
        )
    }

    fun clearMessage() { _state.value = _state.value.copy(message = null) }

    fun loadServerInfo() = viewModelScope.launch {
        try {
            _state.value = _state.value.copy(serverInfo = api.serverInfo(), message = "Info server dimuat")
        } catch (e: Exception) {
            _state.value = _state.value.copy(message = "Info server gagal dimuat")
        }
    }

    fun loadFaq() = viewModelScope.launch {
        try {
            _state.value = _state.value.copy(faq = api.publicContent("faq"))
        } catch (e: Exception) {
            _state.value = _state.value.copy(message = "FAQ server tidak tersedia")
        }
    }

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
    var showFaq by remember { mutableStateOf(false) }

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
                TKButton("Muat info server", viewModel::loadServerInfo, variant = TKButtonVariant.Outlined)
                state.serverInfo.forEach { (key, value) ->
                    Text("$key: $value", style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(TKSpacing.Sm))
                SectionLabel("Bantuan")
                TKButton("Muat FAQ dari server", { viewModel.loadFaq(); showFaq = true }, variant = TKButtonVariant.Tonal)
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

    if (showFaq) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showFaq = false },
            confirmButton = { TextButton(onClick = { showFaq = false }) { Text("Tutup") } },
            title = { Text(state.faq?.title ?: "FAQ") },
            text = { Text(state.faq?.body ?: "Memuat...") }
        )
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
