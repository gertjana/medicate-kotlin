package dev.gertjanassies.model

import dev.gertjanassies.model.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class User(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val username: String,
    val email: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val passwordHash: String = ""
)
