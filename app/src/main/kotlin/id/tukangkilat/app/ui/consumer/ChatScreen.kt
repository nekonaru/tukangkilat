package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
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
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.MessageDto
import id.tukangkilat.shared.WsEventType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ChatUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val messages: List<MessageDto> = emptyList(),
    val myUserId: Long? = null,
    val draft: String = ""
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val wsManager: WsManager,
    private val sessionManager: id.tukangkilat.app.data.local.SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    fun load(orderId: Long, technicianSide: Boolean) {
        viewModelScope.launch {
            try {
                val s = sessionManager.session.first()
                s.user?.let { u -> _state.value = _state.value.copy(myUserId = u.id) }
            } catch (_: Exception) {}
            refresh(orderId, technicianSide)
        }
        viewModelScope.launch {
            wsManager.events.collect { event ->
                if (event.type == WsEventType.CHAT_MESSAGE && event.orderId == orderId) {
                    refresh(orderId, technicianSide)
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(5_000)
                refresh(orderId, technicianSide)
            }
        }
    }

    private suspend fun refresh(orderId: Long, technicianSide: Boolean) {
        try {
            val messages = if (technicianSide) orderRepository.technicianMessages(orderId)
            else orderRepository.messages(orderId)
            _state.value = _state.value.copy(isLoading = false, messages = messages, error = null)
        } catch (e: Exception) {
            _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
        }
    }

    fun onDraft(value: String) { _state.value = _state.value.copy(draft = value) }

    fun send(orderId: Long, technicianSide: Boolean) {
        val body = _state.value.draft.trim()
        if (body.isBlank()) return
        _state.value = _state.value.copy(draft = "")
        viewModelScope.launch {
            try {
                if (technicianSide) orderRepository.technicianSendMessage(orderId, body)
                else orderRepository.sendMessage(orderId, body)
                refresh(orderId, technicianSide)
            } catch (e: Exception) {
                _state.value = _state.value.copy(draft = body, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun ChatScreen(
    orderId: Long,
    technicianSide: Boolean,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(orderId) { viewModel.load(orderId, technicianSide) }
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .imePadding()
    ) {
        ScreenHeader(if (technicianSide) "Pesan ke konsumen" else "Pesan ke teknisi", onBack = onBack)

        if (state.isLoading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = TKColor.Orange)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.messages, key = { it.id }) { message ->
                    val mine = message.senderId == state.myUserId
                    ChatBubble(message = message, mine = mine)
                }
                if (state.messages.isEmpty()) {
                    item {
                        Text(
                            "Belum ada pesan. Mulai obrolan.",
                            color = TKColor.Mist,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
            }
        }

        // composer
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TKColor.InkSoft)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraft,
                modifier = Modifier
                    .weight(1f)
                    .background(TKColor.Card, RoundedCornerShape(24.dp)),
                placeholder = { Text("Tulis pesan...", color = TKColor.CreamText) },
                shape = RoundedCornerShape(24.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = TKColor.Orange,
                    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedTextColor = TKColor.Ink,
                    unfocusedTextColor = TKColor.Ink,
                    cursorColor = TKColor.Orange
                )
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = { viewModel.send(orderId, technicianSide) },
                modifier = Modifier
                    .size(48.dp)
                    .background(TKColor.Orange, androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(Icons.Filled.Send, "Kirim", tint = TKColor.White)
            }
        }
    }
}

@Composable
private fun ChatBubble(message: MessageDto, mine: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (mine) TKColor.Orange else TKColor.Card)
                .padding(10.dp)
        ) {
            if (!mine) {
                Text(message.senderName, color = TKColor.CreamText, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
            }
            Text(message.body, color = if (mine) TKColor.White else TKColor.Ink, fontSize = 14.sp)
            Spacer(Modifier.height(4.dp))
            Text(
                formatTime(message.createdAt),
                color = if (mine) TKColor.White.copy(alpha = 0.8f) else TKColor.CreamText,
                fontSize = 10.sp
            )
        }
    }
}

private fun formatTime(iso: String): String =
    runCatching {
        OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("HH.mm"))
    }.getOrDefault("")
