package id.tukangkilat.app.ui.consumer

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.OrderRepository
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.OrderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReceiptUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val order: OrderDto? = null
)

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    private val orderRepository: OrderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(ReceiptUiState())
    val state = _state.asStateFlow()

    fun load(orderId: Long) {
        viewModelScope.launch {
            try {
                val order = orderRepository.detail(orderId)
                _state.value = ReceiptUiState(isLoading = false, order = order)
            } catch (e: Exception) {
                _state.value = ReceiptUiState(isLoading = false, error = e.cleanApiError())
            }
        }
    }
}

@Composable
fun ReceiptScreen(
    orderId: Long,
    onBack: () -> Unit,
    onReorder: (Long) -> Unit = {},
    viewModel: ReceiptViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(orderId) { viewModel.load(orderId) }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.order == null -> ErrorView(message = state.error!!, onRetry = { viewModel.load(orderId) })
        else -> ReceiptContent(order = state.order!!, onBack = onBack, onReorder = onReorder)
    }
}
