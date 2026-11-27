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
            setBody("""{"stars":5,"tags":["Rapi"],"comment":"Mantap sekali","photos":["$photoUrl"]}""")
        }
        val reviewsRes = client.okGet("/technician/reviews", budi)
        assertTrue(reviewsRes.bodyAsText().contains("Mantap sekali"))
        assertTrue(reviewsRes.bodyAsText().contains(order.code))
        assertTrue(reviewsRes.bodyAsText().contains(photoUrl))
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


    @Test
    fun `technician can withdraw simulated balance and see history`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")
        val budi = client.loginToken("budi", "budi12345")
        val rina = client.loginToken("rina", "rina12345")
        val tomorrow = java.time.LocalDate.now().plusDays(20).toString()
        val slots = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(SlotDto.serializer()),
            client.okGet("/consumer/slots?date=$tomorrow", rina).bodyAsText()
        )
        val addresses = json.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(AddressDto.serializer()),
            client.okGet("/consumer/addresses", rina).bodyAsText()
        )
        val created = client.post("/consumer/orders") {
            bearer(rina); contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateOrderRequest.serializer(),
                CreateOrderRequest(1, "Tarik saldo", addresses.first().id, tomorrow, slots.first { it.available }.id)
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status, created.bodyAsText())
        val order = json.decodeFromString(OrderDto.serializer(), created.bodyAsText())
        client.post("/consumer/orders/${order.id}/pay") {
            bearer(rina); contentType(ContentType.Application.Json); setBody("""{"method":"TRANSFER"}""")
        }
        client.post("/admin/payments/${order.id}/simulate-success") { bearer(admin) }
        val offer = json.decodeFromString<List<OfferDto>>(
            client.okGet("/technician/offers", budi).bodyAsText()
        ).first { it.orderId == order.id }
        client.post("/technician/offers/${offer.id}/accept") { bearer(budi) }
        client.post("/technician/orders/${order.id}/depart") { bearer(budi) }
        client.post("/technician/orders/${order.id}/arrive") { bearer(budi) }
        client.post("/technician/orders/${order.id}/start") { bearer(budi) }
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
        val finished = client.post("/technician/orders/${order.id}/finish") {
            bearer(budi); contentType(ContentType.Application.Json)
            setBody("""{"note":"Selesai","photoUrls":["$photoUrl"]}""")
        }
        assertEquals(HttpStatusCode.OK, finished.status, finished.bodyAsText())
        val confirmed = client.post("/consumer/orders/${order.id}/confirm") { bearer(rina) }
        assertEquals(HttpStatusCode.OK, confirmed.status, confirmed.bodyAsText())

        val before = json.decodeFromString<EarningsDto>(client.okGet("/technician/earnings", budi).bodyAsText())
        assertTrue(before.balance > 0)
        val withdraw = client.post("/technician/withdrawals") {
            bearer(budi); contentType(ContentType.Application.Json)
            setBody("""{"amount":${before.balance}}""")
        }
        assertEquals(HttpStatusCode.OK, withdraw.status, withdraw.bodyAsText())
        val after = json.decodeFromString<EarningsDto>(client.okGet("/technician/earnings", budi).bodyAsText())
        assertEquals(0, after.balance)
        val history = json.decodeFromString<List<WithdrawalDto>>(client.okGet("/technician/withdrawals", budi).bodyAsText())
        assertEquals(1, history.size)
        assertEquals(before.balance, history.first().amount)
    }


    @Test
    fun `admin can create a service visible to consumers`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")
        val rina = client.loginToken("rina", "rina12345")
        val res = client.post("/admin/services") {
            bearer(admin); contentType(ContentType.Application.Json)
            setBody("""{"name":"Servis Kursi","icon":"bolt","skill":"LISTRIK","basePrice":75000,"visitFee":20000}""")
        }
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        val created = json.decodeFromString(ServiceDto.serializer(), res.bodyAsText())
        val services = json.decodeFromString<List<ServiceDto>>(client.okGet("/consumer/services", rina).bodyAsText())
        assertTrue(services.any { it.id == created.id && it.name == "Servis Kursi" })
    }

    @Test
    fun `consumer can browse verified technicians with filters`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")

        val allText = client.okGet("/consumer/technicians", consumer).bodyAsText()
        val all = json.decodeFromString<List<TechnicianBrowseDto>>(allText)
        assertTrue(all.isNotEmpty(), "seed technicians must be visible")
        assertTrue(all.any { it.fullName.contains("budi", ignoreCase = true) })

        val bySkillText = client.okGet("/consumer/technicians?skill=LISTRIK", consumer).bodyAsText()
        val bySkill = json.decodeFromString<List<TechnicianBrowseDto>>(bySkillText)
        assertTrue(bySkill.isNotEmpty())
        assertTrue(bySkill.all { "LISTRIK" in it.skills })

        val byQueryText = client.okGet("/consumer/technicians?q=budi", consumer).bodyAsText()
        val byQuery = json.decodeFromString<List<TechnicianBrowseDto>>(byQueryText)
        assertTrue(byQuery.any { it.fullName.contains("budi", ignoreCase = true) })

        val technician = client.loginToken("budi", "budi12345")
        val denied = client.get("/consumer/technicians") { bearer(technician) }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
    }


    @Test
    fun `admin analytics aggregates all orders`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")
        val res = client.okGet("/admin/analytics", admin)
        val analytics = json.decodeFromString(AdminAnalyticsDto.serializer(), res.bodyAsText())
        assertEquals(7, analytics.dates.size)
        assertEquals(7, analytics.dailyCounts.size)
        assertTrue(analytics.totalOrders > 0, "seed orders must be counted")
        assertTrue(analytics.statusCounts.values.sum() == analytics.totalOrders)
    }


    @Test
    fun `technician proposal can be accepted into an order`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val technician = client.loginToken("budi", "budi12345")
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val created = client.post("/technician/proposals") {
            bearer(technician)
            contentType(ContentType.Application.Json)
            setBody(
                json.encodeToString(
                    CreateProposalRequest.serializer(),
                    CreateProposalRequest(
                        consumerId = 2L,
                        serviceId = serviceId,
                        description = "Perbaikan listrik sesuai diskusi",
                        price = 150000L,
                        scheduledDate = java.time.LocalDate.now().plusDays(1).toString(),
                        customTime = "14:35"
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val proposal = json.decodeFromString(ProposalDto.serializer(), created.bodyAsText())
        val accepted = client.post("/consumer/proposals/${proposal.id}/accept") { bearer(consumer) }
        assertEquals(HttpStatusCode.OK, accepted.status)
        val orderId = json.decodeFromString<Map<String, Long>>(accepted.bodyAsText())["orderId"]!!
        val detail = client.okGet("/consumer/orders/$orderId", consumer).bodyAsText()
        assertTrue(detail.contains("150000"), "negotiated price must be stored on the order")
    }


    @Test
    fun `wallet topup loyalty and subscription work`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val before = client.okGet("/consumer/wallet", consumer).bodyAsText()
            .let { json.decodeFromString<WalletDto>(it).balance }
        val after = client.post("/consumer/wallet/topup") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(TopUpRequest.serializer(), TopUpRequest(50_000L)))
        }.bodyAsText().let { json.decodeFromString<WalletDto>(it).balance }
        assertTrue(after == before + 50_000L)
        val points = client.okGet("/consumer/loyalty", consumer).bodyAsText()
            .let { json.decodeFromString<LoyaltyDto>(it) }
        assertTrue(points.points >= 0)
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val created = client.post("/consumer/subscriptions") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateSubscriptionRequest.serializer(),
                CreateSubscriptionRequest(serviceId, addressId, 30, java.time.LocalDate.now().plusDays(1).toString())
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status)
    }

    @Test
    fun `technician workbench endpoints work`() = testApplication {
        setupApp()
        val technician = client.loginToken("budi", "budi12345")
        val block = client.post("/technician/availability") {
            bearer(technician)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateAvailabilityBlockRequest.serializer(),
                CreateAvailabilityBlockRequest(java.time.LocalDate.now().plusDays(2).toString(), "09.00")
            ))
        }
        assertEquals(HttpStatusCode.Created, block.status)
        val stats = client.okGet("/technician/stats", technician).bodyAsText()
            .let { json.decodeFromString<TechnicianStatsDto>(it) }
        assertTrue(stats.totalOffers >= 0)
    }

    @Test
    fun `admin announcements and config work`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")
        val created = client.post("/admin/announcements") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateAnnouncementRequest.serializer(),
                CreateAnnouncementRequest("Pengumuman uji", "Isi pengumuman")
            ))
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val public = client.get("/announcements")
        assertEquals(HttpStatusCode.OK, public.status)
        assertTrue(public.bodyAsText().contains("Pengumuman uji"))
        val config = client.put("/admin/config/dispatch_radius") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"value\":\"12\"}")
        }
        assertEquals(HttpStatusCode.OK, config.status)
    }


    @Test
    fun `auto online schedule and unread chat work`() = testApplication {
        setupApp()
        val techLogin = client.login("budi", "budi12345")
        val techId = json.decodeFromString<LoginResponse>(techLogin.bodyAsText()).user.id
        val tech = json.decodeFromString<LoginResponse>(techLogin.bodyAsText()).accessToken
        val consumerLogin = client.login("rina", "rina12345")
        val consumerId = json.decodeFromString<LoginResponse>(consumerLogin.bodyAsText()).user.id
        val consumer = json.decodeFromString<LoginResponse>(consumerLogin.bodyAsText()).accessToken

        val sent = client.post("/technician/direct-messages/$consumerId") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SendDirectMessageRequest.serializer(), SendDirectMessageRequest("Halo dari teknisi")))
        }
        assertEquals(HttpStatusCode.OK, sent.status)
        val before = client.okGet("/consumer/direct-peers", consumer).bodyAsText()
            .let { json.decodeFromString<List<DirectPeerDto>>(it) }
        assertTrue(before.any { it.peerId == techId && it.unreadCount >= 1 })

        client.post("/consumer/direct-peers/$techId/read") { bearer(consumer) }
        val after = client.okGet("/consumer/direct-peers", consumer).bodyAsText()
            .let { json.decodeFromString<List<DirectPeerDto>>(it) }
        assertEquals(0, after.first { it.peerId == techId }.unreadCount)

        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Jakarta")).dayOfWeek.value
        client.post("/technician/online") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody("{\"online\":false}")
        }
        val schedule = client.post("/technician/auto-schedule") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateAutoScheduleRequest.serializer(),
                CreateAutoScheduleRequest(today, "00:00", "23:59")
            ))
        }
        assertEquals(HttpStatusCode.Created, schedule.status)
        val schedules = client.okGet("/technician/auto-schedule", tech).bodyAsText()
            .let { json.decodeFromString<List<AutoOnlineScheduleDto>>(it) }
        assertTrue(schedules.any { it.dayOfWeek == today && it.enabled })

        assertEquals(1, EcosystemService.applyAutoSchedules())
        val profile = client.okGet("/technician/profile", tech).bodyAsText()
            .let { json.decodeFromString<TechnicianProfileDto>(it) }
        assertTrue(profile.isOnline)
    }

    @Test
    fun `message moderation and csv export work`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")
        val tech = client.loginToken("budi", "budi12345")
        val consumerLogin = client.login("rina", "rina12345")
        val consumerId = json.decodeFromString<LoginResponse>(consumerLogin.bodyAsText()).user.id

        val consumer = json.decodeFromString<LoginResponse>(consumerLogin.bodyAsText()).accessToken
        val message = client.post("/technician/direct-messages/$consumerId") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SendDirectMessageRequest.serializer(), SendDirectMessageRequest("Pesan untuk dimoderasi")))
        }.bodyAsText().let { json.decodeFromString<DirectMessageDto>(it) }

        val otherTechnician = client.loginToken("sari", "sari12345")
        val outsider = client.post("/technician/messages/${message.id}/report") {
            bearer(otherTechnician)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReportMessageRequest.serializer(), ReportMessageRequest(message.id, "Spam")))
        }
        assertEquals(HttpStatusCode.Forbidden, outsider.status)

        val reported = client.post("/consumer/messages/${message.id}/report") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReportMessageRequest.serializer(), ReportMessageRequest(message.id, "Spam")))
        }
        assertEquals(HttpStatusCode.OK, reported.status)
        val reports = client.okGet("/admin/message-reports", admin).bodyAsText()
            .let { json.decodeFromString<List<MessageReportDto>>(it) }
        assertTrue(reports.any { it.messageId == message.id })

        val reportId = reports.first { it.messageId == message.id }.id
        val resolved = client.post("/admin/message-reports/$reportId/resolve") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"hideMessage\":true}")
        }
        assertEquals(HttpStatusCode.OK, resolved.status)
        val hidden = client.okGet("/technician/direct-messages/$consumerId", tech).bodyAsText()
        assertTrue(hidden.contains("pesan disembunyikan oleh admin"))

        val ordersCsv = client.okGet("/admin/export/orders", admin).bodyAsText()
        assertTrue(ordersCsv.contains("kode"))
        val walletCsv = client.okGet("/admin/export/wallet", admin).bodyAsText()
        assertTrue(walletCsv.contains("pengguna_id"))
    }


    private suspend fun HttpClient.createTestOrder(token: String, serviceId: Long, addressId: Long, slotId: Long): Long {
        val tomorrow = java.time.LocalDate.now().plusDays(3).toString()
        val res = post("/consumer/orders") {
            bearer(token)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateOrderRequest.serializer(), CreateOrderRequest(
                serviceId = serviceId,
                description = "Pesanan uji v4",
                addressId = addressId,
                scheduledDate = tomorrow,
                slotId = slotId
            )))
        }
        assertEquals(HttpStatusCode.Created, res.status, res.bodyAsText())
        return json.decodeFromString<OrderDto>(res.bodyAsText()).id
    }

    @Test
    fun `catalog search estimate tiers and vouchers work`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")

        val services = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it) }
        assertTrue(services.isNotEmpty())
        val search = client.okGet("/consumer/search?q=${services.first().name}&sort=price", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceSearchDto>>(it) }
        assertTrue(search.isNotEmpty())

        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val estimate = client.post("/consumer/estimate") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(EstimateRequest.serializer(), EstimateRequest(
                services.first().id, addressId, java.time.LocalDate.now().plusDays(3).toString(), slotId
            )))
        }
        assertEquals(HttpStatusCode.OK, estimate.status, estimate.bodyAsText())
        val priced = json.decodeFromString<EstimateDto>(estimate.bodyAsText())
        assertTrue(priced.total > 0)

        val tiers = client.okGet("/consumer/tiers", consumer).bodyAsText()
            .let { json.decodeFromString<List<TierDto>>(it) }
        assertEquals(3, tiers.size)
        assertTrue(tiers.any { it.current && it.code == "SILVER" })

        val consumerId = transaction {
            UsersTable.selectAll().where { UsersTable.username eq "rina" }.single()[UsersTable.id]
        }
        val voucher = client.post("/admin/vouchers/issue") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"consumerId\":$consumerId,\"amount\":20000}")
        }
        assertEquals(HttpStatusCode.Created, voucher.status)
        val vouchers = client.okGet("/consumer/vouchers", consumer).bodyAsText()
            .let { json.decodeFromString<List<VoucherDto>>(it) }
        assertTrue(vouchers.any { it.amount == 20_000L })

        val referral = client.okGet("/consumer/referrals", consumer).bodyAsText()
            .let { json.decodeFromString<List<ReferralDto>>(it).first() }
        val registered = client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest.serializer(), RegisterRequest(
                "Teman Rina", "rina2", "rina212345", "081234567891"
            )))
        }
        assertEquals(HttpStatusCode.Created, registered.status, registered.bodyAsText())
        val friend = client.loginToken("rina2", "rina212345")
        val redeemed = client.post("/consumer/referrals/redeem") {
            bearer(friend)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RedeemReferralRequest.serializer(), RedeemReferralRequest(referral.code)))
        }
        assertTrue(redeemed.status == HttpStatusCode.OK || redeemed.status == HttpStatusCode.Conflict)
    }

    @Test
    fun `wallet payment subscription controls and installments work`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val orderId = client.createTestOrder(consumer, serviceId, addressId, slotId)

        client.post("/consumer/wallet/topup") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody("{\"amount\":2000000}")
        }
        val paid = client.post("/consumer/wallet/pay/$orderId") { bearer(consumer) }
        assertEquals(HttpStatusCode.OK, paid.status, paid.bodyAsText())
        val result = json.decodeFromString<WalletPaymentResultDto>(paid.bodyAsText())
        assertTrue(result.balance < 2_000_000)

        val settings = client.put("/consumer/wallet/settings") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody("{\"autoTopupEnabled\":true,\"autoTopupAmount\":100000,\"autoTopupThreshold\":50000,\"dailyLimit\":500000,\"cashbackPercent\":5}")
        }
        assertEquals(HttpStatusCode.OK, settings.status)
        val saved = client.okGet("/consumer/wallet/settings", consumer).bodyAsText()
            .let { json.decodeFromString<WalletSettingsDto>(it) }
        assertTrue(saved.autoTopupEnabled)

        transaction {
            OrdersTable.update({ OrdersTable.id eq orderId }) { it[total] = 1_200_000L }
        }
        val installment = client.post("/consumer/installments") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateInstallmentRequest.serializer(), CreateInstallmentRequest(orderId, 3)))
        }
        assertEquals(HttpStatusCode.Created, installment.status, installment.bodyAsText())
        val plan = json.decodeFromString<InstallmentDto>(installment.bodyAsText())
        assertEquals(400_000L, plan.monthly)

        val subscription = client.post("/consumer/subscriptions") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateSubscriptionRequest.serializer(),
                CreateSubscriptionRequest(serviceId, addressId, 30, java.time.LocalDate.now().plusDays(1).toString())
            ))
        }
        val subId = json.decodeFromString<SubscriptionDto>(subscription.bodyAsText()).id
        client.post("/consumer/subscriptions/$subId/pause") { bearer(consumer) }
        val paused = client.okGet("/consumer/subscriptions", consumer).bodyAsText()
            .let { json.decodeFromString<List<SubscriptionDto>>(it).first { it.id == subId } }
        assertEquals("DIJEDA", paused.status)
        client.post("/consumer/subscriptions/$subId/resume") { bearer(consumer) }
        client.post("/consumer/subscriptions/$subId/skip") { bearer(consumer) }
    }

    @Test
    fun `signatures checklist reviews and refunds work`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val technician = client.loginToken("budi", "budi12345")
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val orderId = client.createTestOrder(consumer, serviceId, addressId, slotId)
        val techId = transaction {
            val id = UsersTable.selectAll().where { UsersTable.username eq "budi" }.single()[UsersTable.id]
            OrdersTable.update({ OrdersTable.id eq orderId }) { it[OrdersTable.technicianId] = id }
            RatingsTable.insert {
                it[RatingsTable.orderId] = orderId
                it[stars] = 5
                it[tags] = ""
                it[comment] = "Ulasan uji v4"
                it[createdAt] = OffsetDateTime.now()
            }
            id
        }

        val vote = client.post("/consumer/reviews/$orderId/vote") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody("{\"helpful\":true}")
        }
        assertEquals(HttpStatusCode.OK, vote.status)
        val reply = client.post("/technician/reviews/$orderId/reply") {
            bearer(technician)
            contentType(ContentType.Application.Json)
            setBody("{\"reply\":\"Terima kasih\"}")
        }
        assertEquals(HttpStatusCode.OK, reply.status, reply.bodyAsText())
        val details = client.okGet("/technician/review-details", technician).bodyAsText()
            .let { json.decodeFromString<List<ReviewDetailDto>>(it) }
        assertTrue(details.any { it.orderId == orderId && it.helpfulCount == 1 && it.reply == "Terima kasih" })

        val signature = client.post("/orders/$orderId/signature") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody("{\"role\":\"KONSUMEN\",\"dataUrl\":\"data:image/png;base64,AAAA\"}")
        }
        assertEquals(HttpStatusCode.OK, signature.status)
        val signatures = client.okGet("/orders/$orderId/signatures", consumer).bodyAsText()
            .let { json.decodeFromString<List<SignatureDto>>(it) }
        assertTrue(signatures.any { it.role == "KONSUMEN" })

        val steps = client.okGet("/orders/$orderId/checklist", technician).bodyAsText()
            .let { json.decodeFromString<List<ChecklistItemDto>>(it) }
        assertEquals(3, steps.size)
        val updated = client.post("/orders/$orderId/checklist/${steps.first().id}") {
            bearer(technician)
            contentType(ContentType.Application.Json)
            setBody("{\"done\":true,\"photoUrl\":\"/photos/x\",\"note\":\"Selesai\"}")
        }
        assertEquals(HttpStatusCode.OK, updated.status, updated.bodyAsText())

        val refund = client.post("/consumer/refunds?orderId=$orderId") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody("{\"amount\":10000,\"reason\":\"Tidak sesuai\"}")
        }
        assertEquals(HttpStatusCode.Created, refund.status, refund.bodyAsText())
        val refundId = json.decodeFromString<RefundRequestDto>(refund.bodyAsText()).id
        val admin = client.loginToken("admin", "admin12345")
        val resolved = client.post("/admin/refunds/$refundId/resolve") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"approve\":true,\"amount\":10000,\"note\":\"Disetujui\"}")
        }
        assertEquals(HttpStatusCode.OK, resolved.status, resolved.bodyAsText())
    }


    @Test
    fun `v4 pricing applies to order and consumes voucher`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")
        val consumerId = transaction {
            UsersTable.selectAll().where { UsersTable.username eq "rina" }.single()[UsersTable.id]
        }
        val voucher = client.post("/admin/vouchers/issue") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"consumerId\":$consumerId,\"amount\":25000}")
        }.bodyAsText().let { json.decodeFromString<VoucherDto>(it) }

        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val service = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first() }
        val orderId = client.createTestOrder(consumer, serviceId, addressId, slotId)

        val discounted = client.post("/consumer/orders") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(CreateOrderRequest.serializer(), CreateOrderRequest(
                serviceId = serviceId,
                description = "Pesanan voucher v4",
                addressId = addressId,
                scheduledDate = java.time.LocalDate.now().plusDays(4).toString(),
                slotId = slotId,
                voucherCode = voucher.code
            )))
        }
        assertEquals(HttpStatusCode.Created, discounted.status, discounted.bodyAsText())
        val order = json.decodeFromString<OrderDto>(discounted.bodyAsText())
        assertEquals(service.basePrice + service.visitFee - 25_000L, order.total)
        val used = client.okGet("/consumer/vouchers", consumer).bodyAsText()
            .let { json.decodeFromString<List<VoucherDto>>(it).first { it.id == voucher.id } }
        assertEquals("DIPAKAI", used.status)
        assertTrue(orderId > 0)
    }

    @Test
    fun `order extras enforce party and signature role`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val outsider = client.loginToken("sari", "sari12345")
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val orderId = client.createTestOrder(consumer, serviceId, addressId, slotId)

        val forbidden = client.get("/orders/$orderId/checklist") { bearer(outsider) }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        val forbiddenSign = client.post("/orders/$orderId/signature") {
            bearer(outsider)
            contentType(ContentType.Application.Json)
            setBody("{\"role\":\"KONSUMEN\",\"dataUrl\":\"data:image/png;base64,AAAA\"}")
        }
        assertEquals(HttpStatusCode.Forbidden, forbiddenSign.status)

        val techId = transaction {
            val id = UsersTable.selectAll().where { UsersTable.username eq "budi" }.single()[UsersTable.id]
            OrdersTable.update({ OrdersTable.id eq orderId }) { it[technicianId] = id }
            id
        }
        val technician = client.loginToken("budi", "budi12345")
        val signed = client.post("/orders/$orderId/signature") {
            bearer(technician)
            contentType(ContentType.Application.Json)
            setBody("{\"role\":\"KONSUMEN\",\"dataUrl\":\"data:image/png;base64,AAAA\"}")
        }
        assertEquals(HttpStatusCode.OK, signed.status, signed.bodyAsText())
        assertEquals("TUKANG", json.decodeFromString<SignatureDto>(signed.bodyAsText()).role)
        assertTrue(techId > 0)
    }

    @Test
    fun `referral cannot be reused and refund credits wallet`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")
        val rina = client.loginToken("rina", "rina12345")
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest.serializer(), RegisterRequest(
                "Rina Dua", "rina3", "rina312345", "081298888888"
            )))
        }
        val rina3 = client.loginToken("rina3", "rina312345")
        val rinaId = transaction {
            UsersTable.selectAll().where { UsersTable.username eq "rina" }.single()[UsersTable.id]
        }
        client.post("/admin/vouchers/issue") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"consumerId\":$rinaId,\"amount\":10000}")
        }
        val codes = listOf(
            client.okGet("/consumer/referrals", rina).bodyAsText()
                .let { json.decodeFromString<List<ReferralDto>>(it).first().code },
            client.okGet("/consumer/referrals", rina3).bodyAsText()
                .let { json.decodeFromString<List<ReferralDto>>(it).first().code }
        )
        client.post("/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RegisterRequest.serializer(), RegisterRequest(
                "Pemakai Referral", "refuser", "refuser1234", "081299999999"
            )))
        }
        val friend = client.loginToken("refuser", "refuser1234")
        val first = client.post("/consumer/referrals/redeem") {
            bearer(friend)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RedeemReferralRequest.serializer(), RedeemReferralRequest(codes[0])))
        }
        assertEquals(HttpStatusCode.OK, first.status, first.bodyAsText())
        val second = client.post("/consumer/referrals/redeem") {
            bearer(friend)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(RedeemReferralRequest.serializer(), RedeemReferralRequest(codes[1])))
        }
        assertEquals(HttpStatusCode.Conflict, second.status)

        val before = client.okGet("/consumer/wallet", rina).bodyAsText()
            .let { json.decodeFromString<WalletDto>(it).balance }
        val serviceId = client.okGet("/consumer/services", rina).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", rina).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", rina).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val orderId = client.createTestOrder(rina, serviceId, addressId, slotId)
        val refund = client.post("/consumer/refunds?orderId=$orderId") {
            bearer(rina)
            contentType(ContentType.Application.Json)
            setBody("{\"amount\":10000,\"reason\":\"Uji refund\"}")
        }.bodyAsText().let { json.decodeFromString<RefundRequestDto>(it) }
        client.post("/admin/refunds/${refund.id}/resolve") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody("{\"approve\":true,\"amount\":10000,\"note\":\"Disetujui\"}")
        }
        val after = client.okGet("/consumer/wallet", rina).bodyAsText()
            .let { json.decodeFromString<WalletDto>(it).balance }
        assertEquals(before + 10_000L, after)
    }


    @Test
    fun `technician operations endpoints work`() = testApplication {
        setupApp()
        val tech = client.loginToken("budi", "budi12345")
        val admin = client.loginToken("admin", "admin12345")

        val document = client.post("/technician/documents") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddDocumentRequest.serializer(), AddDocumentRequest("KTP", "/photos/ktp.jpg")))
        }
        assertEquals(HttpStatusCode.Created, document.status, document.bodyAsText())
        val docId = json.decodeFromString<TechDocumentDto>(document.bodyAsText()).id
        val verified = client.post("/admin/technician-documents/$docId/verify") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(VerifyDocumentRequest.serializer(), VerifyDocumentRequest(true, "Valid")))
        }
        assertEquals(HttpStatusCode.OK, verified.status)
        assertEquals("TERVERIFIKASI", json.decodeFromString<TechDocumentDto>(verified.bodyAsText()).status)

        val area = client.post("/technician/areas") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddServiceAreaRequest.serializer(), AddServiceAreaRequest("Jakarta Selatan", -6.26, 106.80, 15, false)))
        }
        assertEquals(HttpStatusCode.Created, area.status)
        val areas = client.okGet("/technician/areas", tech).bodyAsText()
            .let { json.decodeFromString<List<ServiceAreaDto>>(it) }
        assertTrue(areas.any { it.areaName == "Jakarta Selatan" })

        val rule = client.put("/technician/auto-accept") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveAutoAcceptRequest.serializer(), SaveAutoAcceptRequest(false, 150_000, 25, "")))
        }
        assertEquals(HttpStatusCode.OK, rule.status)
        val saved = json.decodeFromString<AutoAcceptDto>(rule.bodyAsText())
        assertTrue(!saved.enabled)

        val template = client.post("/technician/chat-templates") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddTemplateRequest.serializer(), AddTemplateRequest("Salam", "Halo, saya teknisi terverifikasi")))
        }
        assertEquals(HttpStatusCode.Created, template.status)
        val journal = client.post("/technician/journal") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddJournalRequest.serializer(), AddJournalRequest(
                java.time.LocalDate.now().toString(), "Menyiapkan peralatan"
            )))
        }
        assertEquals(HttpStatusCode.Created, journal.status)
        val leave = client.post("/technician/leaves") {
            bearer(tech)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(AddLeaveRequest.serializer(), AddLeaveRequest(
                java.time.LocalDate.now().plusDays(5).toString(),
                java.time.LocalDate.now().plusDays(6).toString(), "Libur"
            )))
        }
        assertEquals(HttpStatusCode.Created, leave.status)
        val equipment = client.okGet("/technician/equipment", tech).bodyAsText()
            .let { json.decodeFromString<List<EquipmentDto>>(it) }
        assertTrue(equipment.isNotEmpty())
        val badges = client.okGet("/technician/badges", tech).bodyAsText()
            .let { json.decodeFromString<List<BadgeDto>>(it) }
        assertTrue(badges.isNotEmpty())
        val stats = client.okGet("/technician/ops-stats", tech).bodyAsText()
            .let { json.decodeFromString<OpsStatsDto>(it) }
        assertTrue(stats.level in 1..3)
    }

    @Test
    fun `checkin and material require technician ownership`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val outsider = client.loginToken("sari", "sari12345")
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val orderId = client.createTestOrder(consumer, serviceId, addressId, slotId)
        val ownerId = transaction {
            val id = UsersTable.selectAll().where { UsersTable.username eq "budi" }.single()[UsersTable.id]
            OrdersTable.update({ OrdersTable.id eq orderId }) {
                it[technicianId] = id
                it[status] = OrderStatus.DALAM_PERJALANAN.name
            }
            id
        }
        val owner = client.loginToken("budi", "budi12345")
        val address = transaction {
            val addressIdRow = OrdersTable.selectAll().where { OrdersTable.id eq orderId }.single()[OrdersTable.addressId]
            val row = AddressesTable.selectAll().where { AddressesTable.id eq addressIdRow }.single()
            row[AddressesTable.lat] to row[AddressesTable.lng]
        }
        val success = client.post("/technician/orders/$orderId/checkin") {
            bearer(owner)
            contentType(ContentType.Application.Json)
            setBody("{\"kind\":\"DATANG\",\"lat\":${address.first},\"lng\":${address.second}}")
        }
        assertEquals(HttpStatusCode.OK, success.status, success.bodyAsText())
        val history = client.okGet("/technician/orders/$orderId/checkins", owner).bodyAsText()
            .let { json.decodeFromString<List<CheckinDto>>(it) }
        assertTrue(history.any { it.kind == "DATANG" })
        val historyForbidden = client.get("/technician/orders/$orderId/checkins") { bearer(outsider) }
        assertEquals(HttpStatusCode.Forbidden, historyForbidden.status)

        val forbidden = client.post("/technician/orders/$orderId/checkin") {
            bearer(outsider)
            contentType(ContentType.Application.Json)
            setBody("{\"kind\":\"DATANG\",\"lat\":-6.26,\"lng\":106.80}")
        }
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertTrue(ownerId > 0)
        val materialForbidden = client.post("/technician/materials") {
            bearer(outsider)
            contentType(ContentType.Application.Json)
            setBody("{\"orderId\":$orderId,\"name\":\"Kabel\",\"quantity\":1,\"cost\":10000}")
        }
        assertEquals(HttpStatusCode.Forbidden, materialForbidden.status)
    }


    @Test
    fun `platform flags content broadcast moderation and sla work`() = testApplication {
        setupApp()
        val admin = client.loginToken("admin", "admin12345")

        val flag = client.put("/admin/flags/chat_v2") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveFeatureFlagRequest.serializer(), SaveFeatureFlagRequest(true, "KONSUMEN", "Uji")))
        }
        assertEquals(HttpStatusCode.OK, flag.status)
        val publicFlags = client.get("/flags?role=KONSUMEN").bodyAsText()
            .let { json.decodeFromString<List<FeatureFlagDto>>(it) }
        assertTrue(publicFlags.any { it.key == "chat_v2" && it.enabled })

        val page = client.put("/admin/content") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveContentRequest.serializer(), SaveContentRequest("faq", "FAQ", "Isi FAQ")))
        }
        assertEquals(HttpStatusCode.OK, page.status)
        val publicPage = client.get("/content/faq").bodyAsText()
            .let { json.decodeFromString<ContentDto>(it) }
        assertEquals("FAQ", publicPage.title)

        val broadcast = client.post("/admin/broadcasts") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(
                CreateBroadcastRequest.serializer(),
                CreateBroadcastRequest("Siaran uji", "Isi siaran", "KONSUMEN", OffsetDateTime.now().plusDays(1).toString())
            ))
        }
        assertEquals(HttpStatusCode.Created, broadcast.status)

        val policy = client.put("/admin/sla-policies/CONFIRM") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(SaveSlaPolicyRequest.serializer(), SaveSlaPolicyRequest(60, 5000, true)))
        }
        assertEquals(HttpStatusCode.OK, policy.status)
        assertTrue(client.okGet("/admin/sla-policies", admin).bodyAsText().contains("CONFIRM"))

        val weekly = client.post("/admin/weekly-reports/generate") { bearer(admin) }
        assertEquals(HttpStatusCode.OK, weekly.status)
        val summary = client.okGet("/admin/platform-summary", admin).bodyAsText()
            .let { json.decodeFromString<PlatformSummaryDto>(it) }
        assertTrue(summary.totalUsers >= 3)
    }

    @Test
    fun `review report requires party and admin can resolve`() = testApplication {
        setupApp()
        val consumer = client.loginToken("rina", "rina12345")
        val admin = client.loginToken("admin", "admin12345")
        val serviceId = client.okGet("/consumer/services", consumer).bodyAsText()
            .let { json.decodeFromString<List<ServiceDto>>(it).first().id }
        val addressId = client.okGet("/consumer/addresses", consumer).bodyAsText()
            .let { json.decodeFromString<List<AddressDto>>(it).first().id }
        val slotId = client.okGet("/consumer/slots?date=${java.time.LocalDate.now().plusDays(3)}", consumer).bodyAsText()
            .let { json.decodeFromString<List<SlotDto>>(it).first().id }
        val orderId = client.createTestOrder(consumer, serviceId, addressId, slotId)
        transaction {
            RatingsTable.insert {
                it[RatingsTable.orderId] = orderId
                it[stars] = 4
                it[tags] = ""
                it[comment] = "Ulasan uji"
                it[createdAt] = OffsetDateTime.now()
            }
        }
        val report = client.post("/consumer/reviews/$orderId/report") {
            bearer(consumer)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ReportReviewRequest.serializer(), ReportReviewRequest("Tidak pantas")))
        }
        assertEquals(HttpStatusCode.OK, report.status)
        val reports = client.okGet("/admin/review-reports", admin).bodyAsText()
            .let { json.decodeFromString<List<ReviewReportDto>>(it) }
        val found = reports.first { it.reviewOrderId == orderId }
        val resolved = client.post("/admin/review-reports/${found.id}/resolve") {
            bearer(admin)
            contentType(ContentType.Application.Json)
            setBody(json.encodeToString(ResolveReviewReportRequest.serializer(), ResolveReviewReportRequest(true)))
        }
        assertEquals(HttpStatusCode.OK, resolved.status)
    }

}
