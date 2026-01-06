package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Dosage history routes
 */
fun Route.dosageHistoryRoutes(redisService: RedisService) {
    // Get all dosage histories
    get("/history") {
        either {
            val histories = redisService.getAllDosageHistories().bind()
            call.respond(HttpStatusCode.OK, histories)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }
}
