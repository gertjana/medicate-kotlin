package dev.gertjanassies.model

import kotlinx.serialization.Serializable

@Serializable
data class MedicineSearchResult(
    val productnaam: String,
    val farmaceutischevorm: String,
    val werkzamestoffen: String,
    val bijsluiter_filenaam: String = ""
)
