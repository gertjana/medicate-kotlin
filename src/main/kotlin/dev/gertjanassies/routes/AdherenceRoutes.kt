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

        // Get user to obtain username
        val userResult = storageService.getUserById(userId)
        if (userResult.isLeft()) {
            logger.error("User with ID '$userId' not found")
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid user"))
            return@get
        }
        val username = userResult.getOrNull()!!.username

        either {
            val weeklyAdherence = storageService.getWeeklyAdherence(username).bind()
            logger.debug("Successfully retrieved weekly adherence for user '$username' (ID: $userId)")
            call.respond(HttpStatusCode.OK, weeklyAdherence)
        }.onLeft { error ->
            logger.error("Failed to get weekly adherence for user '$username' (ID: $userId): ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
