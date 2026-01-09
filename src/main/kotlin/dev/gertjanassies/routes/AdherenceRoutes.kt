package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Adherence and analytics routes
 */
fun Route.adherenceRoutes(redisService: RedisService) {
    // Get weekly adherence
    get("/adherence") {
        either {
            val weeklyAdherence = redisService.getWeeklyAdherence().bind()
            call.respond(HttpStatusCode.OK, weeklyAdherence)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get low stock medicines
    get("/lowstock") {
        val thresholdParam = call.request.queryParameters["threshold"]

        val threshold = if (thresholdParam == null) {
            10.0
        } else {
            val parsed = thresholdParam.toDoubleOrNull()
            if (parsed == null) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid threshold value. It must be a positive number.")
                )
                return@get
            }
            if (parsed <= 0.0) {
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Threshold must be greater than 0.")
                )
                return@get
            }
            parsed
        }
        either {
            val lowStockMedicines = redisService.getLowStockMedicines(threshold).bind()
            call.respond(HttpStatusCode.OK, lowStockMedicines)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
