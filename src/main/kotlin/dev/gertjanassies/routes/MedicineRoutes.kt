package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.MedicineSearchResult
import dev.gertjanassies.model.request.AddStockRequest
import dev.gertjanassies.model.request.DosageHistoryRequest
import dev.gertjanassies.model.request.MedicineRequest
import dev.gertjanassies.service.MedicineSearchService
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("MedicineRoutes")

/**
 * Medicine routes
 */
fun Route.medicineRoutes(storageService: StorageService) {

    // Get all medicines
    get("/medicine") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        either {
            val medicines = storageService.getAllMedicines(userId).bind()
            logger.debug("Successfully retrieved ${medicines.size} medicines for user ID: $userId")
            call.respond(HttpStatusCode.OK, medicines)
        }.onLeft { error ->
            logger.error("Failed to get all medicines for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get medicine by ID
    get("/medicine/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@get
        }

        either {
            val medicine = storageService.getMedicine(userId, id).bind()
            logger.debug("Successfully retrieved medicine '$id' (${medicine.name}) for user ID: $userId")
            call.respond(HttpStatusCode.OK, medicine)
        }.onLeft { error ->
            logger.error("Failed to get medicine '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    // Create new medicine
    post("/medicine") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@post
        }

        val request = call.receive<MedicineRequest>()

        either {
            val created = storageService.createMedicine(userId, request).bind()
            logger.debug("Successfully created medicine '${created.name}' (${created.id}) for user ID: $userId")
            call.respond(HttpStatusCode.Created, created)
        }.onLeft { error ->
            logger.error("Failed to create medicine for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Update existing medicine
    put("/medicine/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@put
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@put
        }

        val medicine = call.receive<Medicine>()

        either {
            val updated = storageService.updateMedicine(userId, id, medicine).bind()
            logger.debug("Successfully updated medicine '$id' (${updated.name}) for user ID: $userId")
            call.respond(HttpStatusCode.OK, updated)
        }.onLeft { error ->
            logger.error("Failed to update medicine '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    // Delete medicine
    delete("/medicine/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@delete
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@delete
        }

        either {
            storageService.deleteMedicine(userId, id).bind()
            logger.debug("Successfully deleted medicine '$id' for user ID: $userId")
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
            logger.error("Failed to delete medicine '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    // Record a dose taken
    post("/takedose") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@post
        }

        val request = call.receive<DosageHistoryRequest>()

        either {
            val dosageHistory = storageService.createDosageHistory(userId, request.medicineId, request.amount, request.scheduledTime, request.datetime).bind()
            logger.debug("Successfully recorded dose for medicine '${request.medicineId}' (amount: ${request.amount}) for user ID: $userId")
            call.respond(HttpStatusCode.Created, dosageHistory)
        }.onLeft { error ->
            logger.error("Failed to record dose for medicine '${request.medicineId}' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    // Add stock to medicine
    post("/addstock") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@post
        }

        val request = call.receive<AddStockRequest>()

        either {
            val updatedMedicine = storageService.addStock(userId, request.medicineId, request.amount).bind()
            logger.debug("Successfully added ${request.amount} stock to medicine '${request.medicineId}' for user ID: $userId (new stock: ${updatedMedicine.stock})")
            call.respond(HttpStatusCode.OK, updatedMedicine)
        }.onLeft { error ->
            logger.error("Failed to add stock to medicine '${request.medicineId}' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    get("/medicineExpiry") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        either {
            val expiringMedicines = storageService.medicineExpiry(userId).bind()
            logger.debug("Successfully retrieved ${expiringMedicines.size} medicine expiry records for user ID: $userId")
            call.respond(HttpStatusCode.OK, expiringMedicines)
        }.onLeft { error ->
            logger.error("Failed to get medicine expiry for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
