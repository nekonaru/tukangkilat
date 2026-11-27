package id.tukangkilat.app.ui

import id.tukangkilat.app.ui.theme.TKSpacing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.NavDeepLink
import id.tukangkilat.app.R
import id.tukangkilat.app.data.local.LocalBackend
import id.tukangkilat.app.ui.components.rememberReduceMotion
import id.tukangkilat.app.ui.components.TKNavigationBar
import id.tukangkilat.app.ui.admin.*
import id.tukangkilat.app.ui.auth.*
import id.tukangkilat.app.ui.consumer.*
import id.tukangkilat.app.ui.technician.*
import id.tukangkilat.app.ui.theme.TKColor
import id.tukangkilat.shared.Role
import kotlinx.coroutines.launch

data class BottomTab(val route: String, val label: String, val icon: ImageVector)

object Routes {
    const val LOGIN = "auth/login"
    const val REGISTER = "auth/register"
    const val REGISTER_TECH = "auth/register-technician"
    const val PENDING_VERIFICATION = "auth/pending"

    // consumer
    const val C_HOME = "consumer/home"
    const val C_TECHNICIANS = "consumer/technicians"
    const val C_ORDERS = "consumer/orders"
    const val C_ACCOUNT = "consumer/account"
    const val C_CHATS = "consumer/chats"
    const val C_DIRECT_CHAT = "consumer/direct-chat/{peerId}"
    const val T_DIRECT_CHAT = "technician/direct-chat/{peerId}"
    const val T_DIRECT_PEERS = "technician/chats"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"
    const val WALLET = "wallet"
    const val T_WORKBENCH = "technician/workbench"
    const val T_OPS = "technician/operations"
    const val A_CONTROL = "admin/control"
    const val A_PLATFORM = "admin/platform"
    const val C_CATALOG = "consumer/catalog"
    const val C_COMMERCE = "consumer/commerce"
    const val C_ORDER_NEW = "consumer/new"
    const val C_STATUS = "consumer/order/{orderId}/status"
    const val C_PAYMENT = "consumer/order/{orderId}/payment"
    const val C_SEARCHING = "consumer/order/{orderId}/searching"
    const val C_DONE = "consumer/order/{orderId}/done"
    const val C_CONFIRM = "consumer/order/{orderId}/confirm"
    const val C_RATING = "consumer/order/{orderId}/rating"
    const val C_REPORT = "consumer/order/{orderId}/report"
    const val C_RECEIPT = "consumer/order/{orderId}/receipt"
    const val C_CHAT = "consumer/chat/{orderId}"

    // technician
    const val T_HOME = "technician/home"
    const val T_JOBS = "technician/jobs"
    const val T_EARNINGS = "technician/earnings"
    const val T_ACCOUNT = "technician/account"
    const val T_JOB = "technician/job/{orderId}"
    const val T_FINISH = "technician/finish/{orderId}"
    const val T_CHAT = "technician/chat/{orderId}"

    // admin
    const val A_SUMMARY = "admin/summary"
    const val A_VERIFY = "admin/verify"
    const val A_REPORTS = "admin/reports"
    const val A_ORDERS = "admin/orders"
    const val A_SERVICES = "admin/services"
    const val A_PROMO = "admin/promo"

    fun status(orderId: Long) = "consumer/order/$orderId/status"
    fun payment(orderId: Long) = "consumer/order/$orderId/payment"
    fun searching(orderId: Long) = "consumer/order/$orderId/searching"
    fun done(orderId: Long) = "consumer/order/$orderId/done"
    fun confirm(orderId: Long) = "consumer/order/$orderId/confirm"
    fun rating(orderId: Long) = "consumer/order/$orderId/rating"
    fun report(orderId: Long) = "consumer/order/$orderId/report"
    fun receipt(orderId: Long) = "consumer/order/$orderId/receipt"
    fun chat(orderId: Long) = "consumer/chat/$orderId"
    fun techJob(orderId: Long) = "technician/job/$orderId"
    fun techFinish(orderId: Long) = "technician/finish/$orderId"
    fun techChat(orderId: Long) = "technician/chat/$orderId"
}

@Composable
fun TukangKilatApp(
    viewModel: AppViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val backendReady by LocalBackend.ready.collectAsState()
    val startupError by LocalBackend.startupError.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val usesEmbeddedBackend = listOf("127.0.0.1", "localhost", "10.0.2.2")
        .any { serverUrl.contains(it, ignoreCase = true) }
    val navController = rememberNavController()

    if (!backendReady && usesEmbeddedBackend) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        Box(
            modifier = Modifier.fillMaxSize().background(TKColor.Ink),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(TKSpacing.Md)) {
                Icon(
                    painter = painterResource(R.drawable.ic_brand),
                    contentDescription = "Tukang Kilat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(Modifier.height(24.dp))
                if (startupError == null) {
                    CircularProgressIndicator(color = TKColor.Orange)
                    Spacer(Modifier.height(12.dp))
                    Text("Menyiapkan aplikasi...", color = TKColor.Mist, fontSize = 13.sp)
                } else {
                    Text("Aplikasi gagal dimulai", color = TKColor.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    Spacer(Modifier.height(TKSpacing.Xs))
                    Text(startupError ?: "", color = TKColor.Mist, fontSize = 12.sp)
                    Spacer(Modifier.height(TKSpacing.Sm))
                    Button(onClick = { scope.launch { LocalBackend.retry(context.applicationContext) } }) {
                        Text("Coba lagi")
                    }
                }
            }
        }
        return
    }

    // websocket lifecycle tied to the session
    LaunchedEffect(session.isLoggedIn) {
        if (session.isLoggedIn) viewModel.startWs() else viewModel.stopWs()
    }
    // forced logout when the session dies (e.g. refresh token rejected)
    LaunchedEffect(session.isLoggedIn) {
        if (!session.isLoggedIn && navController.currentDestination != null) {
            if (navController.currentDestination?.route != Routes.LOGIN) {
                navController.navigate(Routes.LOGIN) { popUpTo(0) { inclusive = true } }
            }
        }
    }

    val startDestination = remember {
        when {
            !session.isLoggedIn -> Routes.LOGIN
            session.role == Role.KONSUMEN -> Routes.C_HOME
            session.role == Role.TUKANG -> Routes.T_HOME
            else -> Routes.A_SUMMARY
        }
    }

    // after a cold start the session is restored asynchronously; leave the login screen once it resolves
    LaunchedEffect(session.isLoggedIn, session.role) {
        val route = when {
            !session.isLoggedIn -> null
            session.role == Role.KONSUMEN -> Routes.C_HOME
            session.role == Role.TUKANG -> Routes.T_HOME
            else -> Routes.A_SUMMARY
        } ?: return@LaunchedEffect
        if (navController.currentDestination?.route == Routes.LOGIN) {
            navController.navigate(route) { popUpTo(0) { inclusive = true } }
        }
    }

    val roleName = session.role?.name ?: "KONSUMEN"

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = when (session.role) {
        Role.KONSUMEN -> currentRoute in listOf(Routes.C_HOME, Routes.C_ORDERS, Routes.C_CHATS, Routes.C_ACCOUNT)
        Role.TUKANG -> currentRoute in listOf(Routes.T_HOME, Routes.T_JOBS, Routes.T_EARNINGS, Routes.T_ACCOUNT)
        Role.ADMIN -> currentRoute in listOf(
            Routes.A_VERIFY, Routes.A_REPORTS, Routes.A_SERVICES, Routes.A_PROMO, Routes.A_SUMMARY
        )
        else -> false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (showBottomBar) {
                TKNavigationBar(roleName, currentRoute ?: "") { route ->
                    // Navigating to a tab must always emit a new destination. popUpTo(route)
                    // made the call a no-op when that tab was already in the back stack.
                    navController.navigate(route) { launchSingleTop = true }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            // ---------- Auth ----------
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoggedIn = { role ->
                        val route = when (role) {
                            Role.KONSUMEN -> Routes.C_HOME
                            Role.TUKANG -> Routes.T_HOME
                            Role.ADMIN -> Routes.A_SUMMARY
                        }
                        navController.navigate(route) { popUpTo(0) { inclusive = true } }
                    },
                    onRegister = { navController.navigate(Routes.REGISTER) },
                    onRegisterTechnician = { navController.navigate(Routes.REGISTER_TECH) }
                )
            }
            composable(Routes.REGISTER) {
                RegisterScreen(
                    onDone = { navController.navigateUp() },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.REGISTER_TECH) {
                RegisterTechnicianScreen(
                    onDone = { navController.navigate(Routes.PENDING_VERIFICATION) { popUpTo(Routes.LOGIN) } },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.PENDING_VERIFICATION) {
                PendingVerificationScreen(onBack = { navController.navigate(Routes.LOGIN) { popUpTo(0) } })
            }

            // ---------- Consumer ----------
            composable(Routes.C_HOME) {
                HomeScreen(
                    onOrder = { serviceId -> navController.navigate("${Routes.C_ORDER_NEW}?serviceId=$serviceId") },
                    onActiveOrder = { orderId, status -> navController.navigate(statusRouteFor(status, orderId)) },
                    onFindTechnicians = { navController.navigate(Routes.C_TECHNICIANS) },
                    onCatalog = { navController.navigate(Routes.C_CATALOG) },
                    onCommerce = { navController.navigate(Routes.C_COMMERCE) }
                )
            }
            composable(
                "${Routes.C_ORDER_NEW}?serviceId={serviceId}",
                arguments = listOf(navArgument("serviceId") { type = NavType.LongType; defaultValue = -1L })
            ) { entry ->
                val serviceId = entry.arguments?.getLong("serviceId")?.takeIf { it > 0 }
                OrderFormScreen(
                    preselectedServiceId = serviceId,
                    onDone = { orderId -> navController.navigate(Routes.payment(orderId)) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_ORDERS) {
                OrdersScreen(
                    onOpen = { orderId, status -> navController.navigate(statusRouteFor(status, orderId)) }
                )
            }
            composable(Routes.C_ACCOUNT) {
                AccountScreen(
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onBrowseTechnicians = { navController.navigate(Routes.C_TECHNICIANS) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onWallet = { navController.navigate(Routes.WALLET) }
                )
            }
            composable(Routes.C_TECHNICIANS) {
                TechniciansScreen(
                    onBack = { navController.navigateUp() },
                    onChat = { id -> navController.navigate("consumer/direct-chat/$id") }
                )
            }
            composable(
                Routes.C_DIRECT_CHAT,
                arguments = listOf(navArgument("peerId") { type = NavType.LongType }),
                deepLinks = listOf(NavDeepLink.Builder().setUriPattern("tukangkilat://chat/{peerId}").build())
            ) { entry ->
                DirectChatScreen(
                    peerId = entry.arguments!!.getLong("peerId"),
                    technicianSide = false,
                    onBack = { navController.navigateUp() }
                )
            }
            composable(
                Routes.T_DIRECT_CHAT,
                arguments = listOf(navArgument("peerId") { type = NavType.LongType })
            ) { entry ->
                DirectChatScreen(
                    peerId = entry.arguments!!.getLong("peerId"),
                    technicianSide = true,
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.T_DIRECT_PEERS) {
                TechnicianChatsScreen(
                    onOpenChat = { id -> navController.navigate("technician/direct-chat/$id") },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.navigateUp() },
                    onOpenOrder = { orderId -> navController.navigate(Routes.status(orderId)) }
                )
            }
            composable(Routes.C_CATALOG) {
                CatalogScreen(
                    onBack = { navController.navigateUp() },
                    onOpenService = { serviceId -> navController.navigate("${Routes.C_ORDER_NEW}?serviceId=$serviceId") }
                )
            }
            composable(Routes.C_COMMERCE) { CommerceScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.WALLET, deepLinks = listOf(
                NavDeepLink.Builder().setUriPattern("tukangkilat://promo/{code}").build()
            )) { entry ->
                WalletScreen(
                    onBack = { navController.navigateUp() },
                    promoCode = entry.arguments?.getString("code")
                )
            }
            composable(Routes.T_WORKBENCH) { WorkbenchScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.T_OPS) { OpsScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.A_CONTROL) { ControlScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.A_PLATFORM) { AdminPlatformScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.SETTINGS) { SettingsScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.C_CHATS) {
                ChatListScreen(
                    onOpenChat = { orderId -> navController.navigate(Routes.chat(orderId)) },
                    onOpenTechnicianChat = { id -> navController.navigate("consumer/direct-chat/$id") },
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_STATUS,
                arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
                deepLinks = listOf(NavDeepLink.Builder().setUriPattern("tukangkilat://order/{orderId}").build())
            ) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                OrderStatusScreen(
                    orderId = orderId,
                    onBack = { navController.navigateUp() },
                    onOpenChat = { navController.navigate(Routes.chat(orderId)) },
                    onBecomeSearching = { navController.navigate(Routes.searching(orderId)) { popUpTo(Routes.status(orderId)) { inclusive = true } } },
                    onWorkDone = { navController.navigate(Routes.done(orderId)) { popUpTo(Routes.status(orderId)) { inclusive = true } } }
                )
            }
            composable(Routes.C_PAYMENT, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                PaymentScreen(
                    orderId = orderId,
                    onPaid = { navController.navigate(Routes.searching(orderId)) { popUpTo(Routes.C_HOME) { inclusive = false } } },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_SEARCHING, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                SearchingScreen(
                    orderId = orderId,
                    onFound = { navController.navigate(Routes.status(orderId)) { popUpTo(Routes.C_HOME) } },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_DONE, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                DoneScreen(
                    orderId = orderId,
                    onConfirm = { navController.navigate(Routes.confirm(orderId)) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_CONFIRM, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                ConfirmScreen(
                    orderId = orderId,
                    onDone = { navController.navigate(Routes.rating(orderId)) { popUpTo(Routes.C_HOME) } },
                    onReport = { navController.navigate(Routes.report(orderId)) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_RATING, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                RatingScreen(
                    orderId = orderId,
                    onDone = { navController.navigate(Routes.C_HOME) { popUpTo(Routes.C_HOME) { inclusive = true } } },
                    onReorder = { serviceId -> navController.navigate("${Routes.C_ORDER_NEW}?serviceId=$serviceId") }
                )
            }
            composable(Routes.C_REPORT, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                ReportScreen(
                    orderId = orderId,
                    onDone = { navController.navigate(Routes.status(orderId)) { popUpTo(Routes.status(orderId)) { inclusive = true } } },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.C_RECEIPT, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                ReceiptScreen(
                    orderId = orderId,
                    onBack = { navController.navigate(Routes.C_HOME) { popUpTo(0) } },
                    onReorder = { serviceId -> navController.navigate("${Routes.C_ORDER_NEW}?serviceId=$serviceId") }
                )
            }
            composable(Routes.C_CHAT, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                ChatScreen(orderId = orderId, technicianSide = false, onBack = { navController.navigateUp() })
            }

            // ---------- Technician ----------
            composable(Routes.T_HOME) {
                TechHomeScreen(
                    onOpenJob = { orderId -> navController.navigate(Routes.techJob(orderId)) },
                    onGoEarnings = { navController.navigate(Routes.T_EARNINGS) },
                    onOpenChats = { navController.navigate(Routes.T_DIRECT_PEERS) },
                    onWorkbench = { navController.navigate(Routes.T_WORKBENCH) },
                    onOperations = { navController.navigate(Routes.T_OPS) }
                )
            }
            composable(Routes.T_JOBS) {
                TechJobsScreen(onOpenJob = { orderId -> navController.navigate(Routes.techJob(orderId)) })
            }
            composable(Routes.T_EARNINGS) { TechEarningsScreen() }
            composable(Routes.T_ACCOUNT) { TechAccountScreen(onNotifications = { navController.navigate(Routes.NOTIFICATIONS) }) }
            composable(Routes.T_JOB, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                TechJobScreen(
                    orderId = orderId,
                    onFinish = { navController.navigate(Routes.techFinish(orderId)) },
                    onOpenChat = { navController.navigate(Routes.techChat(orderId)) },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.T_FINISH, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                TechFinishScreen(
                    orderId = orderId,
                    onDone = { navController.navigate(Routes.T_JOBS) { popUpTo(Routes.T_HOME) } },
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.T_CHAT, arguments = listOf(navArgument("orderId") { type = NavType.LongType })) { entry ->
                val orderId = entry.arguments!!.getLong("orderId")
                ChatScreen(orderId = orderId, technicianSide = true, onBack = { navController.navigateUp() })
            }

            // ---------- Admin ----------
            composable(Routes.A_SUMMARY) {
                AdminSummaryScreen(
                    onVerify = { navController.navigate(Routes.A_VERIFY) },
                    onReports = { navController.navigate(Routes.A_REPORTS) },
                    onOrders = { navController.navigate(Routes.A_ORDERS) },
                    onServices = { navController.navigate(Routes.A_SERVICES) },
                    onPromo = { navController.navigate(Routes.A_PROMO) },
                    onNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onControl = { navController.navigate(Routes.A_CONTROL) },
                    onPlatform = { navController.navigate(Routes.A_PLATFORM) },
                )
            }
            composable(Routes.A_VERIFY) { AdminVerifyScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.A_REPORTS) { AdminReportsScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.A_ORDERS) { AdminOrdersScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.A_SERVICES) { AdminServicesScreen(onBack = { navController.navigateUp() }) }
            composable(Routes.A_PROMO) { AdminPromoScreen(onBack = { navController.navigateUp() }) }
        }
    }
}

private fun statusRouteFor(status: id.tukangkilat.shared.OrderStatus, orderId: Long): String =
    when (status) {
        id.tukangkilat.shared.OrderStatus.MENUNGGU_PEMBAYARAN,
        id.tukangkilat.shared.OrderStatus.PEMBAYARAN_GAGAL -> Routes.payment(orderId)
        id.tukangkilat.shared.OrderStatus.MENCARI_TEKNISI -> Routes.searching(orderId)
        id.tukangkilat.shared.OrderStatus.TEKNISI_DITEMUKAN,
        id.tukangkilat.shared.OrderStatus.DALAM_PERJALANAN,
        id.tukangkilat.shared.OrderStatus.TIBA,
        id.tukangkilat.shared.OrderStatus.SEDANG_BEKERJA,
        id.tukangkilat.shared.OrderStatus.DILAPORKAN,
        id.tukangkilat.shared.OrderStatus.KUNJUNGAN_ULANG -> Routes.status(orderId)
        id.tukangkilat.shared.OrderStatus.MENUNGGU_KONFIRMASI -> Routes.done(orderId)
        id.tukangkilat.shared.OrderStatus.SELESAI -> Routes.receipt(orderId)
        id.tukangkilat.shared.OrderStatus.DIBATALKAN -> Routes.status(orderId)
    }
