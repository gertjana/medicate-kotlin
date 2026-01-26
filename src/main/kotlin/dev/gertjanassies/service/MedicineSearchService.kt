package dev.gertjanassies.service

import dev.gertjanassies.model.MedicineSearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.File

object MedicineSearchService {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val json = Json { ignoreUnknownKeys = true }

    // Cache for medicines data
    private var medicinesData: List<MedicineSearchResult>? = null

    init {
        loadMedicinesData()
    }

    private fun loadMedicinesData() {
        try {
            // Get data directory from environment variable or use default
            val dataDir = System.getenv("MEDICINES_DATA_DIR") ?: "data"

            // Try multiple locations for medicines.json
            val possibleLocations = listOf(
                "$dataDir/medicines.json",     // Configured/default data directory
                "/app/data/medicines.json",    // Docker fallback
                "scripts/medicines.json"       // Legacy location
            )

            val medicinesFile = possibleLocations
                .map { File(it) }
                .firstOrNull { it.exists() }

            if (medicinesFile == null) {
                logger.warn("Medicines database not found in any of: ${possibleLocations.joinToString()}")
                return
            }

            val medicinesJson = medicinesFile.readText()

            // Parse JSON
            val jsonElement = json.parseToJsonElement(medicinesJson)
            val medicines = mutableListOf<MedicineSearchResult>()

            jsonElement.jsonArray.forEach { item ->
                val obj = item.jsonObject
                val productnaam = obj["productnaam"]?.jsonPrimitive?.content ?: ""
                val farmaceutischevorm = obj["farmaceutischevorm"]?.jsonPrimitive?.content ?: ""
                val werkzamestoffen = obj["werkzamestoffen"]?.jsonPrimitive?.content ?: ""
                val bijsluiterFilenaam = obj["bijsluiter_filenaam"]?.jsonPrimitive?.content ?: ""

                if (productnaam.isNotEmpty()) {
                    medicines.add(MedicineSearchResult(productnaam, farmaceutischevorm, werkzamestoffen, bijsluiterFilenaam))
                }
            }

            medicinesData = medicines
            logger.info("Loaded ${medicines.size} medicines from database at ${medicinesFile.absolutePath}")
        } catch (e: Exception) {
            logger.error("Failed to load medicines.json", e)
        }
    }

    fun searchMedicines(query: String, limit: Int = 10): List<MedicineSearchResult> {
        if (query.length < 2 || limit <= 0) {
            return emptyList()
        }

        val data = medicinesData ?: return emptyList()
        val lowerQuery = query.trim().lowercase()

        return data
            .filter { it.productnaam.lowercase().contains(lowerQuery) }
            .take(limit)
    }
}
