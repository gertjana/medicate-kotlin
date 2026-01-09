package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Helper function to extract username from request header
 */
private suspend fun ApplicationCall.getUsername(): String? {
    return request.header("X-Username")
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
            call.respond(HttpStatusCode.OK, dailySchedule)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
