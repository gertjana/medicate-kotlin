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
 * Helper function to extract username from JWT token
 */
private fun ApplicationCall.getUsername(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString()
}

/**
 * Adherence and analytics routes
 */
fun Route.adherenceRoutes(storageService: StorageService) {
    // Get weekly adherence
    get("/adherence") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val weeklyAdherence = storageService.getWeeklyAdherence(username).bind()
            logger.debug("Successfully retrieved weekly adherence for user '$username'")
            call.respond(HttpStatusCode.OK, weeklyAdherence)
        }.onLeft { error ->
            logger.error("Failed to get weekly adherence for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
