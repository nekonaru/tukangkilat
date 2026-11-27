package id.tukangkilat.app.data.api

import id.tukangkilat.shared.*
import retrofit2.http.*

interface ApiService {

    @GET("/announcements")
    suspend fun publicAnnouncements(): List<AnnouncementDto>

    @GET("/content/{code}")
    suspend fun publicContent(@Path("code") code: String): ContentDto

    @GET("/flags")
    suspend fun publicFlags(@Query("role") role: String): List<FeatureFlagDto>

    @GET("/health")
    suspend fun health(): Map<String, String>

    @GET("/server-info")
    suspend fun serverInfo(): Map<String, String>

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

    // ----- v4 commerce -----

    // ----- v5 trust -----
    @GET("/consumer/trust/devices")
    suspend fun trustedDevices(): List<TrustedDeviceDto>

    @POST("/consumer/trust/devices")
    suspend fun registerDevice(@Body req: RegisterDeviceRequest): TrustedDeviceDto

    @PUT("/consumer/trust/devices/{id}")
    suspend fun setDeviceTrust(@Path("id") id: Long, @Body req: Map<String, Boolean>): Map<String, String>

    @DELETE("/consumer/trust/devices/{id}")
    suspend fun removeDevice(@Path("id") id: Long)

    @GET("/consumer/trust/sessions")
    suspend fun sessions(): List<SessionDto>

    @POST("/consumer/trust/sessions")
    suspend fun recordSession(@Body req: Map<String, String>): SessionDto

    @POST("/consumer/trust/sessions/{id}/revoke")
    suspend fun revokeSession(@Path("id") id: Long): Map<String, String>

    @GET("/consumer/trust/consents")
    suspend fun consents(): List<ConsentDto>

    @PUT("/consumer/trust/consents")
    suspend fun saveConsent(@Body req: SaveConsentRequest): ConsentDto

    @GET("/consumer/trust/blocks")
    suspend fun blocks(): List<BlockDto>

    @POST("/consumer/trust/blocks")
    suspend fun blockUser(@Body req: Map<String, Long>): BlockDto

    @DELETE("/consumer/trust/blocks/{id}")
    suspend fun unblockUser(@Path("id") id: Long)

    @GET("/consumer/trust/login-alerts")
    suspend fun loginAlerts(): List<LoginAlertDto>

    @POST("/consumer/trust/login-alerts/{id}/read")
    suspend fun markAlertRead(@Path("id") id: Long): Map<String, String>

    @POST("/consumer/trust/sos")
    suspend fun createSos(@Body req: CreateSosRequest): SosAlertDto

    @GET("/consumer/trust/sos")
    suspend fun sosAlerts(): List<SosAlertDto>

    @GET("/consumer/trust/safe-visit/{orderId}")
    suspend fun safeVisit(@Path("orderId") orderId: Long): SafeVisitDto

    @GET("/consumer/trust/app-lock")
    suspend fun appLock(): AppLockDto

    @PUT("/consumer/trust/app-lock")
    suspend fun setPin(@Body req: SetPinRequest): AppLockDto

    @POST("/consumer/trust/app-lock/verify")
    suspend fun verifyPin(@Body req: VerifyPinRequest): Map<String, Boolean>

    @GET("/consumer/trust/two-factor")
    suspend fun twoFactor(): TwoFactorDto

    @POST("/consumer/trust/two-factor/request")
    suspend fun requestOtp(): TwoFactorDto

    @POST("/consumer/trust/two-factor/verify")
    suspend fun verifyOtp(@Body req: Map<String, String>): TwoFactorDto

    @DELETE("/consumer/trust/two-factor")
    suspend fun disableTwoFactor(): Map<String, String>

    @GET("/consumer/trust/privacy")
    suspend fun privacySnapshot(): PrivacySnapshotDto

    @GET("/consumer/trust/privacy/export")
    suspend fun exportPrivacyData(): String

    @DELETE("/consumer/trust/privacy")
    suspend fun deletePrivacyData(): Map<String, String>

    // ----- v5 marketplace -----
    @GET("/consumer/market/bundles")
    suspend fun marketBundles(): List<CustomBundleDto>

    @POST("/consumer/market/bundles")
    suspend fun createBundle(@Body req: CreateBundleRequest): CustomBundleDto

    @GET("/consumer/market/flash-sales")
    suspend fun flashSales(): List<FlashSaleDto>

    @GET("/consumer/market/group-buys")
    suspend fun groupBuys(): List<GroupBuyDto>

    @POST("/consumer/market/group-buys")
    suspend fun createGroupBuy(@Body req: CreateGroupBuyRequest): GroupBuyDto

    @POST("/consumer/market/group-buys/{id}/join")
    suspend fun joinGroupBuy(@Path("id") id: Long): GroupBuyDto

    @GET("/consumer/market/bids/{orderId}")
    suspend fun consumerBids(@Path("orderId") orderId: Long): List<BidDto>

    @POST("/consumer/market/bids/{bidId}/resolve")
    suspend fun resolveBid(@Path("bidId") bidId: Long, @Body req: ResolveBidRequest): BidDto

    @GET("/consumer/market/gift-cards")
    suspend fun giftCards(): List<GiftCardDto>

    @POST("/consumer/market/gift-cards")
    suspend fun createGiftCard(@Body req: Map<String, Long>): GiftCardDto

    @POST("/consumer/market/gift-cards/redeem")
    suspend fun redeemGift(@Body req: RedeemGiftRequest): GiftCardDto

    @GET("/consumer/market/point-items")
    suspend fun pointItems(): List<PointItemDto>

    @POST("/consumer/market/point-items/redeem")
    suspend fun redeemPoint(@Body req: RedeemPointRequest): PointRedemptionDto

    @POST("/consumer/market/tips")
    suspend fun createTip(@Body req: CreateTipRequest): TipDto

    @GET("/consumer/market/wishlist")
    suspend fun wishlist(): List<WishlistDto>

    @POST("/consumer/market/wishlist")
    suspend fun addWishlist(@Body req: AddWishlistRequest): WishlistDto

    @DELETE("/consumer/market/wishlist/{id}")
    suspend fun removeWishlist(@Path("id") id: Long)

    @GET("/consumer/market/price-alerts")
    suspend fun priceAlerts(): List<PriceAlertDto>

    @POST("/consumer/market/price-alerts")
    suspend fun addPriceAlert(@Body req: AddPriceAlertRequest): PriceAlertDto

    @GET("/consumer/market/price-history/{serviceId}")
    suspend fun priceHistory(@Path("serviceId") serviceId: Long): List<PricePointDto>
    @GET("/consumer/categories")
    suspend fun categories(): List<ServiceCategoryDto>

    @GET("/consumer/bundles")
    suspend fun bundles(): List<ServiceBundleDto>

    @GET("/consumer/search")
    suspend fun searchServices(
        @Query("q") query: String? = null,
        @Query("skill") skill: String? = null,
        @Query("categoryId") categoryId: Long? = null,
        @Query("maxPrice") maxPrice: Long? = null,
        @Query("minRating") minRating: Double? = null,
        @Query("sort") sort: String? = null
    ): List<ServiceSearchDto>

    @POST("/consumer/estimate")
    suspend fun estimate(@Body req: EstimateRequest): EstimateDto

    @GET("/consumer/vouchers")
    suspend fun vouchers(): List<VoucherDto>

    @GET("/consumer/referrals")
    suspend fun referrals(): List<ReferralDto>

    @POST("/consumer/referrals/redeem")
    suspend fun redeemReferral(@Body req: RedeemReferralRequest): ReferralDto

    @GET("/consumer/installments")
    suspend fun installments(): List<InstallmentDto>

    @POST("/consumer/installments")
    suspend fun createInstallment(@Body req: CreateInstallmentRequest): InstallmentDto

    @POST("/consumer/installments/{id}/pay")
    suspend fun payInstallment(@Path("id") id: Long): InstallmentDto

    @GET("/consumer/tiers")
    suspend fun tiers(): List<TierDto>

    @GET("/consumer/wallet/settings")
    suspend fun walletSettings(): WalletSettingsDto

    @PUT("/consumer/wallet/settings")
    suspend fun saveWalletSettings(@Body req: SaveWalletSettingsRequest): Map<String, String>

    @POST("/consumer/wallet/pay/{orderId}")
    suspend fun payWithWallet(@Path("orderId") orderId: Long): WalletPaymentResultDto

    @POST("/consumer/subscriptions/{id}/pause")
    suspend fun pauseSubscription(@Path("id") id: Long): Map<String, String>

    @POST("/consumer/subscriptions/{id}/resume")
    suspend fun resumeSubscription(@Path("id") id: Long): Map<String, String>

    @POST("/consumer/subscriptions/{id}/skip")
    suspend fun skipSubscription(@Path("id") id: Long): Map<String, String>


    @POST("/consumer/reviews/{orderId}/report")
    suspend fun reportReview(@Path("orderId") orderId: Long, @Body req: ReportReviewRequest): Map<String, String>
    @POST("/consumer/reviews/{orderId}/vote")
    suspend fun voteReview(@Path("orderId") orderId: Long, @Body req: ReviewVoteRequest): Map<String, String>

    @GET("/consumer/refunds")
    suspend fun refunds(): List<RefundRequestDto>

    @POST("/consumer/refunds")
    suspend fun createRefund(@Query("orderId") orderId: Long, @Body req: CreateRefundRequest): RefundRequestDto

    @GET("/consumer/preferences")
    suspend fun preferences(): PreferencesDto

    @PUT("/consumer/preferences")
    suspend fun savePreferences(@Body req: SavePreferencesRequest): Map<String, String>

    @GET("/consumer/offline-queue")
    suspend fun offlineQueue(): List<OfflineActionDto>

    @POST("/consumer/offline-queue")
    suspend fun enqueueOffline(@Body req: QueueActionRequest): OfflineActionDto

    @DELETE("/consumer/offline-queue")
    suspend fun clearOfflineQueue()

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

    @POST("/orders/{id}/signature")
    suspend fun saveSignature(@Path("id") id: Long, @Body req: SignatureRequest): SignatureDto

    @GET("/orders/{id}/signatures")
    suspend fun signatures(@Path("id") id: Long): List<SignatureDto>

    @GET("/orders/{id}/checklist")
    suspend fun checklist(@Path("id") id: Long): List<ChecklistItemDto>

    @POST("/orders/{id}/checklist/{itemId}")
    suspend fun updateChecklist(
        @Path("id") id: Long,
        @Path("itemId") itemId: Long,
        @Body req: UpdateChecklistRequest
    ): ChecklistItemDto

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


    // ----- v4 technician operations -----
    @GET("/technician/documents")
    suspend fun techDocuments(): List<TechDocumentDto>

    @POST("/technician/documents")
    suspend fun addTechDocument(@Body req: AddDocumentRequest): TechDocumentDto

    @GET("/technician/documents/expiring")
    suspend fun expiringDocuments(): List<TechDocumentDto>

    @GET("/technician/areas")
    suspend fun serviceAreas(): List<ServiceAreaDto>

    @POST("/technician/areas")
    suspend fun addServiceArea(@Body req: AddServiceAreaRequest): ServiceAreaDto

    @DELETE("/technician/areas/{id}")
    suspend fun deleteServiceArea(@Path("id") id: Long)

    @GET("/technician/auto-accept")
    suspend fun autoAccept(): AutoAcceptDto

    @PUT("/technician/auto-accept")
    suspend fun saveAutoAccept(@Body req: SaveAutoAcceptRequest): AutoAcceptDto

    @GET("/technician/route")
    suspend fun route(): List<RouteStopDto>

    @POST("/technician/orders/{id}/checkin")
    suspend fun checkin(@Path("id") id: Long, @Body req: CheckinRequest): CheckinDto

    @GET("/technician/orders/{id}/checkins")
    suspend fun checkins(@Path("id") id: Long): List<CheckinDto>

    @GET("/technician/materials")
    suspend fun materials(@Query("orderId") orderId: Long? = null): List<MaterialDto>

    @POST("/technician/materials")
    suspend fun addMaterial(@Body req: AddMaterialRequest): MaterialDto

    @GET("/technician/badges")
    suspend fun badges(): List<BadgeDto>

    @GET("/technician/ops-stats")
    suspend fun opsStats(): OpsStatsDto

    @GET("/technician/chat-templates")
    suspend fun chatTemplates(): List<TemplateDto>

    @POST("/technician/chat-templates")
    suspend fun addChatTemplate(@Body req: AddTemplateRequest): TemplateDto

    @DELETE("/technician/chat-templates/{id}")
    suspend fun deleteChatTemplate(@Path("id") id: Long)

    @GET("/technician/leaves")
    suspend fun leaves(): List<LeaveDto>

    @POST("/technician/leaves")
    suspend fun addLeave(@Body req: AddLeaveRequest): LeaveDto

    @GET("/technician/journal")
    suspend fun journal(): List<JournalDto>

    @POST("/technician/journal")
    suspend fun addJournal(@Body req: AddJournalRequest): JournalDto

    @GET("/technician/equipment")
    suspend fun equipment(): List<EquipmentDto>

    @PUT("/technician/equipment")
    suspend fun setEquipment(@Body req: SetEquipmentRequest): Map<String, String>


    @GET("/technician/market/bids/{orderId}")
    suspend fun technicianBids(@Path("orderId") orderId: Long): List<BidDto>

    @POST("/technician/market/bids")
    suspend fun placeBid(@Body req: PlaceBidRequest): BidDto
    @GET("/technician/review-details")
    suspend fun reviewDetails(): List<ReviewDetailDto>

    @POST("/technician/reviews/{orderId}/reply")
    suspend fun replyReview(@Path("orderId") orderId: Long, @Body req: ReviewReplyRequest): ReviewDetailDto

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




    // ----- v5 trust admin -----
    @GET("/admin/trust/sos")
    suspend fun adminSos(@Query("status") status: String? = null): List<SosAlertDto>

    @POST("/admin/trust/sos/{id}/resolve")
    suspend fun resolveSos(@Path("id") id: Long): Map<String, String>

    @GET("/admin/trust/subadmins")
    suspend fun subadmins(): List<SubadminDto>

    @POST("/admin/trust/subadmins")
    suspend fun saveSubadmin(@Body req: SaveSubadminRequest): SubadminDto

    @GET("/admin/trust/incidents")
    suspend fun incidents(@Query("status") status: String? = null): List<IncidentDto>

    @POST("/admin/trust/incidents")
    suspend fun createIncident(@Body req: CreateIncidentRequest): IncidentDto

    @POST("/admin/trust/incidents/{id}/resolve")
    suspend fun resolveIncident(@Path("id") id: Long, @Body req: Map<String, String>): Map<String, String>

    @GET("/admin/trust/retention")
    suspend fun retentionPolicies(): List<RetentionDto>

    @PUT("/admin/trust/retention/{code}")
    suspend fun saveRetention(@Path("code") code: String, @Body req: SaveRetentionRequest): RetentionDto

    @GET("/admin/trust/backups")
    suspend fun backupJobs(): List<BackupJobDto>

    @POST("/admin/trust/backups")
    suspend fun saveBackupJob(@Body req: SaveBackupJobRequest): BackupJobDto

    @POST("/admin/trust/backups/{id}/run")
    suspend fun runBackupJob(@Path("id") id: Long): BackupJobDto
    @GET("/admin/technician-documents")
    suspend fun adminDocuments(@Query("status") status: String? = null): List<TechDocumentDto>

    @POST("/admin/technician-documents/{id}/verify")
    suspend fun verifyDocument(@Path("id") id: Long, @Body req: VerifyDocumentRequest): TechDocumentDto

    @GET("/admin/technician-leaves")
    suspend fun adminLeaves(): List<LeaveDto>

    @POST("/admin/technician-leaves/{id}/resolve")
    suspend fun resolveLeave(@Path("id") id: Long, @Body req: Map<String, Boolean>): Map<String, String>

    @GET("/admin/refunds")
    suspend fun adminRefunds(): List<RefundRequestDto>

    @POST("/admin/refunds/{id}/resolve")
    suspend fun resolveRefund(@Path("id") id: Long, @Body req: ResolveRefundRequest): RefundRequestDto

    @POST("/admin/vouchers/issue")
    suspend fun issueVoucher(@Body req: Map<String, Long>): VoucherDto
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


    // ----- v4 platform -----

    @GET("/admin/market/summary")
    suspend fun marketSummary(): MarketSummaryDto

    @GET("/admin/market/ledger")
    suspend fun ledger(): List<LedgerEntryDto>

    @POST("/admin/market/ledger")
    suspend fun addLedger(@Body req: AddLedgerRequest): LedgerEntryDto

    @GET("/admin/market/commission-rules")
    suspend fun commissionRules(): List<CommissionRuleDto>

    @PUT("/admin/market/commission-rules/{code}")
    suspend fun saveCommission(@Path("code") code: String, @Body req: SaveCommissionRequest): CommissionRuleDto

    @POST("/admin/market/flash-sales")
    suspend fun createFlashSale(@Body req: Map<String, Int>): FlashSaleDto

    @POST("/admin/market/price-history")
    suspend fun recordPrice(@Body req: Map<String, Long>): Map<String, String>
    @GET("/admin/platform-summary")
    suspend fun platformSummary(): PlatformSummaryDto

    @GET("/admin/flags")
    suspend fun featureFlags(): List<FeatureFlagDto>

    @PUT("/admin/flags/{key}")
    suspend fun setFeatureFlag(@Path("key") key: String, @Body req: SaveFeatureFlagRequest): FeatureFlagDto

    @GET("/admin/content")
    suspend fun adminContent(): List<ContentPageDto>

    @PUT("/admin/content")
    suspend fun saveContent(@Body req: SaveContentRequest): ContentPageDto

    @GET("/admin/broadcasts")
    suspend fun broadcasts(): List<BroadcastDto>

    @POST("/admin/broadcasts")
    suspend fun createBroadcast(@Body req: CreateBroadcastRequest): BroadcastDto

    @GET("/admin/review-reports")
    suspend fun reviewReports(): List<ReviewReportDto>

    @POST("/admin/review-reports/{id}/resolve")
    suspend fun resolveReviewReport(@Path("id") id: Long, @Body req: ResolveReviewReportRequest): Map<String, String>

    @GET("/admin/sla-policies")
    suspend fun slaPolicies(): List<SlaPolicyDto>

    @PUT("/admin/sla-policies/{code}")
    suspend fun saveSlaPolicy(@Path("code") code: String, @Body req: SaveSlaPolicyRequest): SlaPolicyDto

    @GET("/admin/sla-breaches")
    suspend fun slaBreaches(): List<SlaBreachDto>

    @POST("/admin/sla-breaches/{id}/compensate")
    suspend fun compensateBreach(@Path("id") id: Long): SlaBreachDto

    @POST("/admin/notes")
    suspend fun addAdminNote(@Body req: AddAdminNoteRequest): AdminNoteDto

    @GET("/admin/weekly-reports")
    suspend fun weeklyReports(): List<WeeklyReportDto>

    @POST("/admin/weekly-reports/generate")
    suspend fun generateWeeklyReport(): WeeklyReportDto
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
