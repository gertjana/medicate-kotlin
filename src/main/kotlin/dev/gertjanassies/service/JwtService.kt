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
    private val accessTokenExpirationMs: Long = 60 * 60 * 1000, // 1 hour
    private val refreshTokenExpirationMs: Long = 30L * 24 * 60 * 60 * 1000 // 30 days
) {
    private val algorithm = Algorithm.HMAC256(secret)

    /**
     * Generate an access token for a user (short-lived)
     */
    fun generateAccessToken(username: String, userId: String, isAdmin: Boolean = false): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("username", username)
            .withClaim("userId", userId)
            .withClaim("isAdmin", isAdmin)
            .withClaim("type", "access")
            .withExpiresAt(Date(System.currentTimeMillis() + accessTokenExpirationMs))
            .sign(algorithm)
    }

    /**
     * Generate a refresh token for a user (long-lived)
     */
    fun generateRefreshToken(username: String, userId: String, isAdmin: Boolean = false): String {
        return JWT.create()
            .withAudience(audience)
            .withIssuer(issuer)
            .withClaim("username", username)
            .withClaim("userId", userId)
            .withClaim("isAdmin", isAdmin)
            .withClaim("type", "refresh")
            .withExpiresAt(Date(System.currentTimeMillis() + refreshTokenExpirationMs))
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
     * Validate a refresh token specifically
     * Returns the username if valid, null otherwise
     */
    fun validateRefreshToken(token: String): String? {
        return try {
            val verifier = JWT.require(algorithm)
                .withAudience(audience)
                .withIssuer(issuer)
                .withClaim("type", "refresh")
                .build()

            val decodedJWT = verifier.verify(token)
            decodedJWT.getClaim("username").asString()
        } catch (e: JWTVerificationException) {
            null // Token is invalid or expired
        }
    }

    /**
     * Get the expiration time for access tokens (in milliseconds)
     */
    fun getAccessTokenExpirationMs(): Long = accessTokenExpirationMs

    /**
     * Get the expiration time for refresh tokens (in milliseconds)
     */
    fun getRefreshTokenExpirationMs(): Long = refreshTokenExpirationMs

    /**
     * Get the expiration time for tokens (in milliseconds)
     * @deprecated Use getAccessTokenExpirationMs instead
     */
    @Deprecated("Use getAccessTokenExpirationMs instead", ReplaceWith("getAccessTokenExpirationMs()"))
    fun getExpirationMs(): Long = accessTokenExpirationMs
}
