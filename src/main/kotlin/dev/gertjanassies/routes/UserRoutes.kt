package dev.gertjanassies.routes

import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(redisService: RedisService) {
    route("/user") {
        /**
         * POST /api/user/register
         * Register a new user
         */
        post("/register") {
            val request = call.receive<UserRequest>()

            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username cannot be empty"))
                return@post
            }

            redisService.registerUser(request.username).fold(
                { error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error.message)) },
                { user -> call.respond(HttpStatusCode.Created, user) }
            )
        }

        /**
         * POST /api/user/login
         * Login user (for now just checks if user exists)
         */
        post("/login") {
            val request = call.receive<UserRequest>()

            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username cannot be empty"))
                return@post
            }

            redisService.loginUser(request.username).fold(
                { _ -> call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials")) },
                { user -> call.respond(HttpStatusCode.OK, user) }
            )
        }
    }
}
