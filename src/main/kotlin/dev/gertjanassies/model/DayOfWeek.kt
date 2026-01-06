package dev.gertjanassies.model

import kotlinx.serialization.Serializable

@Serializable
enum class DayOfWeek(val code: String) {
    MONDAY("MO"),
    TUESDAY("TU"),
    WEDNESDAY("WE"),
    THURSDAY("TH"),
    FRIDAY("FR"),
    SATURDAY("SA"),
    SUNDAY("SU");

    companion object {
        fun fromCode(code: String): DayOfWeek? {
            return values().find { it.code == code.uppercase() }
        }
        
        fun fromJavaDay(day: java.time.DayOfWeek): DayOfWeek {
            return when (day) {
                java.time.DayOfWeek.MONDAY -> MONDAY
                java.time.DayOfWeek.TUESDAY -> TUESDAY
                java.time.DayOfWeek.WEDNESDAY -> WEDNESDAY
                java.time.DayOfWeek.THURSDAY -> THURSDAY
                java.time.DayOfWeek.FRIDAY -> FRIDAY
                java.time.DayOfWeek.SATURDAY -> SATURDAY
                java.time.DayOfWeek.SUNDAY -> SUNDAY
            }
        }
    }
}
