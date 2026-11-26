package id.tukangkilat.app.ui.technician

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechnicianChatsUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val peers: List<Long> = emptyList()
)

@HiltViewModel
class TechnicianChatsViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(TechnicianChatsUiState())
    val state: StateFlow<TechnicianChatsUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                _state.value = TechnicianChatsUiState(isLoading = false, peers = api.directPeers())
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun TechnicianChatsScreen(onOpenChat: (Long) -> Unit, onBack: () -> Unit, viewModel: TechnicianChatsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { TKTopAppBar("Percakapan", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).tkScreenEnter()) {
            when {
                state.isLoading -> SkeletonCard()
                state.error != null && state.peers.isEmpty() ->
                    ErrorView(message = state.error ?: "Ada kendala sebentar", onRetry = viewModel::refresh)
                state.peers.isEmpty() -> StateView(
                    kind = StateViewKind.Empty,
                    title = "Belum ada percakapan",
                    description = "Percakapan dengan konsumen akan muncul di sini."
                )
                else -> LazyColumn(
                    Modifier.fillMaxSize().padding(horizontal = TKSpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(TKSpacing.Xs)
                ) {
                    items(state.peers, key = { it }) { peer ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = TKSpacing.MinTouch)
                                .background(MaterialTheme.colorScheme.surfaceContainer, TKShape.Card)
                                .clickable { onOpenChat(peer) }
                                .padding(TKSpacing.Sm)
                        ) {
                            Text("Konsumen #$peer", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
