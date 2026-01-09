package dev.gertjanassies.model.request

import dev.gertjanassies.model.DayOfWeek
import dev.gertjanassies.model.serializer.DayOfWeekListSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ScheduleRequest(
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val time: String,
    val amount: Double,
    @Serializable(with = DayOfWeekListSerializer::class)
    val daysOfWeek: List<DayOfWeek> = emptyList()
)
