package id.tukangkilat.app.ui.consumer

import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
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
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.repository.FavoriteRepository
import id.tukangkilat.app.ui.components.AvatarCircle
import id.tukangkilat.app.ui.components.EmptyView
import id.tukangkilat.app.ui.components.ErrorView
import id.tukangkilat.app.ui.components.LoadingView
import id.tukangkilat.app.ui.components.ScreenHeader
import id.tukangkilat.app.ui.components.TextFieldDark
import id.tukangkilat.app.ui.components.tkScreenEnter
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.shared.TechnicianBrowseDto
import id.tukangkilat.shared.TechnicianPublicDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TechniciansUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val items: List<TechnicianBrowseDto> = emptyList(),
    val favoriteIds: Set<Long> = emptySet()
)

@HiltViewModel
class TechniciansViewModel @Inject constructor(
    private val api: ApiService,
    private val favoriteRepository: FavoriteRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TechniciansUiState())
    val state: StateFlow<TechniciansUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            favoriteRepository.refresh()
            _state.value = _state.value.copy(favoriteIds = favoriteRepository.items.value.map { it.userId }.toSet())
        }
        load(null, null)
    }

    fun load(skill: String?, query: String?) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val items = api.technicians(skill = skill?.takeIf { it != "SEMUA" }, query = query?.takeIf { it.isNotBlank() })
                _state.value = _state.value.copy(isLoading = false, items = items, error = null)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Gagal memuat teknisi")
            }
        }
    }

    fun toggleFavorite(item: TechnicianBrowseDto) {
        viewModelScope.launch {
            favoriteRepository.toggle(
                TechnicianPublicDto(
                    id = item.id,
                    fullName = item.fullName,
                    ratingAvg = item.ratingAvg,
                    jobsCount = item.jobsCount,
                    photoUrl = item.photoUrl
                )
            )
            _state.value = _state.value.copy(favoriteIds = favoriteRepository.items.value.map { it.userId }.toSet())
        }
    }
}

private val SKILLS = listOf("SEMUA", "LISTRIK", "PIPA", "AC")

@Composable
fun TechniciansScreen(onBack: () -> Unit, viewModel: TechniciansViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var skill by remember { mutableStateOf("SEMUA") }
    var detail by remember { mutableStateOf<TechnicianBrowseDto?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TKColor.Ink)
            .tkScreenEnter()
    ) {
        ScreenHeader("Cari Teknisi", onBack = onBack)
        TextFieldDark(query, { query = it }, placeholder = "Cari nama atau keahlian", modifier = Modifier.padding(horizontal = 18.dp))
        Spacer(Modifier.height(TKSpacing.Xs))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SKILLS.forEach { option ->
                FilterChip(
                    selected = skill == option,
                    onClick = {
                        skill = option
                        viewModel.load(option, query)
                    },
                    label = { Text(option, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = TKColor.Orange,
                        selectedLabelColor = TKColor.White,
                        labelColor = TKColor.Mist
                    )
                )
            }
        }
        Spacer(Modifier.height(TKSpacing.Xs))
        TextButton(
            onClick = { viewModel.load(skill, query) },
            modifier = Modifier.padding(horizontal = 12.dp)
        ) { Text("Cari", color = TKColor.Orange) }

        when {
            state.isLoading -> LoadingView()
            state.error != null && state.items.isEmpty() ->
                ErrorView(message = state.error ?: "Galat", onRetry = { viewModel.load(skill, query) })
            state.items.isEmpty() -> EmptyView(
                title = "Belum ada teknisi",
                subtitle = "Coba kata kunci atau filter lain."
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(state.items, key = { it.id }) { item ->
                    TechnicianCard(
                        item = item,
                        favorite = item.id in state.favoriteIds,
                        onFavorite = { viewModel.toggleFavorite(item) },
                        onDetail = { detail = item }
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }

    detail?.let { item ->
        AlertDialog(
            onDismissRequest = { detail = null },
            containerColor = TKColor.InkSoft,
            title = { Text(item.fullName, color = TKColor.White, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Keahlian: ${item.skills.joinToString(", ").ifBlank { "-" }}", color = TKColor.Mist, fontSize = 13.sp)
                    Text("Rating: ${String.format("%.1f", item.ratingAvg)} dari ${item.jobsCount} pekerjaan", color = TKColor.Mist, fontSize = 13.sp)
                    Text("Radius layanan: ${item.serviceRadiusKm} km", color = TKColor.Mist, fontSize = 13.sp)
                    Text(
                        if (item.isOnline) "Sedang online" else "Sedang offline",
                        color = if (item.isOnline) TKColor.Green else TKColor.Mist,
                        fontSize = 13.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { detail = null }) { Text("Tutup", color = TKColor.Orange) }
            }
        )
    }
}

@Composable
private fun TechnicianCard(
    item: TechnicianBrowseDto,
    favorite: Boolean,
    onFavorite: () -> Unit,
    onDetail: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(TKColor.InkSoft)
            .clickable(onClick = onDetail)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(item.fullName, size = 48, photoUrl = item.photoUrl)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.fullName, color = TKColor.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(item.skills.joinToString(" · ").ifBlank { "Belum ada keahlian" }, color = TKColor.Mist, fontSize = 11.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star, null,
                    tint = TKColor.Orange,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "${String.format("%.1f", item.ratingAvg)} (${item.jobsCount} pekerjaan)",
                    color = TKColor.Mist,
                    fontSize = 11.sp
                )
                if (item.isOnline) {
                    Spacer(Modifier.width(TKSpacing.Xs))
                    Text("Online", color = TKColor.Green, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        IconButton(onClick = onFavorite) {
            Icon(
                if (favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                if (favorite) "Hapus favorit" else "Simpan favorit",
                tint = if (favorite) TKColor.Red else TKColor.Mist
            )
        }
    }
}
