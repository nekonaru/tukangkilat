package id.tukangkilat.app.ui.admin

import id.tukangkilat.app.ui.theme.TKShape
import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tukangkilat.app.data.local.DatabaseTransfer
import id.tukangkilat.app.data.local.SecureBackup
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.repository.AdminRepository
import id.tukangkilat.app.data.local.LocalBackend
import id.tukangkilat.app.data.local.SessionManager
import id.tukangkilat.app.data.repository.AuthRepository
import id.tukangkilat.app.ui.components.tkPressScale
import id.tukangkilat.app.ui.components.tkScreenEnter
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.SectionLabel
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.AdminSummaryDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class AdminUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val summary: AdminSummaryDto? = null,
    val statusCounts: Map<String, Int> = emptyMap(),
    val dailyCounts: List<Pair<String, Int>> = emptyList()
)

@HiltViewModel
class AdminViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _state = MutableStateFlow(AdminUiState())
    val state: StateFlow<AdminUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val summaryDeferred = async { adminRepository.summary() }
                val analyticsDeferred = async { runCatching { adminRepository.analytics() }.getOrNull() }
                val summary = summaryDeferred.await()
                val analytics = analyticsDeferred.await()
                val fallbackOrders = if (analytics == null) {
                    runCatching { adminRepository.orders(null) }.getOrDefault(emptyList())
                } else emptyList()
                val today = java.time.LocalDate.now()
                val daily = if (analytics != null) {
                    analytics.dates.mapIndexed { index, date ->
                        date.takeLast(2) to analytics.dailyCounts.getOrElse(index) { 0 }
                    }
                } else {
                    (6 downTo 0).map { offset ->
                        val date = today.minusDays(offset.toLong())
                        date.dayOfMonth.toString() to fallbackOrders.count { it.createdAt.take(10) == date.toString() }
                    }
                }
                _state.value = AdminUiState(
                    isLoading = false,
                    summary = summary,
                    statusCounts = analytics?.statusCounts
                        ?: fallbackOrders.groupingBy { it.status.name }.eachCount(),
                    dailyCounts = daily
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.cleanApiError())
            }
        }
    }

    fun logout() {
        viewModelScope.launch { authRepository.logout() }
    }

    /** Clear the session through DataStore after a database import. */
    suspend fun clearSession() {
        sessionManager.clear()
    }

}

@Composable
fun AdminSummaryScreen(
    onVerify: () -> Unit,
    onReports: () -> Unit,
    onOrders: () -> Unit,
    onServices: () -> Unit,
    onPromo: () -> Unit,
    onNotifications: () -> Unit,
    onControl: () -> Unit = {},
    viewModel: AdminViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var backupMode by remember { mutableStateOf<String?>(null) }
    var backupPassphrase by remember { mutableStateOf("") }
    var firstResume by remember { mutableStateOf(true) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (firstResume) firstResume = false else viewModel.refresh()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            transferMessage = try {
                context.contentResolver.openOutputStream(uri)?.use { DatabaseTransfer.export(context, it) }
                    ?: error("File tidak bisa dibuka")
                "Ekspor database berhasil"
            } catch (e: Exception) {
                "Ekspor gagal: ${e.message}"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(Dispatchers.IO) {
            transferMessage = try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("File tidak bisa dibuka")
                LocalBackend.stopForImport()
                try {
                    stream.use { DatabaseTransfer.import(context, it) }
                } finally {
                    LocalBackend.restartAfterImport(context.applicationContext)
                }
                viewModel.clearSession()
                "Impor database berhasil. Silakan masuk ulang."
            } catch (e: Exception) {
                "Impor gagal: ${e.message}"
            }
        }
    }

    val secureExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val passphrase = backupPassphrase
        backupPassphrase = ""
        if (passphrase.length < 8) {
            transferMessage = "Kata sandi minimal 8 karakter"
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            transferMessage = try {
                context.contentResolver.openOutputStream(uri)?.use {
                    SecureBackup.encrypt(context, passphrase, it)
                } ?: error("File tidak bisa dibuka")
                "Cadangan terenkripsi berhasil dibuat"
            } catch (e: Exception) {
                "Cadangan gagal: ${e.message}"
            }
        }
    }

    val secureImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val passphrase = backupPassphrase
        backupPassphrase = ""
        if (passphrase.length < 8) {
            transferMessage = "Kata sandi minimal 8 karakter"
            return@rememberLauncherForActivityResult
        }
        scope.launch(Dispatchers.IO) {
            transferMessage = try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: error("File tidak bisa dibuka")
                LocalBackend.stopForImport()
                try {
                    stream.use { SecureBackup.decrypt(context, passphrase, it) }
                } finally {
                    LocalBackend.restartAfterImport(context.applicationContext)
                }
                viewModel.clearSession()
                "Cadangan dipulihkan. Silakan masuk ulang."
            } catch (e: Exception) {
                "Pemulihan gagal: ${e.message}"
            }
        }
    }

    when {
        state.isLoading -> LoadingView()
        state.error != null && state.summary == null -> ErrorView(message = state.error!!, onRetry = { viewModel.refresh() })
        else -> {
            val summary = state.summary!!
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TKColor.Ink)
                    .tkScreenEnter()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Panel Admin", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("Mode Admin", color = TKColor.Orange, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { viewModel.logout() }) {
                        Icon(Icons.Filled.Logout, "Keluar", tint = TKColor.Red)
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            StatCard("Pesanan hari ini", "${summary.ordersToday}", Modifier.weight(1f))
                            StatCard("Tukang online", "${summary.techniciansOnline}", Modifier.weight(1f))
                            StatCard("Laporan terbuka", "${summary.openReports}", Modifier.weight(1f))
                        }
                    }
                    item { SectionLabel("Aktivitas 7 hari terakhir") }
                    item {
                        val max = (state.dailyCounts.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.dailyCounts.forEach { (label, count) ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(64.dp),
                                        contentAlignment = Alignment.BottomCenter
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height((64f * count / max).dp.coerceAtLeast(2.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (count > 0) TKColor.Orange else TKColor.InkLine)
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(label, color = TKColor.Mist, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                    if (state.statusCounts.isNotEmpty()) {
                        item { SectionLabel("Status pesanan") }
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.statusCounts.entries.take(3).forEach { (status, count) ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(TKShape.Media)
                                            .background(TKColor.InkSoft)
                                            .padding(10.dp)
                                    ) {
                                        Column {
                                            Text("$count", color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                            Text(status, color = TKColor.Mist, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { SectionLabel("Kelola") }
                    item { AdminMenuCard(Icons.Filled.VerifiedUser, "Verifikasi tukang", "Setujui atau tolak pendaftaran tukang", onVerify) }
                    item { AdminMenuCard(Icons.Filled.ReportProblem, "Laporan masalah", "Tinjau dan jadwalkan kunjungan ulang", onReports) }
                    item { AdminMenuCard(Icons.Filled.ReceiptLong, "Semua pesanan", "Filter status dan simulasi pembayaran", onOrders) }
                    item { AdminMenuCard(Icons.Filled.Build, "Layanan", "Ubah harga, biaya kunjungan, dan status", onServices) }
                    item { AdminMenuCard(Icons.Filled.LocalActivity, "Kode promo", "Buat, aktifkan, dan nonaktifkan kode promo", onPromo) }
                    item { AdminMenuCard(Icons.Filled.Notifications, "Notifikasi", "Riwayat perubahan pesanan dan tawaran", onNotifications) }
                    item { SectionLabel("Data") }
                    item {
                        AdminMenuCard(
                            Icons.Filled.Upload,
                            "Ekspor database",
                            "Simpan salinan pesanan, foto, dan akun ke file zip",
                            { exportLauncher.launch("tukangkilat-backup.zip") }
                        )
                    }
                    item {
                        AdminMenuCard(
                            Icons.Filled.Download,
                            "Impor database",
                            "Pulihkan seluruh data dari file zip ekspor",
                            { importLauncher.launch(arrayOf("application/zip")) }
                        )
                    }
                    item {
                        AdminMenuCard(
                            Icons.Filled.Lock,
                            "Cadangan terenkripsi",
                            "Ekspor database dengan kata sandi (AES-GCM)",
                            { backupMode = "EXPORT" }
                        )
                    }
                    item {
                        AdminMenuCard(
                            Icons.Filled.Lock,
                            "Pulihkan cadangan",
                            "Buka cadangan terenkripsi dengan kata sandi",
                            { backupMode = "IMPORT" }
                        )
                    }
                    transferMessage?.let { message ->
                        item { Text(message, color = TKColor.Mist, fontSize = 12.sp) }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }

    backupMode?.let { mode ->
        AlertDialog(
            onDismissRequest = { backupMode = null; backupPassphrase = "" },
            confirmButton = {
                TextButton(onClick = {
                    backupMode = null
                    if (mode == "EXPORT") secureExportLauncher.launch("tukangkilat-backup.tkbk")
                    else secureImportLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
                }) { Text("Lanjut") }
            },
            dismissButton = {
                TextButton(onClick = { backupMode = null; backupPassphrase = "" }) { Text("Batal") }
            },
            title = { Text(if (mode == "EXPORT") "Kata sandi cadangan" else "Kata sandi pemulihan") },
            text = {
                Column {
                    Text("Minimal 8 karakter. Simpan kata sandi ini, tidak bisa dikembalikan.", color = TKColor.Mist, fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    TextField(
                        value = backupPassphrase,
                        onValueChange = { backupPassphrase = it },
                        label = { Text("Kata sandi") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                    )
                }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .padding(14.dp)
    ) {
        Text(label, color = TKColor.Mist, fontSize = 11.sp)
        Spacer(Modifier.height(6.dp))
        Text(value, color = TKColor.White, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp)
    }
}

@Composable
private fun AdminMenuCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val pressSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .clickable(onClick = onClick, interactionSource = pressSource)
            .tkPressScale(pressSource)
            .padding(TKSpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(TKShape.Media)
                .background(TKColor.Ink),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, "Masuk", tint = TKColor.Orange)
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(subtitle, color = TKColor.Mist, fontSize = 12.sp)
        }
        Icon(Icons.Filled.ChevronRight, "Lanjut", tint = TKColor.InkLine)
    }
}
