package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

/**
 * Helper function to extract username from request header
 */
private suspend fun ApplicationCall.getUsername(): String? {
    return request.header("X-Username")
}

/**
 * Dosage history routes
 */
fun Route.dosageHistoryRoutes(redisService: RedisService) {
    // Get all dosage histories
    get("/history") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val histories = redisService.getAllDosageHistories(username).bind()
            call.respond(HttpStatusCode.OK, histories)
        }.onLeft { error ->
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Delete a dosage history (undo dose)
    delete("/history/{id}") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@delete
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@delete
        }

        val dosageHistoryId = try {
            UUID.fromString(id)
        } catch (e: IllegalArgumentException) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid UUID format"))
            return@delete
        }

        either {
            redisService.deleteDosageHistory(username, dosageHistoryId).bind()
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }
}
