package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.request.ScheduleRequest
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ScheduleRoutes")

/**
 * Schedule routes
 */
fun Route.scheduleRoutes(storageService: StorageService) {
    // Get all schedules
    get("/schedule") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        either {
            val schedules = storageService.getAllSchedules(userId).bind()
            logger.debug("Successfully retrieved ${schedules.size} schedules for user ID: $userId")
            call.respond(HttpStatusCode.OK, schedules)
        }.onLeft { error ->
            logger.error("Failed to get all schedules for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get schedule by id
    get("/schedule/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@get
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@get
        }

        either {
            val schedule = storageService.getSchedule(userId, id).bind()
            logger.debug("Successfully retrieved schedule '$id' for user ID: $userId")
            call.respond(HttpStatusCode.OK, schedule)
        }.onLeft { error ->
            logger.error("Failed to get schedule '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    // Create new schedule
    post("/schedule") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@post
        }

        val request = call.receive<ScheduleRequest>()

        either {
            val created = storageService.createSchedule(userId, request).bind()
            logger.debug("Successfully created schedule '${created.id}' for medicine '${created.medicineId}' for user ID: $userId")
            call.respond(HttpStatusCode.Created, created)
        }.onLeft { error ->
            logger.error("Failed to create schedule for user ID '$userId': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Update existing schedule
    put("/schedule/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@put
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@put
        }

        val schedule = call.receive<Schedule>()

        either {
            val updated = storageService.updateSchedule(userId, id, schedule).bind()
            logger.debug("Successfully updated schedule '$id' for user ID: $userId")
            call.respond(HttpStatusCode.OK, updated)
        }.onLeft { error ->
            logger.error("Failed to update schedule '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }

    // Delete schedule
    delete("/schedule/{id}") {
        val userId = call.getUserId() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User ID required"))
            return@delete
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@delete
        }

        either {
            storageService.deleteSchedule(userId, id).bind()
            logger.debug("Successfully deleted schedule '$id' for user ID: $userId")
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
            logger.error("Failed to delete schedule '$id' for user ID '$userId': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }
}
