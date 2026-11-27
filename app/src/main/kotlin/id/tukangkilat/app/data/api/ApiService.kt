package id.tukangkilat.app.data.api

import id.tukangkilat.shared.*
import retrofit2.http.*

interface ApiService {

    @GET("/announcements")
    suspend fun publicAnnouncements(): List<AnnouncementDto>

    @GET("/health")
    suspend fun health(): Map<String, String>

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
    @POST("/auth/register/photo")
    suspend fun uploadRegistrationPhoto(@Part file: okhttp3.MultipartBody.Part): Map<String, String>

    @Multipart
    @POST("/photos")
    suspend fun uploadPhoto(@Part file: okhttp3.MultipartBody.Part): Map<String, String>

    // ----- Consumer -----
    @GET("/consumer/wallet")
    suspend fun wallet(): WalletDto

    @POST("/consumer/wallet/topup")
    suspend fun topUp(@Body req: TopUpRequest): WalletDto

    @GET("/consumer/loyalty")
    suspend fun loyalty(): LoyaltyDto

    @POST("/consumer/loyalty/redeem")
    suspend fun redeemPoints(@Body req: Map<String, Int>): LoyaltyDto

    @GET("/consumer/subscriptions")
    suspend fun subscriptions(): List<SubscriptionDto>

    @POST("/consumer/subscriptions")
    suspend fun addSubscription(@Body req: CreateSubscriptionRequest): SubscriptionDto

    @DELETE("/consumer/subscriptions/{id}")
    suspend fun cancelSubscription(@Path("id") id: Long)

    @GET("/consumer/services")
    suspend fun services(): List<ServiceDto>

    @GET("/consumer/technicians")
    suspend fun technicians(
        @Query("skill") skill: String? = null,
        @Query("q") query: String? = null,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null
    ): List<TechnicianBrowseDto>

    @GET("/consumer/direct-peers")
    suspend fun consumerDirectPeers(): List<DirectPeerDto>

    @POST("/consumer/direct-peers/{peerId}/read")
    suspend fun markConsumerPeerRead(@Path("peerId") peerId: Long): Map<String, String>

    @POST("/consumer/messages/{messageId}/report")
    suspend fun reportConsumerMessage(
        @Path("messageId") messageId: Long,
        @Body req: ReportMessageRequest
    ): MessageReportDto

    @GET("/consumer/technicians/{technicianId}/messages")
    suspend fun consumerMessages(@Path("technicianId") technicianId: Long): List<DirectMessageDto>

    @POST("/consumer/technicians/{technicianId}/messages")
    suspend fun sendConsumerMessage(
        @Path("technicianId") technicianId: Long,
        @Body req: SendDirectMessageRequest
    ): DirectMessageDto

    @GET("/consumer/proposals")
    suspend fun consumerProposals(): List<ProposalDto>

    @POST("/consumer/proposals/{id}/accept")
    suspend fun acceptProposal(@Path("id") id: Long): Map<String, Long>

    @POST("/consumer/proposals/{id}/reject")
    suspend fun rejectProposal(@Path("id") id: Long): ProposalDto

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
    @GET("/technician/availability")
    suspend fun availability(): List<AvailabilityBlockDto>

    @POST("/technician/availability")
    suspend fun addAvailability(@Body req: CreateAvailabilityBlockRequest): AvailabilityBlockDto

    @DELETE("/technician/availability/{id}")
    suspend fun deleteAvailability(@Path("id") id: Long)

    @GET("/technician/portfolio")
    suspend fun portfolio(): List<PortfolioDto>

    @POST("/technician/portfolio")
    suspend fun addPortfolio(@Body req: AddPortfolioRequest): PortfolioDto

    @DELETE("/technician/portfolio/{id}")
    suspend fun deletePortfolio(@Path("id") id: Long)

    @GET("/technician/payout-account")
    suspend fun payoutAccount(): PayoutAccountDto?

    @PUT("/technician/payout-account")
    suspend fun savePayoutAccount(@Body req: SavePayoutAccountRequest): Map<String, String>

    @GET("/technician/stats")
    suspend fun technicianStats(): TechnicianStatsDto

    @PUT("/technician/online")
    suspend fun setOnline(@Body req: OnlineRequest): Map<String, String>

    @POST("/technician/location")
    suspend fun sendLocation(@Body req: LocationRequest): Map<String, String>

    @GET("/technician/profile")
    suspend fun technicianProfile(): TechnicianProfileDto

    @PUT("/technician/profile")
    suspend fun updateTechnicianProfile(@Body req: TechnicianUpdateRequest): TechnicianProfileDto

    @GET("/technician/direct-peers")
    suspend fun directPeers(): List<Long>

    @GET("/technician/direct-peers/summary")
    suspend fun directPeerSummaries(): List<DirectPeerDto>

    @POST("/technician/direct-peers/{peerId}/read")
    suspend fun markTechnicianPeerRead(@Path("peerId") peerId: Long): Map<String, String>

    @POST("/technician/messages/{messageId}/report")
    suspend fun reportTechnicianMessage(
        @Path("messageId") messageId: Long,
        @Body req: ReportMessageRequest
    ): MessageReportDto

    @GET("/technician/auto-schedule")
    suspend fun autoSchedules(): List<AutoOnlineScheduleDto>

    @POST("/technician/auto-schedule")
    suspend fun saveAutoSchedule(@Body req: CreateAutoScheduleRequest): AutoOnlineScheduleDto

    @DELETE("/technician/auto-schedule/{id}")
    suspend fun deleteAutoSchedule(@Path("id") id: Long)

    @GET("/technician/direct-messages/{consumerId}")
    suspend fun technicianDirectMessages(@Path("consumerId") consumerId: Long): List<DirectMessageDto>

    @POST("/technician/direct-messages/{consumerId}")
    suspend fun sendTechnicianMessage(
        @Path("consumerId") consumerId: Long,
        @Body req: SendDirectMessageRequest
    ): DirectMessageDto

    @GET("/technician/proposals")
    suspend fun technicianProposals(): List<ProposalDto>

    @POST("/technician/proposals")
    suspend fun createProposal(@Body req: CreateProposalRequest): ProposalDto

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
    @GET("/admin/disputes")
    suspend fun disputes(): List<DisputeDto>

    @POST("/admin/disputes/{id}/resolve")
    suspend fun resolveDispute(@Path("id") id: Long, @Body req: ResolveDisputeRequest): DisputeDto

    @GET("/admin/audit")
    suspend fun auditLogs(): List<AuditLogDto>

    @GET("/admin/announcements")
    suspend fun announcements(): List<AnnouncementDto>

    @POST("/admin/announcements")
    suspend fun createAnnouncement(@Body req: CreateAnnouncementRequest): AnnouncementDto

    @GET("/admin/config")
    suspend fun systemConfigs(): List<SystemConfigDto>

    @PUT("/admin/config/{key}")
    suspend fun setSystemConfig(@Path("key") key: String, @Body req: Map<String, String>): Map<String, String>

    @GET("/admin/leaders")
    suspend fun leaders(): List<TechnicianLeaderDto>

    @GET("/admin/message-reports")
    suspend fun messageReports(): List<MessageReportDto>

    @POST("/admin/message-reports/{id}/resolve")
    suspend fun resolveMessageReport(
        @Path("id") id: Long,
        @Body req: Map<String, Boolean>
    ): Map<String, String>

    @GET("/admin/export/orders")
    suspend fun exportOrders(@Query("from") from: String? = null, @Query("to") to: String? = null): okhttp3.ResponseBody

    @GET("/admin/export/wallet")
    suspend fun exportWallet(): okhttp3.ResponseBody

    @GET("/admin/analytics")
    suspend fun adminAnalytics(): AdminAnalyticsDto

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
