package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Schedule(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val time: String, // e.g., "08:00", "12:00"
    val amount: Double, // Amount of medicine to take
    @Serializable(with = DayOfWeekListSerializer::class)
    val daysOfWeek: List<DayOfWeek> = emptyList() // Empty list means all days
)
