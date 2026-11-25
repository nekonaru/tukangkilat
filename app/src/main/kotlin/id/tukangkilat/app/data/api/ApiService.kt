package id.tukangkilat.app.data.api

import id.tukangkilat.shared.*
import retrofit2.http.*

interface ApiService {

    // ----- Auth -----
    @POST("/auth/register")
    suspend fun register(@Body req: RegisterRequest): LoginResponse

    @POST("/auth/register-technician")
    suspend fun registerTechnician(@Body req: RegisterTechnicianRequest): UserBrief

    @POST("/auth/login")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @POST("/auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): LoginResponse

    @POST("/auth/logout")
    suspend fun logout(@Body req: RefreshRequest): Map<String, String>

    @POST("/account/password")
    suspend fun changePassword(@Body req: ChangePasswordRequest): Map<String, String>

    @Multipart
    @POST("/photos")
    suspend fun uploadPhoto(@Part file: okhttp3.MultipartBody.Part): Map<String, String>

    // ----- Consumer -----
    @GET("/consumer/services")
    suspend fun services(): List<ServiceDto>

    @GET("/consumer/slots")
    suspend fun slots(@Query("date") date: String): List<SlotDto>

    @GET("/consumer/addresses")
    suspend fun addresses(): List<AddressDto>

    @POST("/consumer/addresses")
    suspend fun addAddress(@Body req: AddressUpsertRequest): AddressDto

    @PUT("/consumer/addresses/{id}")
    suspend fun updateAddress(@Path("id") id: Long, @Body req: AddressUpsertRequest): AddressDto

    @DELETE("/consumer/addresses/{id}")
    suspend fun deleteAddress(@Path("id") id: Long)

    @POST("/consumer/orders")
    suspend fun createOrder(@Body req: CreateOrderRequest): OrderDto

    @GET("/consumer/orders")
    suspend fun myOrders(): List<OrderSummaryDto>

    @GET("/consumer/orders/{id}")
    suspend fun orderDetail(@Path("id") id: Long): OrderDto

    @POST("/consumer/orders/{id}/pay")
    suspend fun pay(@Path("id") id: Long, @Body req: PayRequest): PayResponse

    @POST("/consumer/orders/{id}/cancel")
    suspend fun cancel(@Path("id") id: Long, @Body req: CancelRequest): OrderDto

    @POST("/consumer/orders/{id}/reschedule")
    suspend fun reschedule(@Path("id") id: Long, @Body req: RescheduleRequest): OrderDto

    @POST("/consumer/orders/{id}/confirm")
    suspend fun confirm(@Path("id") id: Long): OrderDto

    @POST("/consumer/orders/{id}/report")
    suspend fun report(@Path("id") id: Long, @Body req: ReportOrderRequest): OrderDto

    @POST("/consumer/orders/{id}/rating")
    suspend fun rate(@Path("id") id: Long, @Body req: RatingRequest): OrderDto

    @GET("/consumer/orders/{id}/messages")
    suspend fun messages(@Path("id") id: Long): List<MessageDto>

    @POST("/consumer/orders/{id}/messages")
    suspend fun sendMessage(@Path("id") id: Long, @Body req: SendMessageRequest): MessageDto

    // ----- Technician -----
    @PUT("/technician/online")
    suspend fun setOnline(@Body req: OnlineRequest): Map<String, String>

    @POST("/technician/location")
    suspend fun sendLocation(@Body req: LocationRequest): Map<String, String>

    @GET("/technician/profile")
    suspend fun technicianProfile(): TechnicianProfileDto

    @PUT("/technician/profile")
    suspend fun updateTechnicianProfile(@Body req: TechnicianUpdateRequest): TechnicianProfileDto

    @GET("/technician/offers")
    suspend fun offers(): List<OfferDto>

    @POST("/technician/offers/{id}/accept")
    suspend fun acceptOffer(@Path("id") id: Long): OrderDto

    @POST("/technician/offers/{id}/reject")
    suspend fun rejectOffer(@Path("id") id: Long, @Body req: RejectOfferRequest): Map<String, String>

    @POST("/technician/orders/{id}/depart")
    suspend fun depart(@Path("id") id: Long): OrderDto

    @POST("/technician/orders/{id}/arrive")
    suspend fun arrive(@Path("id") id: Long): OrderDto

    @POST("/technician/orders/{id}/start")
    suspend fun start(@Path("id") id: Long): OrderDto

    @POST("/technician/orders/{id}/finish")
    suspend fun finish(@Path("id") id: Long, @Body req: FinishWorkRequest): OrderDto

    @GET("/technician/orders")
    suspend fun technicianOrders(): List<OrderSummaryDto>

    @GET("/technician/earnings")
    suspend fun earnings(): EarningsDto

    @GET("/technician/reviews")
    suspend fun technicianReviews(): List<ReviewDto>

    @GET("/technician/withdrawals")
    suspend fun withdrawals(): List<WithdrawalDto>

    @POST("/technician/withdrawals")
    suspend fun withdraw(@Body req: WithdrawRequest): WithdrawalDto

    @GET("/technician/orders/{id}/messages")
    suspend fun technicianMessages(@Path("id") id: Long): List<MessageDto>

    @POST("/technician/orders/{id}/messages")
    suspend fun technicianSendMessage(@Path("id") id: Long, @Body req: SendMessageRequest): MessageDto

    // ----- Admin -----
    @GET("/admin/summary")
    suspend fun adminSummary(): AdminSummaryDto

    @GET("/admin/technicians")
    suspend fun adminTechnicians(@Query("status") status: String?): List<AdminTechnicianDto>

    @POST("/admin/technicians/{id}/approve")
    suspend fun approveTechnician(@Path("id") id: Long): Map<String, String>

    @POST("/admin/technicians/{id}/reject")
    suspend fun rejectTechnician(@Path("id") id: Long, @Body req: RejectTechnicianRequest): Map<String, String>

    @GET("/admin/reports")
    suspend fun adminReports(@Query("status") status: String?): List<IssueReportDto>

    @POST("/admin/reports/{id}/revisit")
    suspend fun revisitReport(@Path("id") id: Long): Map<String, String>

    @POST("/admin/reports/{id}/close")
    suspend fun closeReport(@Path("id") id: Long): Map<String, String>

    @GET("/admin/orders")
    suspend fun adminOrders(@Query("status") status: String?, @Query("limit") limit: Int = 50): List<OrderSummaryDto>

    @GET("/admin/services")
    suspend fun adminServices(): List<ServiceDto>

    @POST("/admin/services")
    suspend fun createService(@Body req: ServiceCreateRequest): ServiceDto

    @PUT("/admin/services/{id}")
    suspend fun updateService(@Path("id") id: Long, @Body req: ServiceUpdateRequest): ServiceDto

    @POST("/admin/users/{id}/status")
    suspend fun setUserStatus(@Path("id") id: Long, @Body req: UserStatusUpdateRequest): Map<String, String>

    @POST("/admin/payments/{orderId}/simulate-success")
    suspend fun simulatePaymentSuccess(@Path("orderId") orderId: Long): Map<String, String>

    @POST("/admin/payments/{orderId}/simulate-fail")
    suspend fun simulatePaymentFail(@Path("orderId") orderId: Long): Map<String, String>

    @GET("/admin/promo-codes")
    suspend fun adminPromoCodes(): List<PromoCodeDto>

    @PUT("/admin/promo-codes")
    suspend fun upsertPromoCode(@Body req: PromoUpsertRequest): PromoCodeDto
}
