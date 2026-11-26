package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import id.tukangkilat.app.data.repository.NotificationRepository
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.tkScreenEnter
import id.tukangkilat.app.ui.theme.TKColor
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    val repository: NotificationRepository
) : ViewModel() {
    fun markAllRead() = viewModelScope.launch { repository.markAllRead() }
    fun clear() = viewModelScope.launch { repository.clear() }
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel()
) {
    val items by viewModel.repository.notifications.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .tkScreenEnter()
    ) {
        ScreenHeader("Notifikasi", onBack = onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TextButton(onClick = { viewModel.markAllRead() }) {
                Text("Tandai dibaca", color = TKColor.Orange)
            }
            TextButton(onClick = { viewModel.clear() }) {
                Text("Hapus semua", color = TKColor.Mist)
            }
        }
        if (items.isEmpty()) {
            EmptyView(
                title = "Belum ada notifikasi",
                subtitle = "Perubahan pesanan dan tawaran akan muncul di sini.",
                icon = { androidx.compose.material3.Icon(Icons.Filled.Notifications, "Notifikasi", tint = TKColor.Mist) }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    NotificationCard(item)
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(item: id.tukangkilat.app.data.repository.AppNotification) {
    val time = SimpleDateFormat("dd MMM HH:mm", Locale.forLanguageTag("id")).format(Date(item.createdAt))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (item.read) TKColor.InkSoft else TKColor.Cream)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Filled.Notifications,
            "Notifikasi",
            tint = if (item.read) TKColor.Mist else TKColor.Orange,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = if (item.read) TKColor.White else TKColor.Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(item.body, color = if (item.read) TKColor.Mist else TKColor.CreamText, fontSize = 12.sp)
            Text(time, color = TKColor.Mist, fontSize = 11.sp)
        }
    }
}
