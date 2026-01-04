package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.AddStockRequest
import dev.gertjanassies.model.DosageHistoryRequest
import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.MedicineRequest
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

/**
 * Medicine routes
 */
fun Route.medicineRoutes(redisService: RedisService) {
    // Get all medicines
    get("/medicine") {
        either {
            val medicines = redisService.getAllMedicines().bind()
            call.respond(HttpStatusCode.OK, medicines)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get medicine by ID
    get("/medicine/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@get
        }

        either {
            val medicine = redisService.getMedicine(id).bind()
            call.respond(HttpStatusCode.OK, medicine)
        }.onLeft { error ->
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
        val request = call.receive<MedicineRequest>()
        
        either {
            val created = redisService.createMedicine(request).bind()
            call.respond(HttpStatusCode.Created, created)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Update existing medicine
    put("/medicine/{id}") {
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@put
        }

        val medicine = call.receive<Medicine>()
        
        either {
            val updated = redisService.updateMedicine(id, medicine).bind()
            call.respond(HttpStatusCode.OK, updated)
        }.onLeft { error ->
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
        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@delete
        }

        either {
            redisService.deleteMedicine(id).bind()
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
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
        val request = call.receive<DosageHistoryRequest>()
        
        either {
            val dosageHistory = redisService.createDosageHistory(request.medicineId, request.amount).bind()
            call.respond(HttpStatusCode.Created, dosageHistory)
        }.onLeft { error ->
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
        val request = call.receive<AddStockRequest>()
        
        either {
            val updatedMedicine = redisService.addStock(request.medicineId, request.amount).bind()
            call.respond(HttpStatusCode.OK, updatedMedicine)
        }.onLeft { error ->
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }
}
