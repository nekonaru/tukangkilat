package id.tukangkilat.app.ui.consumer

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.ui.components.*
import id.tukangkilat.app.ui.theme.TKSpacing
import id.tukangkilat.app.ui.util.cleanApiError
import id.tukangkilat.shared.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MarketUiState(
    val tab: Int = 0,
    val sales: List<FlashSaleDto> = emptyList(),
    val groups: List<GroupBuyDto> = emptyList(),
    val bundles: List<CustomBundleDto> = emptyList(),
    val wishlist: List<WishlistDto> = emptyList(),
    val alerts: List<PriceAlertDto> = emptyList(),
    val gifts: List<GiftCardDto> = emptyList(),
    val pointItems: List<PointItemDto> = emptyList(),
    val bids: List<BidDto> = emptyList(),
    val serviceId: String = "",
    val targetPrice: String = "",
    val giftAmount: String = "",
    val giftCode: String = "",
    val bundleName: String = "",
    val bidOrderId: String = "",
    val notice: String? = null
)

@HiltViewModel
class MarketViewModel @Inject constructor(private val api: ApiService) : ViewModel() {
    private val _state = MutableStateFlow(MarketUiState())
    val state: StateFlow<MarketUiState> = _state.asStateFlow()

    fun refresh() {
        val current = _state.value
        viewModelScope.launch {
            try {
                _state.value = MarketUiState(
                    tab = current.tab, notice = current.notice,
                    serviceId = current.serviceId, targetPrice = current.targetPrice,
                    giftAmount = current.giftAmount, giftCode = current.giftCode, bundleName = current.bundleName,
                    bidOrderId = current.bidOrderId,
                    sales = runCatching { api.flashSales() }.getOrDefault(emptyList()),
                    groups = runCatching { api.groupBuys() }.getOrDefault(emptyList()),
                    bundles = runCatching { api.marketBundles() }.getOrDefault(emptyList()),
                    wishlist = runCatching { api.wishlist() }.getOrDefault(emptyList()),
                    alerts = runCatching { api.priceAlerts() }.getOrDefault(emptyList()),
                    gifts = runCatching { api.giftCards() }.getOrDefault(emptyList()),
                    pointItems = runCatching { api.pointItems() }.getOrDefault(emptyList())
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(notice = e.cleanApiError())
            }
        }
    }

    fun tab(index: Int) { _state.value = _state.value.copy(tab = index) }
    fun field(name: String, value: String) {
        _state.value = when (name) {
            "serviceId" -> _state.value.copy(serviceId = value)
            "targetPrice" -> _state.value.copy(targetPrice = value)
            "giftAmount" -> _state.value.copy(giftAmount = value)
            "giftCode" -> _state.value.copy(giftCode = value)
            "bundleName" -> _state.value.copy(bundleName = value)
            "bidOrderId" -> _state.value.copy(bidOrderId = value)
            else -> _state.value
        }
    }

    fun joinGroup(id: Long) {
        viewModelScope.launch {
            try { api.joinGroupBuy(id); refresh() }
            catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun addWishlist() {
        val s = _state.value
        val service = s.serviceId.toLongOrNull()
        val price = s.targetPrice.toLongOrNull()
        if (service == null || price == null) { _state.value = s.copy(notice = "Isi layanan dan harga target"); return }
        viewModelScope.launch {
            try {
                api.addWishlist(AddWishlistRequest(service, price))
                api.addPriceAlert(AddPriceAlertRequest(service, price))
                _state.value = s.copy(notice = "Wishlist dan alert dibuat", serviceId = "", targetPrice = "")
                refresh()
            } catch (e: Exception) { _state.value = s.copy(notice = e.cleanApiError()) }
        }
    }

    fun createGift() {
        val amount = _state.value.giftAmount.toLongOrNull()
        if (amount == null || amount <= 0) { _state.value = _state.value.copy(notice = "Nominal tidak valid"); return }
        viewModelScope.launch {
            try {
                api.createGiftCard(mapOf("amount" to amount))
                _state.value = _state.value.copy(notice = "Kartu hadiah dibuat", giftAmount = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun redeemGift() {
        val code = _state.value.giftCode.trim()
        if (code.isBlank()) return
        viewModelScope.launch {
            try {
                api.redeemGift(RedeemGiftRequest(code))
                _state.value = _state.value.copy(notice = "Hadiah masuk dompet", giftCode = "")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun redeemPoint(code: String) {
        viewModelScope.launch {
            try {
                api.redeemPoint(RedeemPointRequest(code))
                _state.value = _state.value.copy(notice = "Poin ditukar")
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun loadBids() {
        val id = _state.value.bidOrderId.toLongOrNull()
        if (id == null) { _state.value = _state.value.copy(notice = "ID pesanan tidak valid"); return }
        viewModelScope.launch {
            try {
                _state.value = _state.value.copy(bids = api.consumerBids(id))
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun resolveBid(id: Long, accept: Boolean) {
        viewModelScope.launch {
            try {
                api.resolveBid(id, ResolveBidRequest(accept))
                _state.value = _state.value.copy(notice = if (accept) "Penawaran diterima" else "Penawaran ditolak")
                refresh()
            } catch (e: Exception) { _state.value = _state.value.copy(notice = e.cleanApiError()) }
        }
    }

    fun clearNotice() { _state.value = _state.value.copy(notice = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarketplaceScreen(onBack: () -> Unit, viewModel: MarketViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.notice) { state.notice?.let { snackbar.showSnackbar(it); viewModel.clearNotice() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TKTopAppBar("Pasar", onBack = onBack, showBell = false) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScrollableTabRow(selectedTabIndex = state.tab) {
                listOf("Promo", "Bersama", "Wishlist", "Hadiah", "Poin", "Lelang").forEachIndexed { i, title ->
                    Tab(selected = state.tab == i, onClick = { viewModel.tab(i) }, text = { Text(title) })
                }
            }
            when (state.tab) {
                0 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.sales, key = { it.id }) { sale ->
                        TKCard {
                            Text("Layanan ${sale.serviceId} · diskon ${sale.discountPercent}%", fontWeight = FontWeight.Bold)
                            Text("Sisa ${sale.remaining} · berakhir ${sale.endsAt.take(10)}")
                        }
                    }
                }
                1 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.groups, key = { it.id }) { group ->
                        TKCard {
                            Text("${group.areaName} · ${group.joinedCount}/${group.targetCount}", fontWeight = FontWeight.Bold)
                            Text(group.status, style = MaterialTheme.typography.bodySmall)
                            if (group.status == "OPEN") TKButton("Gabung", { viewModel.joinGroup(group.id) }, variant = TKButtonVariant.Tonal)
                        }
                    }
                }
                2 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.serviceId, { viewModel.field("serviceId", it) }, label = "ID layanan")
                    TKOutlinedField(state.targetPrice, { viewModel.field("targetPrice", it) }, label = "Harga target")
                    TKButton("Tambah wishlist dan alert", viewModel::addWishlist, variant = TKButtonVariant.Tonal)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.wishlist, key = { it.id }) { item ->
                            Text("Layanan ${item.serviceId} · target Rp${item.targetPrice} · kini Rp${item.currentPrice}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                        items(state.alerts, key = { it.id }) { alert ->
                            Text("Alert ${alert.serviceId} · Rp${alert.targetPrice} · ${if (alert.triggered) "terpicu" else "aktif"}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                3 -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.giftAmount, { viewModel.field("giftAmount", it) }, label = "Nominal kartu hadiah")
                    TKButton("Buat kartu", viewModel::createGift, variant = TKButtonVariant.Tonal)
                    TKOutlinedField(state.giftCode, { viewModel.field("giftCode", it) }, label = "Kode hadiah")
                    TKButton("Pakai kode", viewModel::redeemGift, variant = TKButtonVariant.Outlined)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.gifts, key = { it.id }) { gift ->
                            Text("${gift.code} · Rp${gift.amount} · ${gift.status}", modifier = Modifier.padding(vertical = 4.dp))
                        }
                    }
                }
                4 -> LazyColumn(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    items(state.pointItems, key = { it.code }) { item ->
                        TKCard {
                            Text("${item.name} · ${item.points} poin", fontWeight = FontWeight.Bold)
                            Text(item.description, style = MaterialTheme.typography.bodySmall)
                            TKButton("Tukar", { viewModel.redeemPoint(item.code) }, variant = TKButtonVariant.Tonal)
                        }
                    }
                }
                else -> Column(Modifier.fillMaxSize().padding(TKSpacing.Sm)) {
                    TKOutlinedField(state.bidOrderId, { viewModel.field("bidOrderId", it) }, label = "ID pesanan")
                    TKButton("Muat penawaran", viewModel::loadBids)
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(state.bids, key = { it.id }) { bid ->
                            TKCard {
                                Text("Teknisi ${bid.technicianId} · Rp${bid.price}")
                                Text(bid.note, style = MaterialTheme.typography.bodySmall)
                                Row(horizontalArrangement = Arrangement.spacedBy(TKSpacing.Xs)) {
                                    TKButton("Terima", { viewModel.resolveBid(bid.id, true) }, modifier = Modifier.weight(1f))
                                    TKButton("Tolak", { viewModel.resolveBid(bid.id, false) }, modifier = Modifier.weight(1f), variant = TKButtonVariant.Outlined)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
