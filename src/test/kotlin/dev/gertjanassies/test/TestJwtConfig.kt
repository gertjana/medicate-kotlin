package dev.gertjanassies.test

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import java.util.*

/**
 * Test JWT configuration for protected route tests
 */
object TestJwtConfig {
    const val SECRET = "test-secret-key-for-testing-only"
    const val ISSUER = "medicate-app"
    const val AUDIENCE = "medicate-users"

    /**
     * Generate a test JWT token for a given username
     */
    fun generateToken(username: String, userId: String = UUID.randomUUID().toString(), isAdmin: Boolean = false): String {
        return JWT.create()
            .withAudience(AUDIENCE)
            .withIssuer(ISSUER)
            .withClaim("username", username)
            .withClaim("userId", userId)
            .withClaim("isAdmin", isAdmin)
            .withExpiresAt(Date(System.currentTimeMillis() + 3600000)) // 1 hour
            .sign(Algorithm.HMAC256(SECRET))
    }

    /**
     * Install JWT authentication for testing
     * Call this in your testApplication block before routing
     */
    fun Application.installTestJwtAuth() {
        install(Authentication) {
            jwt("auth-jwt") {
                verifier(
                    JWT.require(Algorithm.HMAC256(SECRET))
                        .withAudience(AUDIENCE)
                        .withIssuer(ISSUER)
                        .build()
                )
                validate { credential ->
                    val username = credential.payload.getClaim("username").asString()
                    if (username != null) {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
                challenge { _, _ ->
                    call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
                }
            }
        }
    }
}
