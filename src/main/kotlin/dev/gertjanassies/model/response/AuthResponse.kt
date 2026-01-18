package dev.gertjanassies.model.response

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val user: UserResponse,
    val token: String,  // Access token (short-lived, 1 hour)
    val refreshToken: String  // Refresh token (long-lived, 30 days)
)
