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
        val threshold = call.request.queryParameters["threshold"]?.toDoubleOrNull() ?: 10.0
        either {
            val lowStockMedicines = redisService.getLowStockMedicines(threshold).bind()
            call.respond(HttpStatusCode.OK, lowStockMedicines)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
