package dev.gertjanassies.model.response

import kotlinx.serialization.Serializable

@Serializable
data class ActivationResponse(
    val message: String,
    val user: UserResponse,
    val token: String  // Access token
)
