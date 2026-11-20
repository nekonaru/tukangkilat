package id.tukangkilat.backend

import id.tukangkilat.shared.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.application.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.OffsetDateTime
import kotlin.test.*

@kotlinx.serialization.Serializable
data class PhotoUploadDto(val url: String, val mime: String = "")

/**
 * Full end-to-end API tests against an in-memory H2 database seeded from the Flyway migration.
 * Each test class gets a fresh application instance; H2 is shared per JVM (DB_CLOSE_DELAY=-1),
 * and seed data is inserted only once into an empty database.
 */
class ApiIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun testConfig() = AppConfig(
        dbUrl = "jdbc:h2:mem:tukangkilat-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        dbUser = "sa",
        dbPassword = "",
        jwtSecret = "test-secret-key-that-is-long-enough-32bytes-min!!",
        jwtIssuer = "tukangkilat-test",
        uploadDir = "build/test-uploads",
        port = 0,
        startDispatcher = false,
        dispatcherTickMs = 60_000,
        seedDemoData = true,
        demoAdminPassword = "admin12345",
        demoConsumerPassword = "rina12345",
        demoTechPassword = "budi12345",
        demoTech2Password = "sari12345",
        h2InMemory = true
    )

    private fun ApplicationTestBuilder.setupApp() {
        val config = testConfig()
        // reset database per test class run? Keep it simple: migrate (idempotent) and seed if empty.
        Db.migrate(config)
        Db.connect(config)
        Seed.seedIfEmpty(config)
        application { module(config) }
    }

    private suspend fun HttpClient.login(username: String, password: String): HttpResponse {
        return post("/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(LoginRequest.serializer(), LoginRequest(username, password)))
        }
    }

    private suspend fun HttpClient.loginToken(username: String, password: String): String {
        val res = login(username, password)
        assertEquals(HttpStatusCode.OK, res.status, "login failed for $username: ${res.bodyAsText()}")
        return json.decodeFromString(LoginResponse.serializer(), res.bodyAsText()).accessToken
    }

    private fun HttpRequestBuilder.bearer(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
    }

    private suspend fun HttpClient.okGet(path: String, token: String): HttpResponse {
        val res = get(path) { bearer(token) }
        assertEquals(HttpStatusCode.OK, res.status, "GET $path failed: ${res.bodyAsText()}")
        return res
    }

    @Test
    fun `health endpoint is public`() = testApplication {
        setupApp()
        val res = client.get("/health")
        assertEquals(HttpStatusCode.OK, res.status)
        assertTrue(res.bodyAsText().contains("ok"))
    }

    @Test
    fun `login works for all three roles`() = testApplication {
        setupApp()
        for ((user, pass, expectedRole) in listOf(
            Triple("admin", "admin12345", Role.ADMIN),
            Triple("rina", "rina12345", Role.KONSUMEN),
            Triple("budi", "budi12345", Role.TUKANG)
        )) {
            val res = client.login(user, pass)
            assertEquals(HttpStatusCode.OK, res.status, "login failed for $user: ${res.bodyAsText()}")
            val body = json.decodeFromString(LoginResponse.serializer(), res.bodyAsText())
            assertEquals(expectedRole, body.role)
        }
    }

    @Test
    fun `wrong credentials get generic error without leaking account existence`() = testApplication {
        setupApp()
        val wrongPass = client.login("rina", "salah-salah")
        val noUser = client.login("tidakada123", "salah-salah")
        assertEquals(HttpStatusCode.Unauthorized, wrongPass.status)
        assertEquals(HttpStatusCode.Unauthorized, noUser.status)
        assertEquals(wrongPass.bodyAsText(), noUser.bodyAsText())
        assertTrue(wrongPass.bodyAsText().contains("Username atau password salah"))
    }

    @Test
    fun `unverified technician cannot login with clear message`() = testApplication {
        setupApp()
        val register = client.post("/auth/register-technician") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    RegisterTechnicianRequest.serializer(),
                    RegisterTechnicianRequest(
                        fullName = "Taufik Teknisi",
                        username = "taufik",
                        password = "taufik12345",
                        phone = "08121212121",
                        skills = listOf("LISTRIK"),
                        ktpPhotoUrl = "/photos/fake-ktp",
                        selfiePhotoUrl = "/photos/fake-selfie"
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, register.status, register.bodyAsText())
        val login = client.login("taufik", "taufik12345")
        assertEquals(HttpStatusCode.Forbidden, login.status)
        assertTrue(login.bodyAsText().contains("verifikasi"))
    }

    @Test
    fun `consumer cannot call technician or admin routes`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val tech = client.okGet("/consumer/services", consumer).status
        assertEquals(HttpStatusCode.OK, tech)

        val t1 = client.get("/technician/offers") { bearer(consumer) }
        assertEquals(HttpStatusCode.Forbidden, t1.status)
        val t2 = client.get("/admin/summary") { bearer(consumer) }
        assertEquals(HttpStatusCode.Forbidden, t2.status)
    }

    @Test
    fun `technician cannot call admin routes`() = testApplication {
        setupApp()
        val technician = client.loginToken("budi", "budi12345")
        val res = client.get("/admin/summary") { bearer(technician) }
        assertEquals(HttpStatusCode.Forbidden, res.status)
    }

    @Test
    fun `request without token gets 401`() = testApplication {
        setupApp()
        val res = client.get("/consumer/services")
        assertEquals(HttpStatusCode.Unauthorized, res.status)
    }

    @Test
    fun `account locks after five failed attempts`() = testApplication {
        setupApp()
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    RegisterRequest.serializer(),
                    RegisterRequest("Lock Tester", "locktester", "locktester123", "081999888777")
                )
            )
        }
        repeat(5) {
            val res = client.login("locktester", "wrong-password")
            assertEquals(HttpStatusCode.Unauthorized, res.status)
        }
        // even the correct password is rejected while locked
        val locked = client.login("locktester", "locktester123")
        assertEquals(HttpStatusCode.Forbidden, locked.status)
    }

    @Test
    fun `login rate limiter kicks in after many failures`() = testApplication {
        setupApp()
        repeat(10) {
            client.login("nosuchuser", "wrong-password")
        }
        val res = client.login("nosuchuser", "wrong-password")
        assertEquals(HttpStatusCode.TooManyRequests, res.status)
    }

    @Test
    fun `order lifecycle end to end with transfer payment`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")
        val budi = client.loginToken("budi", "budi12345")

        // technician goes online near the address
        client.put("/technician/online") {
            bearer(budi)
            contentType(ContentType.Application.Json)
            setBody("""{"online":true}""")
        }.let { assertEquals(HttpStatusCode.OK, it.status) }
        client.post("/technician/location") {
            bearer(budi)
            contentType(ContentType.Application.Json)
            setBody("""{"lat":-6.2297,"lng":106.8294}""")
        }.let { assertEquals(HttpStatusCode.OK, it.status) }

        // find service and slot
        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val service = services.first { it.name == "Perbaikan Listrik" }

        val tomorrow = java.time.LocalDate.now().plusDays(1).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }

        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())

        val orderRes = client.post("/consumer/orders") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateOrderRequest.serializer(),
                    CreateOrderRequest(
                        serviceId = service.id,
                        description = "Stopkontak ruang tamu tidak menyala",
                        addressId = addresses.first().id,
                        scheduledDate = tomorrow,
                        slotId = slot.id
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, orderRes.status, orderRes.bodyAsText())
        val order = json.decodeFromString(OrderDto.serializer(), orderRes.bodyAsText())
        assertEquals(OrderStatus.MENUNGGU_PEMBAYARAN, order.status)
        assertEquals(150_000L, order.total) // 125.000 + 25.000
        assertTrue(Regex("TK-\\d{6}-\\d{2}").matches(order.code))

        // pay with transfer -> payment pending with reference number
        val payRes = client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody("""{"method":"TRANSFER"}""")
        }
        assertEquals(HttpStatusCode.OK, payRes.status, payRes.bodyAsText())
        val pay = json.decodeFromString(PayResponse.serializer(), payRes.bodyAsText())
        assertTrue(pay.referenceNo!!.startsWith("BCA-"))
        assertEquals(PaymentStatus.MENUNGGU, pay.status)

        // admin simulates payment success -> order enters search, dispatcher offers it to budi
        val sim = client.post("/admin/payments/${order.id}/simulate-success") { bearer(admin) }
        assertEquals(HttpStatusCode.OK, sim.status)

        // budi should have one offer now
        val offersRes = client.okGet("/technician/offers", budi)
        val offers = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(OfferDto.serializer()), offersRes.bodyAsText())
        val offer = offers.firstOrNull { it.orderId == order.id } ?: fail("expected an offer for order ${order.id}, got: $offers")
        assertEquals(125_000L + 25_000L - Math.round(125_000 * 0.15), offer.estimatedPayout)

        // accept
        val acceptRes = client.post("/technician/offers/${offer.id}/accept") { bearer(budi) }
        assertEquals(HttpStatusCode.OK, acceptRes.status, acceptRes.bodyAsText())

        // budi works the order through the status machine
        for (action in listOf("depart", "arrive", "start")) {
            val res = client.post("/technician/orders/${order.id}/$action") { bearer(budi) }
            assertEquals(HttpStatusCode.OK, res.status, "$action failed: ${res.bodyAsText()}")
        }

        // upload a proof photo (fake PNG: magic bytes + padding)
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
        val boundary = "----tukangkilattest"
        val bodyBytes = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"bukti.png\"\r\n")
            append("Content-Type: image/png\r\n\r\n")
        }.encodeToByteArray() + png + "\r\n--$boundary--\r\n".encodeToByteArray()
        val photoRes = client.post("/photos") {
            bearer(budi)
            header(HttpHeaders.ContentType, "multipart/form-data; boundary=$boundary")
            setBody(bodyBytes)
        }
        assertEquals(HttpStatusCode.Created, photoRes.status, photoRes.bodyAsText())
        val photoUrl = json.decodeFromString(PhotoUploadDto.serializer(), photoRes.bodyAsText()).url

        val finishRes = client.post("/technician/orders/${order.id}/finish") {
            bearer(budi)
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Stopkontak sudah diganti dan diuji","photoUrls":["$photoUrl"],"cashReceived":false}""")
        }
        assertEquals(HttpStatusCode.OK, finishRes.status, finishRes.bodyAsText())

        // consumer sees MENUNGGU_KONFIRMASI and confirms
        val detailRes = client.okGet("/consumer/orders/${order.id}", rina)
        val detail = json.decodeFromString(OrderDto.serializer(), detailRes.bodyAsText())
        assertEquals(OrderStatus.MENUNGGU_KONFIRMASI, detail.status)
        assertEquals("Stopkontak sudah diganti dan diuji", detail.finishNote)
        assertTrue(detail.photos.any { it.kind == PhotoKind.BUKTI })

        val confirmRes = client.post("/consumer/orders/${order.id}/confirm") { bearer(rina) }
        assertEquals(HttpStatusCode.OK, confirmRes.status)
        val confirmed = json.decodeFromString(OrderDto.serializer(), confirmRes.bodyAsText())
        assertEquals(OrderStatus.SELESAI, confirmed.status)

        // rating updates technician aggregates
        val rateRes = client.post("/consumer/orders/${order.id}/rating") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody("""{"stars":5,"tags":["Tepat waktu","Rapi"],"comment":"Mantap"}""")
        }
        assertEquals(HttpStatusCode.OK, rateRes.status, rateRes.bodyAsText())

        val techProfile = client.okGet("/technician/profile", budi)
        val profile = json.decodeFromString(TechnicianProfileDto.serializer(), techProfile.bodyAsText())
        assertTrue(profile.jobsCount >= 1)

        // second rating must fail
        val rateAgain = client.post("/consumer/orders/${order.id}/rating") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody("""{"stars":3,"tags":[],"comment":"dobel"}""")
        }
        assertEquals(HttpStatusCode.Conflict, rateAgain.status)
    }

    @Test
    fun `payment failure then retry with tuai method succeeds`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")

        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val service = services.first()

        val tomorrow = java.time.LocalDate.now().plusDays(2).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }

        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())

        suspend fun createOrder(): OrderDto {
            val res = client.post("/consumer/orders") {
                bearer(rina)
                contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateOrderRequest.serializer(),
                        CreateOrderRequest(service.id, "Pipa bocor di dapur", addresses.first().id, tomorrow, slot.id)
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
            return json.decodeFromString(OrderDto.serializer(), res.bodyAsText())
        }

        val order = createOrder()
        client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody("""{"method":"TRANSFER"}""")
        }
        val fail = client.post("/admin/payments/${order.id}/simulate-fail") { bearer(admin) }
        assertEquals(HttpStatusCode.OK, fail.status)

        val detail = client.okGet("/consumer/orders/${order.id}", rina)
        assertEquals(OrderStatus.PEMBAYARAN_GAGAL, json.decodeFromString(OrderDto.serializer(), detail.bodyAsText()).status)

        // retry with cash: goes straight to searching
        val retry = client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody("""{"method":"TUNAI"}""")
        }
        assertEquals(HttpStatusCode.OK, retry.status, retry.bodyAsText())
        val afterRetry = client.okGet("/consumer/orders/${order.id}", rina)
        val after = json.decodeFromString(OrderDto.serializer(), afterRetry.bodyAsText())
        assertEquals(OrderStatus.MENCARI_TEKNISI, after.status)
        assertEquals(PaymentMethod.TUNAI, after.paymentMethod)
    }

    @Test
    fun `cancel is rejected after technician departs`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")
        val budi = client.loginToken("budi", "budi12345")

        client.put("/technician/online") {
            bearer(budi); contentType(ContentType.Application.Json); setBody("""{"online":true}""")
        }
        client.post("/technician/location") {
            bearer(budi); contentType(ContentType.Application.Json); setBody("""{"lat":-6.2297,"lng":106.8294}""")
        }

        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val service = services.first()
        val tomorrow = java.time.LocalDate.now().plusDays(3).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }
        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())

        val orderRes = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateOrderRequest.serializer(),
                    CreateOrderRequest(service.id, "Kran bocor", addresses.first().id, tomorrow, slot.id)
                )
            )
        }
        val order = json.decodeFromString(OrderDto.serializer(), orderRes.bodyAsText())
        client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"method":"TUNAI"}""")
        }
        client.post("/admin/payments/${order.id}/simulate-success") { bearer(admin) }
        // no payment pending for TUNAI, so simulate-success should fail with conflict
        // (it requires a MENUNGGU payment); that is expected and ok

        // dispatch happens when a TUNAI order enters search; the offer must exist
        val offersRes = client.okGet("/technician/offers", budi)
        val offers = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(OfferDto.serializer()), offersRes.bodyAsText())
        val offer = offers.firstOrNull { it.orderId == order.id }
            ?: fail("expected an offer for TUNAI order ${order.id}, got: $offers")
        client.post("/technician/offers/${offer.id}/accept") { bearer(budi) }
        client.post("/technician/orders/${order.id}/depart") { bearer(budi) }
        val cancel = client.post("/consumer/orders/${order.id}/cancel") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"reason":"berubah pikiran"}""")
        }
        assertEquals(HttpStatusCode.Conflict, cancel.status)
    }

    @Test
    fun `consumer cannot read another consumer order`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    RegisterRequest.serializer(),
                    RegisterRequest("B Consumer", "bconsumer", "bconsumer123", "081111222333")
                )
            )
        }
        val bconsumer = client.loginToken("bconsumer", "bconsumer123")

        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val tomorrow = java.time.LocalDate.now().plusDays(4).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }
        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())
        val orderRes = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateOrderRequest.serializer(),
                    CreateOrderRequest(services.first().id, "AC mati total", addresses.first().id, tomorrow, slot.id)
                )
            )
        }
        val order = json.decodeFromString(OrderDto.serializer(), orderRes.bodyAsText())

        val detail = client.get("/consumer/orders/${order.id}") { bearer(bconsumer) }
        assertEquals(HttpStatusCode.Forbidden, detail.status)
        val cancel = client.post("/consumer/orders/${order.id}/cancel") {
            bearer(bconsumer); contentType(ContentType.Application.Json); setBody("""{"reason":"iseng"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, cancel.status)
        val list = client.get("/consumer/orders") { bearer(bconsumer) }
        assertTrue(!list.bodyAsText().contains("TK-"))
    }

    @Test
    fun `admin can create and disable promo codes`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")

        val create = client.put("/admin/promo-codes") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("""{"code":"HEMAT55","type":"NOMINAL","value":55000,"validUntil":"2030-01-01T00:00:00Z","quota":10,"isActive":true}""")
        }
        assertEquals(HttpStatusCode.OK, create.status, create.bodyAsText())
        val list = client.get("/admin/promo-codes") { bearer(admin) }
        assertTrue(list.bodyAsText().contains("HEMAT55"))

        // disable it, then applying it at payment time must fail
        val disable = client.put("/admin/promo-codes") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("""{"code":"HEMAT55","type":"NOMINAL","value":55000,"validUntil":"2030-01-01T00:00:00Z","quota":10,"isActive":false}""")
        }
        assertEquals(HttpStatusCode.OK, disable.status)

        val rina = client.loginToken("rina", "rina12345")
        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val service = services.first()
        val tomorrow = java.time.LocalDate.now().plusDays(6).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }
        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())
        val orderRes = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateOrderRequest.serializer(),
                    CreateOrderRequest(service.id, "AC bocor", addresses.first().id, tomorrow, slot.id)
                )
            )
        }
        val order = json.decodeFromString(OrderDto.serializer(), orderRes.bodyAsText())
        val pay = client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody("""{"method":"TUNAI","promoCode":"HEMAT55"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, pay.status)
    }

    @Test
    fun `non admin cannot manage promo codes`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        assertEquals(HttpStatusCode.Forbidden, client.get("/admin/promo-codes") { bearer(rina) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/admin/promo-codes") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody("""{"code":"X2026","type":"NOMINAL","value":10000,"validUntil":"2030-01-01T00:00:00Z","quota":1,"isActive":true}""")
        }.status)
    }

    @Test
    fun `technician sees received reviews after rating`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val budi = client.loginToken("budi", "budi12345")
        val admin = client.loginToken("admin", "admin12345")
        client.put("/technician/online") {
            bearer(budi); contentType(ContentType.Application.Json); setBody("""{"online":true}""")
        }
        client.post("/technician/location") {
            bearer(budi); contentType(ContentType.Application.Json); setBody("""{"lat":-6.2297,"lng":106.8294}""")
        }

        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val service = services.first()
        val tomorrow = java.time.LocalDate.now().plusDays(7).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }
        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())
        val orderRes = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateOrderRequest.serializer(),
                    CreateOrderRequest(service.id, "Perbaikan total", addresses.first().id, tomorrow, slot.id)
                )
            )
        }
        val order = json.decodeFromString(OrderDto.serializer(), orderRes.bodyAsText())
        client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"method":"TUNAI"}""")
        }
        val offersRes = client.okGet("/technician/offers", budi)
        val offers = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(OfferDto.serializer()), offersRes.bodyAsText())
        val offer = offers.firstOrNull { it.orderId == order.id } ?: fail("expected an offer for order ${order.id}")
        client.post("/technician/offers/${offer.id}/accept") { bearer(budi) }
        for (action in listOf("depart", "arrive", "start")) {
            val res = client.post("/technician/orders/${order.id}/$action") { bearer(budi) }
            assertEquals(HttpStatusCode.OK, res.status, "$action failed: ${res.bodyAsText()}")
        }
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(64)
        val boundary = "----tukangkilattest"
        val bodyBytes = buildString {
            append("--$boundary\r\n")
            append("Content-Disposition: form-data; name=\"file\"; filename=\"bukti.png\"\r\n")
            append("Content-Type: image/png\r\n\r\n")
        }.encodeToByteArray() + png + "\r\n--$boundary--\r\n".encodeToByteArray()
        val photoRes = client.post("/photos") {
            bearer(budi)
            header(HttpHeaders.ContentType, "multipart/form-data; boundary=$boundary")
            setBody(bodyBytes)
        }
        assertEquals(HttpStatusCode.Created, photoRes.status, photoRes.bodyAsText())
        val photoUrl = json.decodeFromString(PhotoUploadDto.serializer(), photoRes.bodyAsText()).url
        val finishRes = client.post("/technician/orders/${order.id}/finish") {
            bearer(budi)
            contentType(ContentType.Application.Json)
            setBody("""{"note":"Selesai diuji","photoUrls":["$photoUrl"],"cashReceived":true}""")
        }
        assertEquals(HttpStatusCode.OK, finishRes.status, finishRes.bodyAsText())
        client.post("/consumer/orders/${order.id}/confirm") { bearer(rina) }
        client.post("/consumer/orders/${order.id}/rating") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody("""{"stars":5,"tags":["Rapi"],"comment":"Mantap sekali"}""")
        }
        val reviewsRes = client.okGet("/technician/reviews", budi)
        assertTrue(reviewsRes.bodyAsText().contains("Mantap sekali"))
        assertTrue(reviewsRes.bodyAsText().contains(order.code))
    }

    @Test
    fun `two orders created same day get sequential codes`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val servicesRes = client.okGet("/consumer/services", rina)
        val services = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()), servicesRes.bodyAsText())
        val tomorrow = java.time.LocalDate.now().plusDays(5).toString()
        val slotsRes = client.okGet("/consumer/slots?date=$tomorrow", rina)
        val slots = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()), slotsRes.bodyAsText())
        val slot = slots.first { it.available }
        val addressesRes = client.okGet("/consumer/addresses", rina)
        val addresses = json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()), addressesRes.bodyAsText())

        suspend fun create(): String {
            val res = client.post("/consumer/orders") {
                bearer(rina); contentType(ContentType.Application.Json)
                setBody(
                    json.encodeToString(
                        CreateOrderRequest.serializer(),
                        CreateOrderRequest(services.first().id, "Listrik kedip", addresses.first().id, tomorrow, slot.id)
                    )
                )
            }
            assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
            return json.decodeFromString(OrderDto.serializer(), res.bodyAsText()).code
        }

        val code1 = create()
        val code2 = create()
        assertNotEquals(code1, code2)
        assertTrue(code1.startsWith("TK-"))
        assertTrue(code2.startsWith("TK-"))
    }

    @Test
    fun `maintenance expires stale payment and completes stale confirmation`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val date = java.time.LocalDate.now().plusDays(7).toString()
        val slots = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()),
            client.okGet("/consumer/slots?date=$date", rina).bodyAsText()
        )
        val addresses = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()),
            client.okGet("/consumer/addresses", rina).bodyAsText()
        )
        val services = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(ServiceDto.serializer()),
            client.okGet("/consumer/services", rina).bodyAsText()
        )
        val created = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateOrderRequest.serializer(),
                CreateOrderRequest(services.first().id, "Uji perawatan otomatis", addresses.first().id, date, slots.first { it.available }.id)
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val order = json.decodeFromString(OrderDto.serializer(), created.bodyAsText())

        val paid = client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody("""{"method":"TRANSFER"}""")
        }
        assertEquals(HttpStatusCode.OK, paid.status, paid.bodyAsText())

        transaction {
            PaymentsTable.update({ PaymentsTable.orderId eq order.id }) {
                it[expiresAt] = OffsetDateTime.now().minusMinutes(1)
            }
        }
        assertTrue(OrderService.expireStalePayments() >= 1)
        val expired = json.decodeFromString(OrderDto.serializer(), client.okGet("/consumer/orders/${order.id}", rina).bodyAsText())
        assertEquals(OrderStatus.PEMBAYARAN_GAGAL, expired.status)

        transaction {
            OrdersTable.update({ OrdersTable.id eq order.id }) {
                it[status] = OrderStatus.MENUNGGU_KONFIRMASI.name
                it[updatedAt] = OffsetDateTime.now().minusHours(48)
            }
        }
        assertTrue(OrderService.autoCompleteStaleConfirmation(24) >= 1)
        val completed = json.decodeFromString(OrderDto.serializer(), client.okGet("/consumer/orders/${order.id}", rina).bodyAsText())
        assertEquals(OrderStatus.SELESAI, completed.status)
    }

    @Test
    fun `payment failure can be retried with another method`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")
        val date = java.time.LocalDate.now().plusDays(15).toString()
        val slots = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()),
            client.okGet("/consumer/slots?date=$date", rina).bodyAsText()
        )
        val addresses = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()),
            client.okGet("/consumer/addresses", rina).bodyAsText()
        )
        val created = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateOrderRequest.serializer(),
                CreateOrderRequest(1, "Uji gagal bayar", addresses.first().id, date, slots.first { it.available }.id)
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val order = json.decodeFromString(OrderDto.serializer(), created.bodyAsText())

        val paid = client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"method":"TRANSFER"}""")
        }
        assertEquals(HttpStatusCode.OK, paid.status, paid.bodyAsText())
        val failed = client.post("/admin/payments/${order.id}/simulate-fail") { bearer(admin) }
        assertEquals(HttpStatusCode.OK, failed.status, failed.bodyAsText())
        val afterFail = json.decodeFromString(OrderDto.serializer(), client.okGet("/consumer/orders/${order.id}", rina).bodyAsText())
        assertEquals(OrderStatus.PEMBAYARAN_GAGAL, afterFail.status)

        val retried = client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"method":"DOMPET_DIGITAL"}""")
        }
        assertEquals(HttpStatusCode.OK, retried.status, retried.bodyAsText())
        val afterRetry = json.decodeFromString(OrderDto.serializer(), client.okGet("/consumer/orders/${order.id}", rina).bodyAsText())
        assertEquals(OrderStatus.MENUNGGU_PEMBAYARAN, afterRetry.status)
        assertEquals(PaymentMethod.DOMPET_DIGITAL, afterRetry.paymentMethod)
    }

    @Test
    fun `address CRUD and reschedule while searching work`() = testApplication {
        setupApp()
        val rina = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")
        val createdAddress = client.post("/consumer/addresses") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody("""{"label":"Kantor","fullAddress":"Jl. Uji No. 1","lat":-6.23,"lng":106.83,"isPrimary":false}""")
        }
        assertEquals(HttpStatusCode.Created, createdAddress.status, createdAddress.bodyAsText())
        val address = json.decodeFromString(AddressDto.serializer(), createdAddress.bodyAsText())
        val updated = client.put("/consumer/addresses/${address.id}") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody("""{"label":"Kantor baru","fullAddress":"Jl. Uji No. 2","lat":-6.23,"lng":106.83,"isPrimary":false}""")
        }
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())
        val deleted = client.delete("/consumer/addresses/${address.id}") { bearer(rina) }
        assertEquals(HttpStatusCode.NoContent, deleted.status, deleted.bodyAsText())

        val date = java.time.LocalDate.now().plusDays(16).toString()
        val slots = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()),
            client.okGet("/consumer/slots?date=$date", rina).bodyAsText()
        )
        val addresses = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()),
            client.okGet("/consumer/addresses", rina).bodyAsText()
        )
        val created = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateOrderRequest.serializer(),
                CreateOrderRequest(1, "Uji jadwal ulang", addresses.first().id, date, slots.first { it.available }.id)
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val order = json.decodeFromString(OrderDto.serializer(), created.bodyAsText())
        client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"method":"TRANSFER"}""")
        }
        client.post("/admin/payments/${order.id}/simulate-success") { bearer(admin) }
        val searching = json.decodeFromString(OrderDto.serializer(), client.okGet("/consumer/orders/${order.id}", rina).bodyAsText())
        assertEquals(OrderStatus.MENCARI_TEKNISI, searching.status)

        val newDate = java.time.LocalDate.now().plusDays(18).toString()
        val newSlots = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()),
            client.okGet("/consumer/slots?date=$newDate", rina).bodyAsText()
        )
        val rescheduled = client.post("/consumer/orders/${order.id}/reschedule") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RescheduleRequest.serializer(), RescheduleRequest(newDate, newSlots.first { it.available }.id)))
        }
        assertEquals(HttpStatusCode.OK, rescheduled.status, rescheduled.bodyAsText())
        val final = json.decodeFromString(OrderDto.serializer(), client.okGet("/consumer/orders/${order.id}", rina).bodyAsText())
        assertEquals(newDate, final.scheduledDate)
    }
}
