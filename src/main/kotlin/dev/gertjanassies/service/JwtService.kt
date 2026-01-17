package dev.gertjanassies.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import java.util.*

/**
 * JWT Service for generating and validating authentication tokens
 */
class JwtService(
    private val secret: String,
    private val issuer: String = "medicate-app",
    private val audience: String = "medicate-users",
    private val expirationMs: Long = 24 * 60 * 60 * 1000 // 24 hours
) {
    private val algorithm = Algorithm.HMAC256(secret)

    /**
     * Generate a JWT token for a user
     */
    fun generateToken(username: String): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("username", username)
            .withExpiresAt(Date(System.currentTimeMillis() + expirationMs))
            .sign(algorithm)
    }

    /**
     * Validate a JWT token and extract the username
     * Returns null if token is invalid or expired
     */
    fun validateToken(token: String): String? {
        return try {
            val verifier = JWT.require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)
                .build()

            val decodedJWT = verifier.verify(token)
            decodedJWT.getClaim("username").asString()
        } catch (e: JWTVerificationException) {
            null // Token is invalid or expired
        }
    }

    /**
     * Get the expiration time for tokens (in milliseconds)
     */
    fun getExpirationMs(): Long = expirationMs
}
