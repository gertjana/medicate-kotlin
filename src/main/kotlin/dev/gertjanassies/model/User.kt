package dev.gertjanassies.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val passwordHash: String = ""
)
