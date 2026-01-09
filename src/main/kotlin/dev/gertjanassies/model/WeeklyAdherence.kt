 package dev.gertjanassies.model

import kotlinx.serialization.Serializable

@Serializable
data class DayAdherence(
    val date: String, // ISO format date string (yyyy-MM-dd)
    val dayOfWeek: String,
    val dayNumber: Int,
    val month: Int,
    val status: AdherenceStatus,
    val expectedCount: Int,
    val takenCount: Int
)

@Serializable
enum class AdherenceStatus {
    NONE,
    PARTIAL,
    COMPLETE
}

@Serializable
data class WeeklyAdherence(
    val days: List<DayAdherence>
)
