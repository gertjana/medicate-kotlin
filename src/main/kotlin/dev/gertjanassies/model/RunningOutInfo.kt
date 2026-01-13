package dev.gertjanassies.model

import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.UUID

@Serializable
data class MedicineWithExpiry(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val dose: Double,
    val unit: String,
    val stock: Double,
    val description: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val expiryDate: LocalDateTime?
)
