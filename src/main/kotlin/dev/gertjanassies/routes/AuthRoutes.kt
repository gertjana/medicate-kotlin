package dev.gertjanassies.routes

import dev.gertjanassies.model.request.PasswordResetRequest
import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.request.VerifyResetTokenRequest
import dev.gertjanassies.service.EmailError
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AuthRoutes")

fun Route.authRoutes(redisService: RedisService, emailService: EmailService, jwtService: JwtService) {
    route("/auth") {
        /**
         * POST /api/auth/refresh
         * Refresh access token using a valid refresh token
         */
        post("/refresh") {
            val request = call.receive<Map<String, String>>()
            val refreshToken = request["refreshToken"]

            if (refreshToken.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Refresh token is required"))
                return@post
            }

            // Validate refresh token and extract username
            val username = jwtService.validateRefreshToken(refreshToken)
            if (username == null) {
                logger.debug("Invalid or expired refresh token")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired refresh token"))
                return@post
            }

            // Generate new access token
            val newAccessToken = jwtService.generateAccessToken(username)

            logger.debug("Successfully refreshed access token for user '$username'")
            call.respond(
                HttpStatusCode.OK,
                mapOf(
                    "token" to newAccessToken,
                    "refreshToken" to refreshToken  // Return same refresh token
                )
            )
        }

        /**
         * POST /api/auth/resetPassword
         * Request a password reset for a user by username
         */
        post("/resetPassword") {
            val request = call.receive<PasswordResetRequest>()

            if (request.username.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Username cannot be empty"))
                return@post
            }

            // Get user by username
            val userResult = redisService.getUser(request.username)
            val userError = userResult.leftOrNull()
            if (userError != null) {
                logger.error("Failed to get user for password reset (username: '${request.username}'): ${userError.message}")
                when (userError) {
                    is RedisError.NotFound -> {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "User not found"))
                    }
                    else -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to userError.message))
                    }
                }
                return@post
            }

            val user = userResult.getOrNull()!!

            // Send reset password email
            val emailResult = emailService.resetPassword(user)
            val emailError = emailResult.leftOrNull()
            if (emailError != null) {
                logger.error("Failed to send password reset email (username: '${request.username}'): ${emailError.message}")
                when (emailError) {
                    is EmailError.InvalidEmail -> {
                        call.respond(HttpStatusCode.BadRequest, mapOf("error" to emailError.message))
                    }
                    is EmailError.SendFailed -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to emailError.message))
                    }
                    is EmailError.TokenGenerationFailed -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to emailError.message))
                    }
                }
                return@post
            }

            val emailId = emailResult.getOrNull()!!
            logger.debug("Successfully sent password reset email for user '${request.username}', emailId: $emailId")
            call.respond(HttpStatusCode.OK, mapOf("message" to "Password reset email sent", "emailId" to emailId))
        }

        /**
         * POST /api/auth/verifyResetToken
         * Verify a password reset token and return the associated username
         */
        post("/verifyResetToken") {
            val request = call.receive<VerifyResetTokenRequest>()

            if (request.token.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Token cannot be empty"))
                return@post
            }

            val result = redisService.verifyPasswordResetToken(request.token)
            val error = result.leftOrNull()
            if (error != null) {
                logger.error("Failed to verify password reset token: ${error.message}")
                when (error) {
                    is RedisError.NotFound -> {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to error.message))
                    }
                    else -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to error.message))
                    }
                }
                return@post
            }

            val username = result.getOrNull()!!
            logger.debug("Successfully verified password reset token for user '$username'")
            call.respond(HttpStatusCode.OK, mapOf("username" to username))
        }

        /**
         * PUT /api/auth/updatePassword
         * Update user password (public endpoint for password reset flow)
         */
        put("/updatePassword") {
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
                        is RedisError.NotFound ->
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
