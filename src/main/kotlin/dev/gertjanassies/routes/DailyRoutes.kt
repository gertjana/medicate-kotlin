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

private val logger = LoggerFactory.getLogger("DailyRoutes")


/**
 * Daily schedule routes
 */
fun Route.dailyRoutes(storageService: StorageService) {
    // Get daily schedule
    get("/daily") {
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
            val dailySchedule = storageService.getDailySchedule(username).bind()

            logger.debug("Successfully retrieved daily schedule for user '$username' (ID: $userId) with ${dailySchedule.schedule.size} time slots")
            call.respond(HttpStatusCode.OK, dailySchedule)
        }.onLeft { error ->
            logger.error("Failed to get daily schedule for user '$username' (ID: $userId): ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
