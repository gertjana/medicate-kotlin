package dev.gertjanassies.model.response

import dev.gertjanassies.model.User
import kotlinx.serialization.Serializable

@Serializable
data class UserResponse(
    val username: String,
    val email: String
)

fun User.toResponse() = UserResponse(username = username, email = email)
