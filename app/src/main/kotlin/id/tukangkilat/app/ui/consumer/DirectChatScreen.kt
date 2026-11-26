package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.local.SessionManager
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DirectChatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val messages: List<DirectMessageDto> = emptyList(),
    val draft: String = "",
    val myUserId: Long = -1L,
    val services: List<ServiceDto> = emptyList(),
    val proposalDescription: String = "",
    val proposalPrice: String = "",
    val proposalDate: String = "",
    val proposalTime: String = "",
    val selectedServiceId: Long? = null,
    val notice: String? = null
)

@HiltViewModel
class DirectChatViewModel @Inject constructor(
    private val api: ApiService,
    private val sessionManager: SessionManager
) : ViewModel() {
    private val _state = MutableStateFlow(DirectChatUiState())
    val state: StateFlow<DirectChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { sessionManager.session.first().user?.id }
                .getOrNull()?.let { id -> _state.value = _state.value.copy(myUserId = id) }
        }
    }

    fun load(peerId: Long, technicianSide: Boolean) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val messages = if (technicianSide) api.technicianDirectMessages(peerId)
                else api.consumerMessages(peerId)
                val services = if (technicianSide) runCatching { api.services() }.getOrDefault(emptyList()) else emptyList()
                _state.value = _state.value.copy(
                    isLoading = false,
                    messages = messages,
                    services = services,
                    selectedServiceId = _state.value.selectedServiceId ?: services.firstOrNull()?.id
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun onDraft(value: String) { _state.value = _state.value.copy(draft = value) }

    fun send(peerId: Long, technicianSide: Boolean) {
        val body = _state.value.draft.trim()
        if (body.isBlank()) return
        _state.value = _state.value.copy(draft = "")
        viewModelScope.launch {
            try {
                if (technicianSide) api.sendTechnicianMessage(peerId, SendDirectMessageRequest(body))
                else api.sendConsumerMessage(peerId, SendDirectMessageRequest(body))
                load(peerId, technicianSide)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.cleanApiError(), draft = body)
            }
        }
    }

    fun onProposal(field: String, value: String) {
        _state.value = when (field) {
            "description" -> _state.value.copy(proposalDescription = value)
            "price" -> _state.value.copy(proposalPrice = value)
            "date" -> _state.value.copy(proposalDate = value)
            "time" -> _state.value.copy(proposalTime = value)
            else -> _state.value
        }
    }

    fun selectService(id: Long) { _state.value = _state.value.copy(selectedServiceId = id) }

    fun submitProposal(peerId: Long) {
        val s = _state.value
        val price = s.proposalPrice.toLongOrNull()
        val serviceId = s.selectedServiceId
        if (price == null || price <= 0) {
            _state.value = s.copy(notice = "Isi harga dengan angka")
            return
        }
        if (serviceId == null) {
            _state.value = s.copy(notice = "Pilih layanan")
            return
        }
        if (s.proposalDescription.isBlank() || s.proposalDate.isBlank()) {
            _state.value = s.copy(notice = "Isi masalah dan tanggal")
            return
        }
        viewModelScope.launch {
            try {
                api.createProposal(
                    CreateProposalRequest(
                        consumerId = peerId,
                        serviceId = serviceId,
                        description = s.proposalDescription,
                        price = price,
                        scheduledDate = s.proposalDate,
                        customTime = s.proposalTime.ifBlank { null }
                    )
                )
                _state.value = _state.value.copy(
                    notice = "Penawaran dikirim",
                    proposalDescription = "",
                    proposalPrice = ""
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectChatScreen(
    peerId: Long,
    technicianSide: Boolean,
    onBack: () -> Unit,
    viewModel: DirectChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var showProposal by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(peerId, technicianSide) { viewModel.load(peerId, technicianSide) }
    LaunchedEffect(state.notice) {
        state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TKTopAppBar(
                if (technicianSide) "Chat konsumen" else "Chat teknisi",
                onBack = onBack,
                showBell = false
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).tkScreenEnter()) {
            if (state.error != null && state.messages.isEmpty()) {
                ErrorView(message = state.error ?: "Ada kendala sebentar", onRetry = { viewModel.load(peerId, technicianSide) })
            } else {
                LazyColumn(
                    Modifier.fillMaxSize().weight(1f).padding(horizontal = TKSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatBubble(
                            text = message.body,
                            time = message.createdAt.takeLast(8).take(5),
                            mine = message.senderId == state.myUserId
                        )
                    }
                }
                if (technicianSide) {
                    TextButton(onClick = { showProposal = true }) {
                        Text("Ajukan penawaran harga", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(Modifier.padding(TKSpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                    TKOutlinedField(
                        value = state.draft,
                        onValueChange = viewModel::onDraft,
                        label = "Tulis pesan",
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.send(peerId, technicianSide) }) {
                        Icon(Icons.Filled.Send, "Kirim", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    if (showProposal) {
        AlertDialog(
            onDismissRequest = { showProposal = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.submitProposal(peerId)
                    showProposal = false
                }) { Text("Kirim penawaran") }
            },
            dismissButton = { TextButton(onClick = { showProposal = false }) { Text("Batal") } },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text("Ajukan harga", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(TKSpacing.Xs))
                    state.services.forEach { service ->
                        ChoiceFilterChip(
                            label = service.name,
                            selected = service.id == state.selectedServiceId,
                            onClick = { viewModel.selectService(service.id) }
                        )
                    }
                    TKOutlinedField(
                        value = state.proposalDescription,
                        onValueChange = { viewModel.onProposal("description", it) },
                        label = "Permasalahan"
                    )
                    TKOutlinedField(
                        value = state.proposalPrice,
                        onValueChange = { viewModel.onProposal("price", it) },
                        label = "Harga penawaran (Rp)"
                    )
                    TKOutlinedField(
                        value = state.proposalDate,
                        onValueChange = { viewModel.onProposal("date", it) },
                        label = "Tanggal (YYYY-MM-DD)"
                    )
                    TKOutlinedField(
                        value = state.proposalTime,
                        onValueChange = { viewModel.onProposal("time", it) },
                        label = "Jam (opsional, HH:mm)"
                    )
                }
            }
        )
    }
}
