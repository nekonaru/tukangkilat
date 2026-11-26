package id.tukangkilat.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.local.ServerConfigManager
import id.tukangkilat.app.data.local.SessionManager
import id.tukangkilat.app.data.ws.WsManager
import id.tukangkilat.shared.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SessionUiState(
    val isLoggedIn: Boolean,
    val role: Role?,
    val fullName: String?
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val wsManager: WsManager,
    private val serverConfig: ServerConfigManager,
    // Injected at app start so the notification center starts collecting WS events immediately.
    notificationRepository: id.tukangkilat.app.data.repository.NotificationRepository
) : ViewModel() {

    private val _session = MutableStateFlow(SessionUiState(false, null, null))
    val session: StateFlow<SessionUiState> = _session.asStateFlow()
    private val _serverUrl = MutableStateFlow("http://127.0.0.1:8080")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    init {
        viewModelScope.launch {
            sessionManager.session.collect { s ->
                _session.value = SessionUiState(s.isLoggedIn, s.role, s.user?.fullName)
            }
        }
    }

    init {
        viewModelScope.launch {
            serverConfig.serverUrl.collect { _serverUrl.value = it }
        }
    }

    fun startWs() = wsManager.start()
    fun stopWs() = wsManager.stop()
}
