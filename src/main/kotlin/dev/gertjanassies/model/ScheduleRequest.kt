package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ScheduleRequest(
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val time: String,
    val amount: Double
)
