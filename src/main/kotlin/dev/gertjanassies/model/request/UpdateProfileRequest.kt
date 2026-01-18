package dev.gertjanassies.model.request

import kotlinx.serialization.Serializable

@Serializable
data class UpdateProfileRequest(
    val email: String,
    val firstName: String,
    val lastName: String
)
