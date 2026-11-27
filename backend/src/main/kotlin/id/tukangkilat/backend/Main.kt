package id.tukangkilat.backend

import id.tukangkilat.shared.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.event.Level
import java.io.File
import java.time.LocalDate

fun main() {
    val config = AppConfig.fromEnv()
    ensureUploadDir(config.uploadDir)
    Db.migrate(config)
    Db.connect(config)
    Seed.seedIfEmpty(config)
    Seed.ensureSystemUser()

    embeddedServer(Netty, port = config.port, host = "0.0.0.0") {
        module(config)
    }.start(wait = true)
}

fun Application.module(config: AppConfig = AppConfig.fromEnv()) {
    installErrorPages()
    ensureUploadDir(config.uploadDir)

    val jwt = JwtService(config.jwtSecret, config.jwtIssuer)
    val authService = AuthService()
    val loginRateLimiter = RateLimiter(maxRequests = 10, windowMs = 60_000)

    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        })
    }

    install(CallLogging) {
        level = Level.INFO
        // logs method + path + status only; never headers or bodies with secrets
    }

    install(WebSockets)

    install(Authentication) {
        jwt("auth-jwt") {
            realm = "tukangkilat"
            verifier(jwt.verifier)
            validate { credential -> JWTPrincipal(credential.payload) }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, ApiError("UNAUTHORIZED", "Token tidak valid atau kedaluwarsa"))
            }
        }
    }

    // Background maintenance: payment expiry, offer expiry, dispatch, auto-complete
    if (config.startDispatcher) {
        val periodMs = config.dispatcherTickMs
        launch {
            while (true) {
                try {
                    EcosystemService.applyAutoSchedules()
                    DispatchService.dispatchRound()
                    DispatchService.maintenanceRound()
                    EcosystemService.processDueSubscriptions()
                } catch (e: Exception) {
                    environment.log.error("Dispatcher tick failed", e)
                }
                kotlinx.coroutines.delay(periodMs)
            }
        }
    }

    routing {
        get("/health") { call.respond(mapOf("status" to "ok")) }
        get("/announcements") { call.respond(EcosystemService.announcements()) }

        // ---------- Auth (public) ----------
        route("/auth") {
            post("/register") {
                val req = call.receive<RegisterRequest>()
                call.respond(HttpStatusCode.Created, authService.registerConsumer(req, jwt))
            }
            post("/register-technician") {
                val req = call.receive<RegisterTechnicianRequest>()
                call.respond(HttpStatusCode.Created, authService.registerTechnician(req))
            }
            post("/register/photo") {
                Seed.ensureSystemUser()
                val multipart = call.receiveMultipart()
                var saved: PhotoService.PhotoDto? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val bytes = part.streamProvider().readBytes()
                        saved = PhotoService.save(config.uploadDir, 0L, bytes, part.originalFileName ?: "photo")
                    }
                    part.dispose()
                }
                val photo = saved ?: throw BadRequestException("Tidak ada berkas yang diunggah")
                call.respond(HttpStatusCode.Created, mapOf("url" to photo.url, "mime" to photo.mime))
            }
            post("/login") {
                val clientIp = call.request.local.remoteHost ?: "unknown"
                if (!loginRateLimiter.allow("login:$clientIp")) {
                    throw RateLimitedException("Terlalu banyak percobaan masuk dari perangkat ini. Coba lagi sebentar lagi")
                }
                val req = call.receive<LoginRequest>()
                call.respond(authService.login(req, jwt))
            }
            post("/refresh") {
                val req = call.receive<RefreshRequest>()
                call.respond(authService.refresh(req, jwt))
            }
            post("/logout") {
                val req = call.receive<RefreshRequest>()
                authService.logout(req.refreshToken)
                call.respond(mapOf("status" to "ok"))
            }
        }

        // ---------- Account (any authenticated role) ----------
        authenticate("auth-jwt") {
            post("/account/password") {
                val user = call.authUser()
                val req = call.receive<ChangePasswordRequest>()
                authService.changePassword(user.id, req.oldPassword, req.newPassword)
                call.respond(mapOf("status" to "ok"))
            }
            post("/photos") {
                val user = call.authUser()
                val multipart = call.receiveMultipart()
                var saved: PhotoService.PhotoDto? = null
                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val bytes = part.streamProvider().readBytes()
                        saved = PhotoService.save(config.uploadDir, user.id, bytes, part.originalFileName ?: "photo")
                    }
                    part.dispose()
                }
                val photo = saved ?: throw BadRequestException("Tidak ada berkas yang diunggah")
                call.respond(HttpStatusCode.Created, mapOf("url" to photo.url, "mime" to photo.mime))
            }
            get("/photos/{publicId}") {
                val user = call.authUser()
                val publicId = call.parameters["publicId"]!!
                val (file, mime) = PhotoService.resolveForRead(config.uploadDir, publicId, user)
                call.respondBytes(file.readBytes(), ContentType.parse(mime))
            }
        }

        // ---------- Consumer API ----------
        authenticate("auth-jwt") {
            route("/consumer") {
                get("/wallet") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(EcosystemService.wallet(call.authUser().id))
                }
                post("/wallet/topup") {
                    call.requireRole(Role.KONSUMEN)
                    val req = call.receive<TopUpRequest>()
                    call.respond(EcosystemService.topUp(call.authUser().id, req.amount))
                }
                get("/loyalty") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(EcosystemService.loyalty(call.authUser().id))
                }
                post("/loyalty/redeem") {
                    call.requireRole(Role.KONSUMEN)
                    val body = call.receive<Map<String, Int>>()
                    call.respond(EcosystemService.redeem(call.authUser().id, body["points"] ?: 0))
                }
                get("/subscriptions") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(EcosystemService.subscriptions(call.authUser().id))
                }
                post("/subscriptions") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(HttpStatusCode.Created, EcosystemService.addSubscription(call.authUser().id, call.receive()))
                }
                delete("/subscriptions/{id}") {
                    call.requireRole(Role.KONSUMEN)
                    EcosystemService.cancelSubscription(call.authUser().id, call.parameters["id"]!!.toLong())
                    call.respond(HttpStatusCode.NoContent)
                }
                get("/services") {
                    call.requireRole(Role.KONSUMEN); call.respond(OrderService.listServices())
                }
                get("/direct-peers") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(EcosystemService.directPeers(call.authUser().id, true))
                }
                post("/direct-peers/{peerId}/read") {
                    call.requireRole(Role.KONSUMEN)
                    EcosystemService.markDirectRead(call.authUser().id, call.parameters["peerId"]!!.toLong())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/messages/{messageId}/report") {
                    call.requireRole(Role.KONSUMEN)
                    val body = call.receive<ReportMessageRequest>()
                    call.respond(EcosystemService.reportMessage(call.authUser().id, body.copy(messageId = call.parameters["messageId"]!!.toLong())))
                }
                get("/technicians/{technicianId}/messages") {
                    call.requireRole(Role.KONSUMEN)
                    val peer = call.parameters["technicianId"]!!.toLong()
                    call.respond(NegotiationService.listMessages(call.authUser().id, peer))
                }
                post("/technicians/{technicianId}/messages") {
                    call.requireRole(Role.KONSUMEN)
                    val peer = call.parameters["technicianId"]!!.toLong()
                    val req = call.receive<SendDirectMessageRequest>()
                    call.respond(NegotiationService.sendMessage(call.authUser().id, peer, req.body, true))
                }
                get("/proposals") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(NegotiationService.proposalsFor(call.authUser().id, true))
                }
                post("/proposals/{id}/accept") {
                    call.requireRole(Role.KONSUMEN)
                    val id = call.parameters["id"]!!.toLong()
                    NegotiationService.respond(call.authUser().id, id, true)
                    val orderId = NegotiationService.createOrderFromProposal(id)
                    call.respond(mapOf("orderId" to orderId))
                }
                post("/proposals/{id}/reject") {
                    call.requireRole(Role.KONSUMEN)
                    val id = call.parameters["id"]!!.toLong()
                    call.respond(NegotiationService.respond(call.authUser().id, id, false))
                }
                get("/technicians") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(
                        ConsumerBrowseService.technicians(
                            call.request.queryParameters["skill"],
                            call.request.queryParameters["q"],
                            call.request.queryParameters["lat"]?.toDoubleOrNull(),
                            call.request.queryParameters["lng"]?.toDoubleOrNull()
                        )
                    )
                }
                get("/slots") {
                    call.requireRole(Role.KONSUMEN)
                    val date = call.request.queryParameters["date"]?.let {
                        try { LocalDate.parse(it) } catch (e: Exception) { throw BadRequestException("Format date harus YYYY-MM-DD") }
                    } ?: LocalDate.now()
                    call.respond(OrderService.listSlots(date))
                }
                get("/addresses") { call.requireRole(Role.KONSUMEN); call.respond(OrderService.listAddresses(call.authUser().id)) }
                post("/addresses") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(HttpStatusCode.Created, OrderService.addAddress(call.authUser().id, call.receive()))
                }
                put("/addresses/{id}") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(OrderService.updateAddress(call.authUser().id, call.parameters["id"]!!.toLong(), call.receive()))
                }
                delete("/addresses/{id}") {
                    call.requireRole(Role.KONSUMEN)
                    OrderService.deleteAddress(call.authUser().id, call.parameters["id"]!!.toLong())
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/orders") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(HttpStatusCode.Created, OrderService.createOrder(call.authUser().id, call.receive()))
                }
                get("/orders") { call.requireRole(Role.KONSUMEN); call.respond(OrderService.listMyOrders(call.authUser().id)) }
                get("/orders/{id}") { call.requireRole(Role.KONSUMEN, Role.TUKANG, Role.ADMIN); call.respond(OrderService.getOrder(call.parameters["id"]!!.toLong(), call.authUser())) }
                post("/orders/{id}/pay") {
                    call.requireRole(Role.KONSUMEN)
                    val orderId = call.parameters["id"]!!.toLong()
                    val response = OrderService.pay(orderId, call.authUser().id, call.receive())
                    if (response.method == PaymentMethod.TUNAI) {
                        DispatchService.dispatchRound()
                    }
                    call.respond(response)
                }
                post("/orders/{id}/cancel") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(OrderService.cancel(call.parameters["id"]!!.toLong(), call.authUser().id, call.receive()))
                }
                post("/orders/{id}/reschedule") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(OrderService.reschedule(call.parameters["id"]!!.toLong(), call.authUser().id, call.receive()))
                }
                post("/orders/{id}/confirm") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(OrderService.confirm(call.parameters["id"]!!.toLong(), call.authUser().id, ConfirmOrderRequest()))
                }
                post("/orders/{id}/report") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(OrderService.report(call.parameters["id"]!!.toLong(), call.authUser().id, call.receive()))
                }
                post("/orders/{id}/rating") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(OrderService.rate(call.parameters["id"]!!.toLong(), call.authUser().id, call.receive()))
                }
                get("/orders/{id}/messages") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(listMessages(call.parameters["id"]!!.toLong(), call.authUser()))
                }
                // ---------- v4 order extras ----------
                post("/orders/{id}/signature") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    val body = call.receive<SignatureRequest>()
                    call.respond(CommerceService.saveSignature(call.authUser().id, call.parameters["id"]!!.toLong(), body.role, body.dataUrl))
                }
                get("/orders/{id}/signatures") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(CommerceService.signatures(call.parameters["id"]!!.toLong()))
                }
                get("/orders/{id}/checklist") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(CommerceService.checklist(call.parameters["id"]!!.toLong()))
                }
                post("/orders/{id}/checklist/{itemId}") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(CommerceService.updateChecklistItem(
                        call.parameters["id"]!!.toLong(), call.parameters["itemId"]!!.toLong(), call.receive()
                    ))
                }
                post("/orders/{id}/messages") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    val orderId = call.parameters["id"]!!.toLong()
                    val body = call.receive<SendMessageRequest>()
                    call.respond(HttpStatusCode.Created, sendMessage(orderId, call.authUser(), body))
                }
                // ---------- v4 commerce (consumer) ----------
                get("/categories") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.categories()) }
                get("/bundles") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.bundles()) }
                get("/search") {
                    call.requireRole(Role.KONSUMEN)
                    val q = call.request.queryParameters
                    call.respond(CommerceService.search(
                        q["q"], q["skill"], q["categoryId"]?.toLongOrNull(),
                        q["maxPrice"]?.toLongOrNull(), q["minRating"]?.toDoubleOrNull(), q["sort"]
                    ))
                }
                post("/estimate") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(CommerceService.estimate(call.authUser().id, call.receive()))
                }
                get("/vouchers") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.vouchers(call.authUser().id)) }
                get("/referrals") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.referrals(call.authUser().id)) }
                post("/referrals/redeem") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(CommerceService.redeemReferral(call.authUser().id, call.receive<RedeemReferralRequest>().code))
                }
                get("/installments") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.installments(call.authUser().id)) }
                post("/installments") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(HttpStatusCode.Created, CommerceService.createInstallment(call.authUser().id, call.receive()))
                }
                post("/installments/{id}/pay") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(CommerceService.payInstallment(call.authUser().id, call.parameters["id"]!!.toLong()))
                }
                get("/tiers") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.tiers(call.authUser().id)) }
                get("/wallet/settings") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.walletSettings(call.authUser().id)) }
                put("/wallet/settings") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.saveWalletSettings(call.authUser().id, call.receive())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/wallet/pay/{orderId}") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(CommerceService.payWithWallet(call.authUser().id, call.parameters["orderId"]!!.toLong()))
                }
                post("/subscriptions/{id}/pause") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.pauseSubscription(call.authUser().id, call.parameters["id"]!!.toLong())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/subscriptions/{id}/resume") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.resumeSubscription(call.authUser().id, call.parameters["id"]!!.toLong())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/subscriptions/{id}/skip") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.skipSubscription(call.authUser().id, call.parameters["id"]!!.toLong())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/reviews/{orderId}/vote") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.voteReview(call.authUser().id, call.parameters["orderId"]!!.toLong(), call.receive<ReviewVoteRequest>().helpful)
                    call.respond(mapOf("status" to "ok"))
                }
                get("/refunds") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.refunds(call.authUser().id)) }
                post("/refunds") {
                    call.requireRole(Role.KONSUMEN)
                    val orderId = call.request.queryParameters["orderId"]!!.toLong()
                    call.respond(HttpStatusCode.Created, CommerceService.createRefund(call.authUser().id, orderId, call.receive()))
                }
                get("/preferences") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.preferences(call.authUser().id)) }
                put("/preferences") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.savePreferences(call.authUser().id, call.receive())
                    call.respond(mapOf("status" to "ok"))
                }
                get("/offline-queue") { call.requireRole(Role.KONSUMEN); call.respond(CommerceService.queue(call.authUser().id)) }
                post("/offline-queue") {
                    call.requireRole(Role.KONSUMEN)
                    call.respond(HttpStatusCode.Created, CommerceService.enqueue(call.authUser().id, call.receive()))
                }
                delete("/offline-queue") {
                    call.requireRole(Role.KONSUMEN)
                    CommerceService.clearQueue(call.authUser().id)
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }

        // ---------- Technician API ----------
        authenticate("auth-jwt") {
            route("/technician") {
                get("/availability") {
                call.requireRole(Role.TUKANG)
                call.respond(EcosystemService.blocks(call.authUser().id))
            }
            post("/availability") {
                call.requireRole(Role.TUKANG)
                call.respond(HttpStatusCode.Created, EcosystemService.addBlock(call.authUser().id, call.receive()))
            }
            delete("/availability/{id}") {
                call.requireRole(Role.TUKANG)
                EcosystemService.deleteBlock(call.authUser().id, call.parameters["id"]!!.toLong())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/portfolio") {
                call.requireRole(Role.TUKANG)
                call.respond(EcosystemService.portfolio(call.authUser().id))
            }
            post("/portfolio") {
                call.requireRole(Role.TUKANG)
                call.respond(HttpStatusCode.Created, EcosystemService.addPortfolio(call.authUser().id, call.receive()))
            }
            delete("/portfolio/{id}") {
                call.requireRole(Role.TUKANG)
                EcosystemService.deletePortfolio(call.authUser().id, call.parameters["id"]!!.toLong())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/payout-account") {
                call.requireRole(Role.TUKANG)
                val account = EcosystemService.payoutAccount(call.authUser().id)
                if (account != null) call.respond(account) else call.respond(HttpStatusCode.NoContent)
            }
            put("/payout-account") {
                call.requireRole(Role.TUKANG)
                EcosystemService.savePayoutAccount(call.authUser().id, call.receive())
                call.respond(mapOf("status" to "ok"))
            }
            get("/stats") {
                call.requireRole(Role.TUKANG)
                call.respond(EcosystemService.stats(call.authUser().id))
            }
            put("/online") {
                    call.requireRole(Role.TUKANG)
                    TechnicianService.setOnline(call.authUser().id, call.receive<OnlineRequest>().online)
                    call.respond(mapOf("status" to "ok"))
                }
                post("/location") {
                    call.requireRole(Role.TUKANG)
                    val req = call.receive<LocationRequest>()
                    TechnicianService.updateLocation(call.authUser().id, req.lat, req.lng)
                    call.respond(mapOf("status" to "ok"))
                }
                get("/profile") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.getProfile(call.authUser().id)) }
                put("/profile") {
                    call.requireRole(Role.TUKANG)
                    call.respond(TechnicianService.updateProfile(call.authUser().id, call.receive()))
                }
                get("/direct-peers") {
                call.requireRole(Role.TUKANG)
                call.respond(NegotiationService.recentPeers(call.authUser().id, false))
            }
            get("/direct-peers/summary") {
                call.requireRole(Role.TUKANG)
                call.respond(EcosystemService.directPeers(call.authUser().id, false))
            }
            post("/direct-peers/{peerId}/read") {
                call.requireRole(Role.TUKANG)
                EcosystemService.markDirectRead(call.authUser().id, call.parameters["peerId"]!!.toLong())
                call.respond(mapOf("status" to "ok"))
            }
            post("/messages/{messageId}/report") {
                call.requireRole(Role.TUKANG)
                val body = call.receive<ReportMessageRequest>()
                call.respond(EcosystemService.reportMessage(call.authUser().id, body.copy(messageId = call.parameters["messageId"]!!.toLong())))
            }
            get("/auto-schedule") {
                call.requireRole(Role.TUKANG)
                call.respond(EcosystemService.autoSchedules(call.authUser().id))
            }
            post("/auto-schedule") {
                call.requireRole(Role.TUKANG)
                call.respond(HttpStatusCode.Created, EcosystemService.saveAutoSchedule(call.authUser().id, call.receive()))
            }
            delete("/auto-schedule/{id}") {
                call.requireRole(Role.TUKANG)
                EcosystemService.deleteAutoSchedule(call.authUser().id, call.parameters["id"]!!.toLong())
                call.respond(HttpStatusCode.NoContent)
            }
            get("/direct-messages/{consumerId}") {
                call.requireRole(Role.TUKANG)
                val peer = call.parameters["consumerId"]!!.toLong()
                call.respond(NegotiationService.listMessages(call.authUser().id, peer))
            }
            post("/direct-messages/{consumerId}") {
                call.requireRole(Role.TUKANG)
                val peer = call.parameters["consumerId"]!!.toLong()
                val req = call.receive<SendDirectMessageRequest>()
                call.respond(NegotiationService.sendMessage(call.authUser().id, peer, req.body, false))
            }
            get("/proposals") {
                call.requireRole(Role.TUKANG)
                call.respond(NegotiationService.proposalsFor(call.authUser().id, false))
            }
            post("/proposals") {
                call.requireRole(Role.TUKANG)
                call.respond(HttpStatusCode.Created, NegotiationService.createProposal(call.authUser().id, call.receive()))
            }
            get("/offers") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.listOffers(call.authUser().id)) }
                post("/offers/{id}/accept") {
                    call.requireRole(Role.TUKANG)
                    call.respond(TechnicianService.acceptOffer(call.authUser().id, call.parameters["id"]!!.toLong()))
                }
                post("/offers/{id}/reject") {
                    call.requireRole(Role.TUKANG)
                    TechnicianService.rejectOffer(call.authUser().id, call.parameters["id"]!!.toLong(), call.receive())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/orders/{id}/depart") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.jobAction(call.authUser().id, call.parameters["id"]!!.toLong(), "depart")) }
                post("/orders/{id}/arrive") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.jobAction(call.authUser().id, call.parameters["id"]!!.toLong(), "arrive")) }
                post("/orders/{id}/start") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.jobAction(call.authUser().id, call.parameters["id"]!!.toLong(), "start")) }
                post("/orders/{id}/finish") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.jobAction(call.authUser().id, call.parameters["id"]!!.toLong(), "finish", call.receive())) }
                get("/orders") { call.requireRole(Role.TUKANG); call.respond(OrderService.listOrdersForTechnician(call.authUser().id)) }
                get("/earnings") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.earnings(call.authUser().id)) }
                get("/withdrawals") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.withdrawals(call.authUser().id)) }
                post("/withdrawals") {
                    call.requireRole(Role.TUKANG)
                    call.respond(TechnicianService.withdraw(call.authUser().id, call.receive()))
                }
                get("/reviews") { call.requireRole(Role.TUKANG); call.respond(TechnicianService.reviews(call.authUser().id)) }
            // ---------- v4 commerce (technician) ----------
            get("/review-details") {
                call.requireRole(Role.TUKANG)
                call.respond(CommerceService.reviewDetails(call.authUser().id))
            }
            post("/reviews/{orderId}/reply") {
                call.requireRole(Role.TUKANG)
                call.respond(CommerceService.replyReview(call.authUser().id, call.parameters["orderId"]!!.toLong(), call.receive<ReviewReplyRequest>().reply))
            }
                get("/orders/{id}/messages") {
                    call.requireRole(Role.TUKANG)
                    call.respond(listMessages(call.parameters["id"]!!.toLong(), call.authUser()))
                }
                post("/orders/{id}/messages") {
                    call.requireRole(Role.TUKANG)
                    val orderId = call.parameters["id"]!!.toLong()
                    call.respond(HttpStatusCode.Created, sendMessage(orderId, call.authUser(), call.receive()))
                }
            }
        }

        // ---------- Admin API ----------
        authenticate("auth-jwt") {
            route("/admin") {
                get("/analytics") { call.requireRole(Role.ADMIN); call.respond(AdminService.analytics()) }
            get("/disputes") {
                call.requireRole(Role.ADMIN)
                call.respond(EcosystemService.disputes())
            }
            post("/disputes/{id}/resolve") {
                call.requireRole(Role.ADMIN)
                val id = call.parameters["id"]!!.toLong()
                val result = EcosystemService.resolveDispute(call.authUser().id, id, call.receive())
                EcosystemService.log(call.authUser().id, "RESOLVE_DISPUTE", id.toString())
                call.respond(result)
            }
            get("/audit") {
                call.requireRole(Role.ADMIN)
                call.respond(EcosystemService.audit())
            }
            get("/announcements") {
                call.requireRole(Role.ADMIN)
                call.respond(EcosystemService.announcements())
            }
            post("/announcements") {
                call.requireRole(Role.ADMIN)
                val result = EcosystemService.announce(call.authUser().id, call.receive())
                EcosystemService.log(call.authUser().id, "ANNOUNCE", result.id.toString())
                call.respond(HttpStatusCode.Created, result)
            }
            get("/config") {
                call.requireRole(Role.ADMIN)
                call.respond(EcosystemService.configs())
            }
            put("/config/{key}") {
                call.requireRole(Role.ADMIN)
                val key = call.parameters["key"]!!
                val body = call.receive<Map<String, String>>()
                EcosystemService.setConfig(key, body["value"] ?: "")
                EcosystemService.log(call.authUser().id, "SET_CONFIG", key)
                call.respond(mapOf("status" to "ok"))
            }
            get("/leaders") {
                call.requireRole(Role.ADMIN)
                call.respond(EcosystemService.leaders())
            }
            get("/refunds") {
                call.requireRole(Role.ADMIN)
                call.respond(CommerceService.adminRefunds())
            }
            post("/refunds/{id}/resolve") {
                call.requireRole(Role.ADMIN)
                call.respond(CommerceService.resolveRefund(call.authUser().id, call.parameters["id"]!!.toLong(), call.receive()))
            }
            post("/vouchers/issue") {
                call.requireRole(Role.ADMIN)
                val body = call.receive<Map<String, Long>>()
                val consumerId = body["consumerId"] ?: throw BadRequestException("consumerId wajib")
                val amount = body["amount"] ?: throw BadRequestException("amount wajib")
                call.respond(HttpStatusCode.Created, CommerceService.issueVoucher(consumerId, amount))
            }
            get("/message-reports") {
                call.requireRole(Role.ADMIN)
                call.respond(EcosystemService.messageReports())
            }
            post("/message-reports/{id}/resolve") {
                call.requireRole(Role.ADMIN)
                val body = call.receive<Map<String, Boolean>>()
                EcosystemService.resolveMessageReport(
                    call.authUser().id,
                    call.parameters["id"]!!.toLong(),
                    body["hideMessage"] ?: false
                )
                EcosystemService.log(call.authUser().id, "RESOLVE_MESSAGE", call.parameters["id"]!!)
                call.respond(mapOf("status" to "ok"))
            }
            get("/export/orders") {
                call.requireRole(Role.ADMIN)
                val from = call.request.queryParameters["from"]
                val to = call.request.queryParameters["to"]
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"pesanan.csv\"")
                call.respondText(EcosystemService.ordersCsv(from, to), ContentType("text", "csv").withCharset(Charsets.UTF_8))
            }
            get("/export/wallet") {
                call.requireRole(Role.ADMIN)
                call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"dompet.csv\"")
                call.respondText(EcosystemService.walletCsv(), ContentType("text", "csv").withCharset(Charsets.UTF_8))
            }
            get("/summary") { call.requireRole(Role.ADMIN); call.respond(AdminService.summary()) }
                get("/technicians") {
                    call.requireRole(Role.ADMIN)
                    call.respond(AdminService.listTechnicians(call.request.queryParameters["status"]))
                }
                post("/technicians/{id}/approve") {
                    call.requireRole(Role.ADMIN)
                    AdminService.approveTechnician(call.parameters["id"]!!.toLong())
                    call.respond(mapOf("status" to "ok"))
                }
                post("/technicians/{id}/reject") {
                    call.requireRole(Role.ADMIN)
                    AdminService.rejectTechnician(call.parameters["id"]!!.toLong(), call.receive<RejectTechnicianRequest>().reason)
                    call.respond(mapOf("status" to "ok"))
                }
                get("/reports") {
                    call.requireRole(Role.ADMIN)
                    call.respond(AdminService.listReports(call.request.queryParameters["status"]))
                }
                post("/reports/{id}/revisit") {
                    call.requireRole(Role.ADMIN)
                    AdminService.revisitReport(call.parameters["id"]!!.toLong(), call.authUser().id)
                    call.respond(mapOf("status" to "ok"))
                }
                post("/reports/{id}/close") {
                    call.requireRole(Role.ADMIN)
                    AdminService.closeReport(call.parameters["id"]!!.toLong())
                    call.respond(mapOf("status" to "ok"))
                }
                get("/orders") {
                    call.requireRole(Role.ADMIN)
                    val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                    call.respond(AdminService.listOrders(call.request.queryParameters["status"], limit))
                }
                get("/services") { call.requireRole(Role.ADMIN); call.respond(AdminService.listServices()) }
                post("/services") {
                    call.requireRole(Role.ADMIN)
                    call.respond(HttpStatusCode.Created, AdminService.createService(call.receive()))
                }
                get("/promo-codes") { call.requireRole(Role.ADMIN); call.respond(PromoService.list()) }
                put("/promo-codes") {
                    call.requireRole(Role.ADMIN)
                    call.respond(PromoService.upsert(call.receive()))
                }
                put("/services/{id}") {
                    call.requireRole(Role.ADMIN)
                    call.respond(AdminService.updateService(call.parameters["id"]!!.toLong(), call.receive()))
                }
                post("/users/{id}/status") {
                    call.requireRole(Role.ADMIN)
                    AdminService.setUserStatus(call.parameters["id"]!!.toLong(), call.receive<UserStatusUpdateRequest>().status)
                    call.respond(mapOf("status" to "ok"))
                }
                post("/payments/{orderId}/simulate-success") {
                    call.requireRole(Role.ADMIN)
                    AdminService.simulatePaymentSuccess(call.parameters["orderId"]!!.toLong(), call.authUser().id)
                    call.respond(mapOf("status" to "ok"))
                }
                post("/payments/{orderId}/simulate-fail") {
                    call.requireRole(Role.ADMIN)
                    AdminService.simulatePaymentFail(call.parameters["orderId"]!!.toLong(), call.authUser().id)
                    call.respond(mapOf("status" to "ok"))
                }
            }
        }

        // ---------- Shared order extras (consumer and technician) ----------
        authenticate("auth-jwt") {
            route("/orders") {
                post("/{id}/signature") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    val body = call.receive<SignatureRequest>()
                    call.respond(CommerceService.saveSignature(call.authUser().id, call.parameters["id"]!!.toLong(), body.role, body.dataUrl))
                }
                get("/{id}/signatures") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(CommerceService.signatures(call.parameters["id"]!!.toLong()))
                }
                get("/{id}/checklist") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(CommerceService.checklist(call.parameters["id"]!!.toLong()))
                }
                post("/{id}/checklist/{itemId}") {
                    call.requireRole(Role.KONSUMEN, Role.TUKANG)
                    call.respond(CommerceService.updateChecklistItem(
                        call.parameters["id"]!!.toLong(), call.parameters["itemId"]!!.toLong(), call.receive()
                    ))
                }
            }
        }

        // ---------- WebSocket ----------
        webSocket("/ws") {
            val token = call.request.queryParameters["token"]
            val claims = token?.let { runCatching { jwt.parseAccessToken(it) }.getOrNull() }
            if (claims == null) {
                close(CloseReason(4401, "Token tidak valid"))
                return@webSocket
            }
            val userId = claims.subject.toLong()
            WsHub.add(userId, this)
            try {
                for (frame in incoming) {
                    // currently server-push only; incoming frames are ignored
                }
            } finally {
                WsHub.remove(userId, this)
            }
        }
    }
}

private suspend fun listMessages(orderId: Long, user: AuthUser): List<MessageDto> = transaction {
    val row = OrderQueries.loadOrderRow(orderId)
    OrderQueries.assertCanAccess(row, user)
    val senders = UsersTable.selectAll().where { UsersTable.id inList MessagesTable.selectAll().where { MessagesTable.orderId eq orderId }.map { it[MessagesTable.senderId] } }.associateBy({ it[UsersTable.id] }, { it[UsersTable.fullName] })
    MessagesTable.selectAll().where { MessagesTable.orderId eq orderId }
        .orderBy(MessagesTable.createdAt to org.jetbrains.exposed.sql.SortOrder.ASC, MessagesTable.id to org.jetbrains.exposed.sql.SortOrder.ASC)
        .map {
            MessageDto(
                id = it[MessagesTable.id],
                orderId = orderId,
                senderId = it[MessagesTable.senderId],
                senderName = senders[it[MessagesTable.senderId]] ?: "",
                body = it[MessagesTable.body],
                createdAt = it[MessagesTable.createdAt].toString()
            )
        }
}

private suspend fun sendMessage(orderId: Long, user: AuthUser, req: SendMessageRequest): MessageDto {
    val body = req.body.trim()
    if (body.isBlank()) throw BadRequestException("Pesan tidak boleh kosong")
    if (body.length > 2000) throw BadRequestException("Pesan maksimal 2000 karakter")
    val message = transaction {
        val row = OrderQueries.loadOrderRow(orderId)
        OrderQueries.assertCanAccess(row, user)
        val id = MessagesTable.insert {
            it[MessagesTable.orderId] = orderId
            it[senderId] = user.id
            it[MessagesTable.body] = body
            it[createdAt] = java.time.OffsetDateTime.now()
        } get MessagesTable.id
        id to row
    }
    // notify the other party
    val (messageId, row) = message
    val otherParty = when (user.role) {
        Role.KONSUMEN -> row[OrdersTable.technicianId]
        Role.TUKANG -> row[OrdersTable.consumerId]
        else -> null
    }
    val fullName = transaction { UsersTable.selectAll().where { UsersTable.id eq user.id }.single()[UsersTable.fullName] }
    val dto = MessageDto(
        id = messageId,
        orderId = orderId,
        senderId = user.id,
        senderName = fullName,
        body = body,
        createdAt = java.time.OffsetDateTime.now().toString()
    )
    if (otherParty != null) {
        WsHub.sendTo(otherParty, WsEvent(WsEventType.CHAT_MESSAGE, orderId))
    }
    return dto
}
