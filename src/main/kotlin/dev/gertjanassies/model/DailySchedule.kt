package dev.gertjanassies.model

import kotlinx.serialization.Serializable

@Serializable
data class MedicineScheduleItem(
    val medicine: Medicine,
    val amount: Double
)

@Serializable
data class TimeSlot(
    val time: String,
    val medicines: List<MedicineScheduleItem>
)

@Serializable
data class DailySchedule(
    val schedule: List<TimeSlot>
)
