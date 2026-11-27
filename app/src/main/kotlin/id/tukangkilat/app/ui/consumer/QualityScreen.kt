package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QualityUiState(
    val operations: List<SyncOperationDto> = emptyList(),
    val keys: List<ChatKeyDto> = emptyList(),
    val widgets: List<WidgetConfigDto> = emptyList(),
    val entity: String = "",
    val payload: String = "",
    val widgetType: String = "PESANAN_AKTIF",
    val notice: String? = null
)

@HiltViewModel
class QualityViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(QualityUiState())
    val state: StateFlow<QualityUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            try {
                _state.value = QualityUiState(
                    operations = runCatching { api.syncOperations() }.getOrDefault(emptyList()),
                    keys = runCatching { api.chatKeys() }.getOrDefault(emptyList()),
                    widgets = runCatching { api.widgets() }.getOrDefault(emptyList()),
                    entity = _state.value.entity, payload = _state.value.payload,
                    widgetType = _state.value.widgetType, notice = _state.value.notice
                )
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun field(name: String, value: String) {
        when (name) {
            "entity" -> _state.value = _state.value.copy(entity = value)
            "payload" -> _state.value = _state.value.copy(payload = value)
        }
    }

    fun queue() {
        val s = _state.value
        if (s.entity.isBlank()) { _state.value = s.copy(notice = "Isi entity"); return }
        viewModelScope.launch {
            try {
                api.queueSync(QueueSyncRequest(s.entity, "1", "UPDATE", s.payload))
                _state.value = s.copy(notice = "Operasi diantrikan", payload = "")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun process() {
        viewModelScope.launch {
            try {
                val result = api.processSync()
                _state.value = _state.value.copy(notice = "Sinkron: ${result.processed}, konflik: ${result.conflicts}")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun generateKey() {
        viewModelScope.launch {
            try {
                val key = api.generateKey(GenerateKeyRequest("demo-public-key-${System.currentTimeMillis()}"))
                _state.value = _state.value.copy(notice = "Sidik jari ${key.fingerprint}")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun toggleWidget(type: String, enabled: Boolean) {
        viewModelScope.launch {
            try { api.saveWidget(SaveWidgetRequest(type, !enabled, 0)); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@Composable
fun QualityScreen(onBack: () -> Unit, viewModel: QualityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Kualitas Platform", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionLabel("Sinkronisasi offline")
            TKOutlinedField(state.entity, { viewModel.field("entity", it) }, label = "Entity")
            TKOutlinedField(state.payload, { viewModel.field("payload", it) }, label = "Payload")
            TKButton("Antrikan operasi", viewModel::queue)
            TKButton("Proses sinkronisasi", viewModel::process, variant = TKButtonVariant.Tonal)
            state.operations.forEach { op ->
                Text("${op.entity} · ${op.status} · konflik ${op.conflict}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(TKSpacing.Sm))
            SectionLabel("Kunci chat simulasi")
            TKButton("Buat kunci", viewModel::generateKey, variant = TKButtonVariant.Outlined)
            state.keys.forEach { Text("Sidik jari ${it.fingerprint} · aktif ${it.active}", style = MaterialTheme.typography.bodySmall) }
            Spacer(Modifier.height(TKSpacing.Sm))
            SectionLabel("Widget")
            state.widgets.forEach { widget ->
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = widget.enabled, onCheckedChange = { viewModel.toggleWidget(widget.widgetType, widget.enabled) })
                    Text(widget.widgetType)
                }
            }
        }
    }
}
