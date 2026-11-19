package id.tukangkilat.backend

import at.favre.lib.crypto.bcrypt.BCrypt
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import id.tukangkilat.shared.Role
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.Date

/** Password hashing with BCrypt (cost 12). */
object Passwords {
    fun hash(raw: String): String =
        BCrypt.withDefaults().hashToString(12, raw.toCharArray())

    fun verify(raw: String, hash: String): Boolean =
        BCrypt.verifyer().verify(raw.toCharArray(), hash).verified
}

/** JWT issuer/verifier for access tokens. Refresh tokens are opaque and stored hashed. */
class JwtService(private val secret: String, private val issuer: String) {
    private val keyBytes: ByteArray = secret.toByteArray(Charsets.UTF_8).let {
        if (it.size >= 32) it else MessageDigest.getInstance("SHA-256").digest(it)
    }
    private val algorithm: Algorithm = Algorithm.HMAC256(keyBytes)
    val verifier: JWTVerifier = JWT.require(algorithm).withIssuer(issuer).build()

    fun createAccessToken(userId: Long, role: Role, username: String, fullName: String): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(issuer)
            .withSubject(userId.toString())
            .withClaim("role", role.name)
            .withClaim("username", username)
            .withClaim("fullName", fullName)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + ACCESS_TOKEN_TTL_MS))
            .sign(algorithm)
    }

    fun parseAccessToken(token: String): DecodedJWT = verifier.verify(token)

    companion object {
        const val ACCESS_TOKEN_TTL_MS = 15 * 60 * 1000L // 15 minutes
        const val REFRESH_TOKEN_TTL_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
    }
}

/** Opaque refresh tokens: 48 random bytes, only the SHA-256 hash is stored. */
object RefreshTokens {
    private val secureRandom = SecureRandom()

    fun generate(): String {
        val bytes = ByteArray(48)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    fun sha256(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(digest)
    }
}

/** Fixed-window in-memory rate limiter, keyed by arbitrary string (e.g. IP + route). */
class RateLimiter(private val maxRequests: Int, private val windowMs: Long) {
    private data class Bucket(var windowStart: Long, var count: Int)

    private val buckets = HashMap<String, Bucket>()

    @Synchronized
    fun allow(key: String): Boolean {
        val now = System.currentTimeMillis()
        val bucket = buckets[key]
        if (bucket == null || now - bucket.windowStart >= windowMs) {
            buckets[key] = Bucket(now, 1)
            return true
        }
        if (bucket.count >= maxRequests) return false
        bucket.count += 1
        return true
    }
}

/** Authenticated principal carried by the JWT. */
data class AuthUser(val id: Long, val role: Role, val username: String, val fullName: String)

fun ApplicationCall.authUser(): AuthUser {
    val principal = principal<JWTPrincipal>() ?: throw UnauthorizedException()
    val id = principal.subject?.toLongOrNull() ?: throw UnauthorizedException()
    val role = principal.get("role")?.let { runCatching { Role.valueOf(it) }.getOrNull() } ?: throw UnauthorizedException()
    val username = principal.get("username") ?: ""
    val fullName = principal.get("fullName") ?: ""
    return AuthUser(id, role, username, fullName)
}

fun ApplicationCall.requireRole(vararg roles: Role): AuthUser {
    val user = authUser()
    if (user.role !in roles) throw ForbiddenException()
    return user
}
