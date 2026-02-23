package dev.gertjanassies.model.request

import kotlinx.serialization.Serializable

@Serializable
data class PasswordUpdateRequest(
    val token: String,
    val password: String
)
