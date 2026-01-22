package dev.gertjanassies.model.request

import kotlinx.serialization.Serializable

@Serializable
data class PasswordResetRequest(
    val email: String
)
