package dev.gertjanassies.routes

import dev.gertjanassies.model.request.PasswordResetRequest
import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.request.VerifyResetTokenRequest
import dev.gertjanassies.model.response.toResponse
import dev.gertjanassies.service.EmailError
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AuthRoutes")

fun Route.authRoutes(storageService: StorageService, emailService: EmailService, jwtService: JwtService) {
    route("/auth") {
        /**
         * POST /api/auth/refresh
         * Refresh access token using refresh token from HttpOnly cookie
         */
        post("/refresh") {
            // Get refresh token from HttpOnly cookie
            val refreshToken = call.request.cookies["refresh_token"]

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

            // Get user to obtain userId for new token
            val userResult = storageService.getUser(username)
            if (userResult.isLeft()) {
                logger.error("User '$username' from refresh token not found in database")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "User not found"))
                return@post
            }

            val user = userResult.getOrNull()!!

            // Check if user is admin
            val isAdmin = storageService.isUserAdmin(user.id.toString()).getOrNull() ?: false

            // Generate new access token with userId
            val newAccessToken = jwtService.generateAccessToken(user.username, user.id.toString(), isAdmin)

            logger.debug("Successfully refreshed access token for user '$username'")
            call.respond(
                HttpStatusCode.OK,
                mapOf("token" to newAccessToken)
            )
        }

        /**
         * POST /api/auth/logout
         * Logout user by clearing the refresh token cookie
         */
        post("/logout") {
            // Clear the refresh token cookie
            call.response.cookies.append(
                io.ktor.http.Cookie(
                    name = "refresh_token",
                    value = "",
                    maxAge = 0, // Expire immediately
                    httpOnly = true,
                    secure = false,
                    path = "/"
                )
            )

            logger.debug("User logged out, refresh token cookie cleared")
            call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out successfully"))
        }

        /**
         * POST /api/auth/resetPassword
         * Request a password reset for a user by email address
         * Note: Always returns success to prevent email enumeration attacks
         */
        post("/resetPassword") {
            val request = call.receive<PasswordResetRequest>()

            if (request.email.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email cannot be empty"))
                return@post
            }

            // Get user by email
            val userResult = storageService.getUserByEmail(request.email)
            val userError = userResult.leftOrNull()

            if (userError != null) {
                // Don't reveal whether the email exists or not - still log for debugging
                logger.info("Password reset requested for non-existent email: '${request.email}'")
                // Return success message anyway to prevent email enumeration
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "If an account exists with that email, you will receive a password reset link.",
                    "emailId" to "no-email-sent"
                ))
                return@post
            }

            val user = userResult.getOrNull()!!

            // Get locale from request header (sent by frontend)
            val locale = call.request.headers["Accept-Language"]?.take(2) ?: "en"

            // Send reset password email
            val emailResult = emailService.resetPassword(user, locale)
            val emailError = emailResult.leftOrNull()
            if (emailError != null) {
                logger.error("Failed to send password reset email (email: '${request.email}'): ${emailError.message}")
                // Even if email sending fails, return success message to prevent enumeration
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "If an account exists with that email, you will receive a password reset link.",
                    "emailId" to "email-send-failed"
                ))
                return@post
            }

            val emailId = emailResult.getOrNull()!!
            logger.debug("Successfully sent password reset email for email '${request.email}', emailId: $emailId")
            call.respond(HttpStatusCode.OK, mapOf(
                "message" to "If an account exists with that email, you will receive a password reset link.",
                "emailId" to emailId
            ))
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

            val result = storageService.verifyPasswordResetToken(request.token)
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
         * POST /api/auth/activateAccount
         * Verify activation token and activate user account
         * Body: { "token": "verification-token" }
         */
        post("/activateAccount") {
            val request = call.receive<VerifyResetTokenRequest>() // Reuse same request model

            if (request.token.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Token cannot be empty"))
                return@post
            }

            // Verify activation token and get user ID
            val tokenResult = storageService.verifyActivationToken(request.token)
            val tokenError = tokenResult.leftOrNull()
            if (tokenError != null) {
                logger.error("Failed to verify activation token: ${tokenError.message}")
                when (tokenError) {
                    is RedisError.NotFound -> {
                        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Invalid or expired activation token"))
                    }
                    else -> {
                        call.respond(HttpStatusCode.InternalServerError, mapOf("error" to tokenError.message))
                    }
                }
                return@post
            }

            val userId = tokenResult.getOrNull()!!

            // Activate the user account
            val activationResult = storageService.activateUser(userId)
            val activationError = activationResult.leftOrNull()
            if (activationError != null) {
                logger.error("Failed to activate user account for user ID '$userId': ${activationError.message}")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to activate account"))
                return@post
            }

            val user = activationResult.getOrNull()!!

            // Check if user is admin
            val isAdmin = storageService.isUserAdmin(user.id.toString()).getOrNull() ?: false

            // Generate access and refresh tokens for the newly activated user
            val accessToken = jwtService.generateAccessToken(user.username, user.id.toString(), isAdmin)
            val refreshToken = jwtService.generateRefreshToken(user.username, user.id.toString(), isAdmin)

            // Set refresh token as HttpOnly cookie
            call.response.cookies.append(
                io.ktor.http.Cookie(
                    name = "refresh_token",
                    value = refreshToken,
                    maxAge = 30 * 24 * 3600, // 30 days
                    httpOnly = true,
                    secure = false,
                    path = "/"
                )
            )

            logger.debug("Successfully activated account for user '${user.username}' (ID: $userId)")
            call.respond(
                HttpStatusCode.OK,
                dev.gertjanassies.model.response.ActivationResponse(
                    message = "Account activated successfully",
                    user = user.toResponse(isAdmin),
                    token = accessToken
                )
            )
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

            val result = storageService.updatePassword(request.username, request.password)

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
