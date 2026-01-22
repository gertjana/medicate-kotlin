package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.util.*

private val logger = LoggerFactory.getLogger("DosageHistoryRoutes")


/**
 * Dosage history routes
 */
fun Route.dosageHistoryRoutes(storageService: StorageService) {
    // Get all dosage histories
    get("/history") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        either {
            val histories = storageService.getAllDosageHistories(userId).bind()
            logger.debug("Successfully retrieved ${histories.size} dosage histories for user ID: $userId")
            call.respond(HttpStatusCode.OK, histories)
        }.onLeft { error ->
            logger.error("Failed to get dosage histories for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Delete a dosage history (undo dose)
    delete("/history/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
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
            storageService.deleteDosageHistory(userId, dosageHistoryId).bind()
            logger.debug("Successfully deleted dosage history '$id' for user ID: $userId")
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
            logger.error("Failed to delete dosage history '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }
}
