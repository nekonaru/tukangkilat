package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.shared.OrderSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val orders: List<OrderSummaryDto> = emptyList()
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val orders = orderRepository.myOrders()
                _state.value = ChatListUiState(isLoading = false, orders = orders)
            } catch (e: Exception) {
                _state.value = ChatListUiState(isLoading = false, error = e.message ?: "Gagal memuat percakapan")
            }
        }
    }
}

@Composable
fun ChatListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenNotifications: () -> Unit,
    onBack: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TKTopAppBar("Chat", onBack = onBack, onBell = onOpenNotifications)
        when {
            state.isLoading -> {
                repeat(3) { SkeletonCard() }
            }
            state.error != null && state.orders.isEmpty() ->
                ErrorView(message = state.error ?: "Ada kendala sebentar", onRetry = viewModel::refresh)
            state.orders.isEmpty() -> StateView(
                kind = StateViewKind.Empty,
                title = "Belum ada percakapan",
                description = "Percakapan dengan teknisi akan muncul di sini."
            )
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)
            ) {
                items(state.orders, key = { it.id }) { order ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onOpenChat(order.id) }
                            .background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
                            .padding(TKSpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Chat, contentDescription = "Chat", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(TKSpacing.Sm))
                        Column(Modifier.weight(1f)) {
                            Text(order.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(order.serviceName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        StatusChip(order.status)
                    }
                }
            }
        }
    }
}
