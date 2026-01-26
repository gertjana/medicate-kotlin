package dev.gertjanassies.routes

import dev.gertjanassies.model.MedicineSearchResult
import dev.gertjanassies.service.MedicineSearchService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("MedicineSearchRoutes")

fun Route.medicineSearchRoutes() {
    route("/medicines") {
        get("/search") {
            val query = call.parameters["q"]
            if (query == null) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Query parameter 'q' is required"))
                return@get
            }
            
            val queryLower = query.lowercase()
            if (queryLower.length < 2) {
                call.respond(emptyList<MedicineSearchResult>())
                return@get
            }

            try {
                val medicines = loadMedicinesDatabase()
                val results = medicines
                    .filter { it.productnaam.lowercase().contains(queryLower) }
                    .take(10)

                call.respond(results)
            } catch (e: Exception) {
                logger.error("Failed to search medicines: ${e.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to search medicines"))
            }
        }
    }
}
