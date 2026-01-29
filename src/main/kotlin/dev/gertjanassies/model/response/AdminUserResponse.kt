package dev.gertjanassies.model.response

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserResponse(
    val id: String,
    val username: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val isActive: Boolean,
    val isAdmin: Boolean,
    val isSelf: Boolean
)

@Serializable
data class AdminUsersListResponse(
    val users: List<AdminUserResponse>
)
