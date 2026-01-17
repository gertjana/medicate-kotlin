package dev.gertjanassies.model.response

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val user: UserResponse,
    val token: String
)
