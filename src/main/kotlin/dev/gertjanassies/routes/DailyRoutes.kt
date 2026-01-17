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

private val logger = LoggerFactory.getLogger("DailyRoutes")

/**
 * Helper function to extract username from JWT token
 */
private fun ApplicationCall.getUsername(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString()
}

/**
 * Daily schedule routes
 */
fun Route.dailyRoutes(redisService: RedisService) {
    // Get daily schedule
    get("/daily") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val dailySchedule = redisService.getDailySchedule(username).bind()

            logger.debug("Successfully retrieved daily schedule for user '$username' with ${dailySchedule.schedule.size} time slots")
            call.respond(HttpStatusCode.OK, dailySchedule)
        }.onLeft { error ->
            logger.error("Failed to get daily schedule for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
