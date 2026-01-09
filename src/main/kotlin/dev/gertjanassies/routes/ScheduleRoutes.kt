package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.request.ScheduleRequest
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
            call.respond(HttpStatusCode.OK, schedules)
        }.onLeft { error ->
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
            call.respond(HttpStatusCode.OK, schedule)
        }.onLeft { error ->
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
            call.respond(HttpStatusCode.Created, created)
        }.onLeft { error ->
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
            call.respond(HttpStatusCode.OK, updated)
        }.onLeft { error ->
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
