package dev.gertjanassies.model.request

import dev.gertjanassies.model.serializer.UUIDSerializer
import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.util.*

@Serializable
data class DosageHistoryRequest(
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val amount: Double,
    val scheduledTime: String? = null,
    @Serializable(with = LocalDateTimeSerializer::class)
    val datetime: LocalDateTime? = null
)
