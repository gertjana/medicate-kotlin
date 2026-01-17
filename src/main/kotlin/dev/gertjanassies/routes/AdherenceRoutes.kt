package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdherenceRoutes")

/**
 * Helper function to extract username from JWT token
 */
private fun ApplicationCall.getUsername(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString()
}

/**
 * Adherence and analytics routes
 */
fun Route.adherenceRoutes(redisService: RedisService) {
    // Get weekly adherence
    get("/adherence") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val weeklyAdherence = redisService.getWeeklyAdherence(username).bind()
            logger.debug("Successfully retrieved weekly adherence for user '$username'")
            call.respond(HttpStatusCode.OK, weeklyAdherence)
        }.onLeft { error ->
            logger.error("Failed to get weekly adherence for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get low stock medicines
    get("/lowstock") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

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
            val lowStockMedicines = redisService.getLowStockMedicines(username, threshold).bind()
            logger.debug("Successfully retrieved ${lowStockMedicines.size} low stock medicines for user '$username' (threshold: $threshold)")
            call.respond(HttpStatusCode.OK, lowStockMedicines)
        }.onLeft { error ->
            logger.error("Failed to get low stock medicines for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
