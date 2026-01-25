package dev.gertjanassies.routes

import dev.gertjanassies.model.MedicineSearchResult
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File


private val logger = LoggerFactory.getLogger("MedicineSearchRoutes")
private var medicinesCache: List<MedicineSearchResult>? = null

fun Route.medicineSearchRoutes() {
    route("/medicines") {
        get("/search") {
            val query = call.parameters["q"]?.lowercase() ?: ""
            if (query.length < 2) {
                call.respond(emptyList<MedicineSearchResult>())
                return@get
            }

            try {
                val medicines = loadMedicinesDatabase()
                val results = medicines
                    .filter { it.productnaam.lowercase().contains(query) }
                    .take(10)

                call.respond(results)
            } catch (e: Exception) {
                logger.error("Failed to search medicines: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to search medicines"))
            }
        }
    }
}

private fun loadMedicinesDatabase(): List<MedicineSearchResult> {
    if (medicinesCache != null) {
        return medicinesCache!!
    }

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
        return emptyList()
    }

    try {
        val json = Json { ignoreUnknownKeys = true }
        val content = medicinesFile.readText()
        val medicines = json.decodeFromString<List<MedicineSearchResult>>(content)
        medicinesCache = medicines
        logger.info("Loaded ${medicines.size} medicines from database at ${medicinesFile.absolutePath}")
        return medicines
    } catch (e: Exception) {
        logger.error("Failed to load medicines database: ${e.message}", e)
        return emptyList()
    }
}
