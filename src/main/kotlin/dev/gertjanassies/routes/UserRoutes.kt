package dev.gertjanassies.routes

import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.response.AuthResponse
import dev.gertjanassies.model.response.toResponse
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserRoutes")

fun Route.userRoutes(redisService: RedisService, jwtService: JwtService) {
    route("/user") {
        /**
         * POST /api/user/register
         * Register a new user with username, email and password
         */
        post("/register") {
            val request = call.receive<UserRequest>()

            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username cannot be empty"))
                return@post
            }

            if (request.email.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email cannot be empty"))
                return@post
            }

            if (request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password cannot be empty"))
                return@post
            }

            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password must be at least 6 characters"))
                return@post
            }

            val result = redisService.registerUser(request.username, request.email, request.password)

            val left = result.leftOrNull()
            if (left != null) {
                logger.error("Failed to register user '${request.username}': ${left.message}")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to left.message))
                return@post
            }

            val user = result.getOrNull()!!

            // Generate JWT tokens for newly registered user
            val accessToken = jwtService.generateAccessToken(user.username)
            val refreshToken = jwtService.generateRefreshToken(user.username)

            logger.debug("Successfully registered user '${request.username}' and generated JWT tokens")
            call.respond(
                HttpStatusCode.Created,
                AuthResponse(user = user.toResponse(), token = accessToken, refreshToken = refreshToken)
            )
        }

        /**
         * POST /api/user/login
         * Login user by verifying username and password
         */
        post("/login") {
            val request = call.receive<UserRequest>()

            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username cannot be empty"))
                return@post
            }

            if (request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password cannot be empty"))
                return@post
            }

            val loginResult = redisService.loginUser(request.username, request.password)

            val leftLogin = loginResult.leftOrNull()
            if (leftLogin != null) {
                logger.error("Failed login attempt for user '${request.username}': ${leftLogin.message}")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                return@post
            }

            val user = loginResult.getOrNull()!!

            // Generate JWT tokens for logged in user
            val accessToken = jwtService.generateAccessToken(user.username)
            val refreshToken = jwtService.generateRefreshToken(user.username)

            logger.debug("Successfully logged in user '${request.username}' and generated JWT tokens")
            call.respond(
                HttpStatusCode.OK,
                AuthResponse(user = user.toResponse(), token = accessToken, refreshToken = refreshToken)
            )
        }

        /**
         * PUT /api/user/password
         * Update user password
         */
        put("/password") {
            val request = call.receive<UserRequest>()

            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username cannot be empty"))
                return@put
            }

            if (request.password.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "New password cannot be empty"))
                return@put
            }

            if (request.password.length < 6) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Password must be at least 6 characters"))
                return@put
            }

            val result = redisService.updatePassword(request.username, request.password)

            result.fold(
                { error ->
                    logger.error("Failed to update password for user '${request.username}': ${error.message}")
                    when (error) {
                        is dev.gertjanassies.service.RedisError.NotFound ->
                            call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                        else ->
                            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
                    }
                },
                {
                    logger.debug("Successfully updated password for user '${request.username}'")
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully"))
                }
            )
        }
    }
}
