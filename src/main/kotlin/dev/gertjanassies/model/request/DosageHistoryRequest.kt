package dev.gertjanassies.model.request

import dev.gertjanassies.model.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class DosageHistoryRequest(
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val amount: Double,
    val scheduledTime: String? = null
)
