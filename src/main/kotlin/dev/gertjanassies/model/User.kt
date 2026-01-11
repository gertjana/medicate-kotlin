package dev.gertjanassies.model

import kotlinx.serialization.Serializable

@Serializable
data class User(
    val username: String,
    val email: String = "",
    val passwordHash: String = ""
)
