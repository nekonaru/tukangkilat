package id.tukangkilat.app.data.repository

import id.tukangkilat.app.data.api.ApiService
import id.tukangkilat.app.data.local.CatalogDao
import id.tukangkilat.app.data.local.toCached
import id.tukangkilat.app.data.local.toDto
import id.tukangkilat.app.data.local.SessionManager
import id.tukangkilat.app.data.local.OrderDao
import id.tukangkilat.app.data.local.toCached
import id.tukangkilat.app.data.local.toSummary
import id.tukangkilat.shared.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val session: SessionManager
) {
    suspend fun login(username: String, password: String): Result<LoginResponse> = runCatching {
        val res = api.login(LoginRequest(username, password))
        session.save(res.accessToken, res.refreshToken, res.role, res.user)
        res
    }

    suspend fun register(req: RegisterRequest): Result<LoginResponse> = runCatching {
        val res = api.register(req)
        session.save(res.accessToken, res.refreshToken, res.role, res.user)
        res
    }

    suspend fun registerTechnician(req: RegisterTechnicianRequest): Result<UserBrief> = runCatching {
        api.registerTechnician(req)
    }

    suspend fun logout() = runCatching {
        session.refreshToken()?.let { api.logout(RefreshRequest(it)) }
        session.logout()
    }

    fun currentSession() = session.session
}

@Singleton
class OrderRepository @Inject constructor(
    private val api: ApiService,
    private val dao: OrderDao,
    private val catalog: CatalogDao
) {
    suspend fun services(): List<ServiceDto> = withContext(Dispatchers.IO) {
        try {
            val fresh = api.services()
            catalog.clearServices()
            catalog.upsertServices(fresh.map { it.toCached() })
            fresh
        } catch (e: Exception) {
            val cached = catalog.services().map { it.toDto() }
            if (cached.isNotEmpty()) cached else throw e
        }
    }

    suspend fun cachedServices(): List<ServiceDto> = withContext(Dispatchers.IO) {
        catalog.services().map { it.toDto() }
    }

    suspend fun slots(date: String) = api.slots(date)

    suspend fun addresses(): List<AddressDto> = withContext(Dispatchers.IO) {
        try {
            val fresh = api.addresses()
            catalog.clearAddresses()
            catalog.upsertAddresses(fresh.map { it.toCached() })
            fresh
        } catch (e: Exception) {
            val cached = catalog.addresses().map { it.toDto() }
            if (cached.isNotEmpty()) cached else throw e
        }
    }

    suspend fun cachedAddresses(): List<AddressDto> = withContext(Dispatchers.IO) {
        catalog.addresses().map { it.toDto() }
    }
    suspend fun addAddress(req: AddressUpsertRequest) = api.addAddress(req)
    suspend fun updateAddress(id: Long, req: AddressUpsertRequest) = api.updateAddress(id, req)
    suspend fun deleteAddress(id: Long) = api.deleteAddress(id)

    suspend fun createOrder(req: CreateOrderRequest) = api.createOrder(req)

    suspend fun myOrders(): List<OrderSummaryDto> = withContext(Dispatchers.IO) {
        try {
            val fresh = api.myOrders()
            dao.clear()
            dao.upsertAll(fresh.map { it.toCached() })
            fresh
        } catch (e: Exception) {
            val cached = dao.all().map { it.toSummary() }
            if (cached.isNotEmpty()) cached else throw e
        }
    }

    suspend fun cachedOrders(): List<OrderSummaryDto> = withContext(Dispatchers.IO) {
        dao.all().map { it.toSummary() }
    }

    suspend fun detail(id: Long) = api.orderDetail(id)
    suspend fun pay(id: Long, method: PaymentMethod, promoCode: String? = null) = api.pay(id, PayRequest(method, promoCode))
    suspend fun cancel(id: Long, reason: String) = api.cancel(id, CancelRequest(reason))
    suspend fun reschedule(id: Long, date: String, slotId: Long) = api.reschedule(id, RescheduleRequest(date, slotId))
    suspend fun confirm(id: Long) = api.confirm(id)
    suspend fun report(id: Long, req: ReportOrderRequest) = api.report(id, req)
    suspend fun rate(id: Long, req: RatingRequest) = api.rate(id, req)
    suspend fun messages(id: Long) = api.messages(id)
    suspend fun sendMessage(id: Long, body: String) = api.sendMessage(id, SendMessageRequest(body))
    suspend fun technicianMessages(id: Long) = api.technicianMessages(id)
    suspend fun technicianSendMessage(id: Long, body: String) = api.technicianSendMessage(id, SendMessageRequest(body))
}

@Singleton
class TechnicianRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun setOnline(online: Boolean) = api.setOnline(OnlineRequest(online))
    suspend fun sendLocation(lat: Double, lng: Double) = api.sendLocation(LocationRequest(lat, lng))
    suspend fun profile() = api.technicianProfile()
    suspend fun updateProfile(req: TechnicianUpdateRequest) = api.updateTechnicianProfile(req)
    suspend fun offers() = api.offers()
    suspend fun acceptOffer(id: Long) = api.acceptOffer(id)
    suspend fun rejectOffer(id: Long, reason: String) = api.rejectOffer(id, RejectOfferRequest(reason))
    suspend fun depart(id: Long) = api.depart(id)
    suspend fun arrive(id: Long) = api.arrive(id)
    suspend fun start(id: Long) = api.start(id)
    suspend fun finish(id: Long, req: FinishWorkRequest) = api.finish(id, req)
    suspend fun myOrders() = api.technicianOrders()
    suspend fun earnings() = api.earnings()
    suspend fun reviews() = api.technicianReviews()
    suspend fun withdrawals() = api.withdrawals()
    suspend fun withdraw(amount: Long) = api.withdraw(WithdrawRequest(amount))
}

@Singleton
class SystemRepository @Inject constructor(private val api: ApiService) {
    suspend fun health(): String = runCatching { api.health()["status"] ?: "ok" }
        .getOrElse { throw it }
}

class AdminRepository @Inject constructor(
    private val api: ApiService
) {
    suspend fun summary() = api.adminSummary()
    suspend fun analytics() = api.adminAnalytics()
    suspend fun technicians(status: String?) = api.adminTechnicians(status)
    suspend fun approveTechnician(id: Long) = api.approveTechnician(id)
    suspend fun rejectTechnician(id: Long, reason: String) = api.rejectTechnician(id, RejectTechnicianRequest(reason))
    suspend fun reports(status: String?) = api.adminReports(status)
    suspend fun revisitReport(id: Long) = api.revisitReport(id)
    suspend fun closeReport(id: Long) = api.closeReport(id)
    suspend fun orders(status: String?) = api.adminOrders(status)
    suspend fun services() = api.adminServices()
    suspend fun createService(req: ServiceCreateRequest) = api.createService(req)
    suspend fun updateService(id: Long, req: ServiceUpdateRequest) = api.updateService(id, req)
    suspend fun setUserStatus(id: Long, status: String) = api.setUserStatus(id, UserStatusUpdateRequest(status))
    suspend fun simulatePaymentSuccess(orderId: Long) = api.simulatePaymentSuccess(orderId)
    suspend fun simulatePaymentFail(orderId: Long) = api.simulatePaymentFail(orderId)
    suspend fun promoCodes() = api.adminPromoCodes()
    suspend fun upsertPromoCode(req: PromoUpsertRequest) = api.upsertPromoCode(req)
}
