package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Daily schedule routes
 */
fun Route.dailyRoutes(redisService: RedisService) {
    // Get daily schedule
    get("/daily") {
        either {
            val dailySchedule = redisService.getDailySchedule().bind()
            call.respond(HttpStatusCode.OK, dailySchedule)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
