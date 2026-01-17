package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.request.ScheduleRequest
import dev.gertjanassies.service.RedisService
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
 * Helper function to extract username from JWT token
 */
private fun ApplicationCall.getUsername(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString()
}

/**
 * Schedule routes
 */
fun Route.scheduleRoutes(redisService: RedisService) {
    // Get all schedules
    get("/schedule") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        either {
            val schedules = redisService.getAllSchedules(username).bind()
            logger.debug("Successfully retrieved ${schedules.size} schedules for user '$username'")
            call.respond(HttpStatusCode.OK, schedules)
        }.onLeft { error ->
            logger.error("Failed to get all schedules for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Get schedule by id
    get("/schedule/{id}") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@get
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@get
        }

        either {
            val schedule = redisService.getSchedule(username, id).bind()
            logger.debug("Successfully retrieved schedule '$id' for user '$username'")
            call.respond(HttpStatusCode.OK, schedule)
        }.onLeft { error ->
            logger.error("Failed to get schedule '$id' for user '$username': ${error.message}")
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
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@post
        }

        val request = call.receive<ScheduleRequest>()

        either {
            val created = redisService.createSchedule(username, request).bind()
            logger.debug("Successfully created schedule '${created.id}' for medicine '${created.medicineId}' for user '$username'")
            call.respond(HttpStatusCode.Created, created)
        }.onLeft { error ->
            logger.error("Failed to create schedule for user '$username': ${error.message}")
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
        }
    }

    // Update existing schedule
    put("/schedule/{id}") {
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@put
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@put
        }

        val schedule = call.receive<Schedule>()

        either {
            val updated = redisService.updateSchedule(username, id, schedule).bind()
            logger.debug("Successfully updated schedule '$id' for user '$username'")
            call.respond(HttpStatusCode.OK, updated)
        }.onLeft { error ->
            logger.error("Failed to update schedule '$id' for user '$username': ${error.message}")
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
        val username = call.getUsername() ?: run {
            call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Username required"))
            return@delete
        }

        val id = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing id parameter"))
            return@delete
        }

        either {
            redisService.deleteSchedule(username, id).bind()
            logger.debug("Successfully deleted schedule '$id' for user '$username'")
            call.respond(HttpStatusCode.NoContent)
        }.onLeft { error ->
            logger.error("Failed to delete schedule '$id' for user '$username': ${error.message}")
            when (error) {
                is dev.gertjanassies.service.RedisError.NotFound ->
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                else ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
            }
        }
    }
}
