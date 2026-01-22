package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdherenceRoutes")

/**
 * Adherence tracking routes
 */
fun Route.adherenceRoutes(storageService: StorageService) {
    // Get weekly adherence
    get("/adherence") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        either {
            val weeklyAdherence = storageService.getWeeklyAdherence(userId).bind()
            logger.debug("Successfully retrieved weekly adherence for user ID: $userId")
            call.respond(HttpStatusCode.OK, weeklyAdherence)
        }.onLeft { error ->
            logger.error("Failed to get weekly adherence for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
