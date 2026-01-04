package dev.gertjanassies.model

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class DosageHistoryRequest(
    @Serializable(with = UUIDSerializer::class)
    val medicineId: UUID,
    val amount: Double
)
