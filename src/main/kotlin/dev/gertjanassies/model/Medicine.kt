package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Medicine(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val dose: Double,
    val unit: String,
    val stock: Double
)
