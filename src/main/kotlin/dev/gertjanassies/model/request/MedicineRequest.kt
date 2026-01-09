package dev.gertjanassies.model.request

import kotlinx.serialization.Serializable

@Serializable
data class MedicineRequest(
    val name: String,
    val dose: Double,
    val unit: String,
    val stock: Double
)
