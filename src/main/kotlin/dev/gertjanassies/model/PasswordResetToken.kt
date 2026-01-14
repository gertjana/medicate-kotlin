package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class PasswordResetToken(
    val token: String,
    @Serializable(with = dev.gertjanassies.model.serializer.LocalDateTimeSerializer::class)
    val expiresAt: LocalDateTime
)
