package id.tukangkilat.backend

import id.tukangkilat.shared.ApiError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

// Typed exceptions mapped by StatusPages to the uniform { code, message } shape.

class UnauthorizedException(message: String = "Sesi berakhir atau belum masuk") : Exception(message)
class ForbiddenException(message: String = "Anda tidak punya akses ke sumber daya ini") : Exception(message)
class NotFoundException(message: String = "Data tidak ditemukan") : Exception(message)
open class ConflictException(message: String = "Permintaan bertabrakan dengan kondisi saat ini") : Exception(message)
class BadRequestException(message: String = "Permintaan tidak valid") : Exception(message)
class UnprocessableException(message: String = "Data tidak bisa diproses") : Exception(message)
class RateLimitedException(message: String = "Terlalu banyak permintaan, coba lagi sebentar lagi") : Exception(message)

/** Illegal order transition (409) per PRD section 4 and 10. */
class IllegalTransitionException(from: String, to: String) :
    ConflictException("Transisi pesanan dari $from ke $to tidak sah")

private fun error(code: String, message: String) = ApiError(code, message)

fun Application.installErrorPages() {
    install(StatusPages) {
        exception<UnauthorizedException> { call, cause ->
            call.respond(HttpStatusCode.Unauthorized, error("UNAUTHORIZED", cause.message ?: "Tidak terautentikasi"))
        }
        exception<ForbiddenException> { call, cause ->
            call.respond(HttpStatusCode.Forbidden, error("FORBIDDEN", cause.message ?: "Akses ditolak"))
        }
        exception<NotFoundException> { call, cause ->
            call.respond(HttpStatusCode.NotFound, error("NOT_FOUND", cause.message ?: "Tidak ditemukan"))
        }
        exception<IllegalTransitionException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, error("ILLEGAL_TRANSITION", cause.message ?: "Transisi tidak sah"))
        }
        exception<ConflictException> { call, cause ->
            call.respond(HttpStatusCode.Conflict, error("CONFLICT", cause.message ?: "Konflik"))
        }
        exception<BadRequestException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, error("BAD_REQUEST", cause.message ?: "Permintaan salah"))
        }
        exception<UnprocessableException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, error("UNPROCESSABLE", cause.message ?: "Data tidak bisa diproses"))
        }
        exception<RateLimitedException> { call, cause ->
            call.respond(HttpStatusCode.TooManyRequests, error("RATE_LIMITED", cause.message ?: "Terlalu banyak permintaan"))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("Unhandled exception", cause)
            call.respond(HttpStatusCode.InternalServerError, error("INTERNAL", "Terjadi kesalahan pada server"))
        }
    }
}
