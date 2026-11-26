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
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.shared.Format
import id.tukangkilat.shared.OrderSummaryDto
import id.tukangkilat.shared.ProposalDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatListUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val orders: List<OrderSummaryDto> = emptyList(),
    val proposals: List<ProposalDto> = emptyList()
)

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val orderRepository: OrderRepository,
    private val api: ApiService
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val orders = orderRepository.myOrders()
                val proposals = runCatching { api.consumerProposals() }.getOrDefault(emptyList())
                _state.value = ChatListUiState(isLoading = false, orders = orders, proposals = proposals)
            } catch (e: Exception) {
                _state.value = ChatListUiState(isLoading = false, error = e.message ?: "Gagal memuat percakapan")
            }
        }
    }

    fun acceptProposal(id: Long) {
        viewModelScope.launch {
            runCatching { api.acceptProposal(id) }
            refresh()
        }
    }

    fun rejectProposal(id: Long) {
        viewModelScope.launch {
            runCatching { api.rejectProposal(id) }
            refresh()
        }
    }
}

@Composable
fun ChatListScreen(
    onOpenChat: (Long) -> Unit,
    onOpenTechnicianChat: (Long) -> Unit,
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
                item { SectionLabel("Penawaran teknisi") }
                items(state.proposals, key = { it.id }) { proposal ->
                    Column(
                        Modifier.fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
                            .clickable { onOpenTechnicianChat(proposal.technicianId) }
                            .padding(TKSpacing.Sm)
                    ) {
                        Text(
                            "${proposal.technicianName} · ${Format.rupiah(proposal.price)}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(proposal.serviceName, style = MaterialTheme.typography.bodySmall)
                        Text(proposal.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        if (proposal.status == "MENUNGGU") {
                            Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                                TKButton("Terima", { viewModel.acceptProposal(proposal.id) }, modifier = Modifier.weight(1f))
                                TKButton(
                                    "Tolak",
                                    { viewModel.rejectProposal(proposal.id) },
                                    modifier = Modifier.weight(1f),
                                    variant = TKButtonVariant.Outlined
                                )
                            }
                        } else {
                            Text("Status: ${proposal.status}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item { SectionLabel("Percakapan pesanan") }
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
