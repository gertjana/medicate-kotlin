package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.*

@Serializable
data class DosageHistory(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = LocalDateTimeSerializer::class)
    val datetime: LocalDateTime,
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val amount: Double
)
