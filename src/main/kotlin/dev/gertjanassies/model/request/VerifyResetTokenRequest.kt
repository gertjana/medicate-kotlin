package dev.gertjanassies.model.request

import kotlinx.serialization.Serializable

@Serializable
data class VerifyResetTokenRequest(
    val token: String
)
