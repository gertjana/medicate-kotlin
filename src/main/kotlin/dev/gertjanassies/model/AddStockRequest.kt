package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class AddStockRequest(
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val amount: Double
)
