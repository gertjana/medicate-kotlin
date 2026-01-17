package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.request.AddStockRequest
import dev.gertjanassies.model.request.DosageHistoryRequest
import dev.gertjanassies.model.request.MedicineRequest
import dev.gertjanassies.service.RedisService
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
 * Helper function to extract username from JWT token
 */
private fun ApplicationCall.getUsername(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString()
}

/**
 * Medicine routes
 */
fun Route.medicineRoutes(redisService: RedisService) {
    // Get all medicines
    get("/medicine") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val medicines = redisService.getAllMedicines(username).bind()
            logger.debug("Successfully retrieved ${medicines.size} medicines for user '$username'")
            call.respond(HttpStatusCode.OK, medicines)
        }.onLeft { error ->
            logger.error("Failed to get all medicines for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get medicine by ID
    get("/medicine/{id}") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@get
        }

        either {
            val medicine = redisService.getMedicine(username, id).bind()
            logger.debug("Successfully retrieved medicine '$id' (${medicine.name}) for user '$username'")
            call.respond(HttpStatusCode.OK, medicine)
        }.onLeft { error ->
            logger.error("Failed to get medicine '$id' for user '$username': ${error.message}")
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
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@post
        }

        val request = call.receive<MedicineRequest>()

        either {
            val created = redisService.createMedicine(username, request).bind()
            logger.debug("Successfully created medicine '${created.name}' (${created.id}) for user '$username'")
            call.respond(HttpStatusCode.Created, created)
        }.onLeft { error ->
            logger.error("Failed to create medicine for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Update existing medicine
    put("/medicine/{id}") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@put
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@put
        }

        val medicine = call.receive<Medicine>()

        either {
            val updated = redisService.updateMedicine(username, id, medicine).bind()
            logger.debug("Successfully updated medicine '$id' (${updated.name}) for user '$username'")
            call.respond(HttpStatusCode.OK, updated)
        }.onLeft { error ->
            logger.error("Failed to update medicine '$id' for user '$username': ${error.message}")
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
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@delete
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@delete
        }

        either {
            redisService.deleteMedicine(username, id).bind()
            logger.debug("Successfully deleted medicine '$id' for user '$username'")
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
            logger.error("Failed to delete medicine '$id' for user '$username': ${error.message}")
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
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@post
        }

        val request = call.receive<DosageHistoryRequest>()

        either {
            val dosageHistory = redisService.createDosageHistory(username, request.medicineId, request.amount, request.scheduledTime, request.datetime).bind()
            logger.debug("Successfully recorded dose for medicine '${request.medicineId}' (amount: ${request.amount}) for user '$username'")
            call.respond(HttpStatusCode.Created, dosageHistory)
        }.onLeft { error ->
            logger.error("Failed to record dose for medicine '${request.medicineId}' for user '$username': ${error.message}")
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
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@post
        }

        val request = call.receive<AddStockRequest>()

        either {
            val updatedMedicine = redisService.addStock(username, request.medicineId, request.amount).bind()
            logger.debug("Successfully added ${request.amount} stock to medicine '${request.medicineId}' for user '$username' (new stock: ${updatedMedicine.stock})")
            call.respond(HttpStatusCode.OK, updatedMedicine)
        }.onLeft { error ->
            logger.error("Failed to add stock to medicine '${request.medicineId}' for user '$username': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    get("/medicineExpiry") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val expiringMedicines = redisService.medicineExpiry(username).bind()
            logger.debug("Successfully retrieved ${expiringMedicines.size} medicine expiry records for user '$username'")
            call.respond(HttpStatusCode.OK, expiringMedicines)
        }.onLeft { error ->
            logger.error("Failed to get medicine expiry for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
