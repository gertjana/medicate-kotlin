package dev.gertjanassies.routes

import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.response.AuthResponse
import dev.gertjanassies.model.response.toResponse
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("UserRoutes")

fun Route.userRoutes(storageService: StorageService, jwtService: JwtService, emailService: EmailService) {
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

            val result = storageService.registerUser(request.username, request.email, request.password)

            val left = result.leftOrNull()
            if (left != null) {
                logger.error("Failed to register user '${request.username}': ${left.message}")
                // Don't reveal specific details about what exists (username or email)
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Registration failed. Please try a different username or email."))
                return@post
            }

            val user = result.getOrNull()!!

            // Send verification email (user is created but inactive)
            val emailResult = emailService.sendVerificationEmail(user)
            val emailError = emailResult.leftOrNull()
            if (emailError != null) {
                logger.error("Failed to send verification email to ${user.email}: ${emailError.message}")
                // User is already created but email failed - log them in anyway for now
                // TODO: Consider if we should delete the user or mark for cleanup
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Registration successful but failed to send verification email. Please contact support.")
                )
                return@post
            }

            logger.debug("Successfully registered user '${request.username}' and sent verification email")
            call.respond(
                HttpStatusCode.Created,
                mapOf(
                    "message" to "Registration successful! Please check your email to verify your account.",
                    "email" to user.email
                )
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

            val loginResult = storageService.loginUser(request.username, request.password)

            val leftLogin = loginResult.leftOrNull()
            if (leftLogin != null) {
                logger.error("Failed login attempt for user '${request.username}': ${leftLogin.message}")
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid credentials"))
                return@post
            }

            val user = loginResult.getOrNull()!!

            // Check if user account is active (email verified)
            if (!user.isActive) {
                logger.warn("Login attempt for inactive account: ${request.username}")
                call.respond(
                    HttpStatusCode.Forbidden,
                    mapOf("error" to "Account not verified. Please check your email for the verification link.")
                )
                return@post
            }

            // Generate JWT tokens for logged in user
            val accessToken = jwtService.generateAccessToken(user.username, user.id.toString())
            val refreshToken = jwtService.generateRefreshToken(user.username, user.id.toString())

            // Set refresh token as HttpOnly cookie
            call.response.cookies.append(
                io.ktor.http.Cookie(
                    name = "refresh_token",
                    value = refreshToken,
                    maxAge = 30 * 24 * 60 * 60, // 30 days in seconds
                    httpOnly = true,
                    secure = false, // Set to true in production with HTTPS
                    path = "/",
                    extensions = mapOf("SameSite" to "Strict")
                )
            )

            logger.debug("Successfully logged in user '${request.username}' and generated JWT tokens")
            call.respond(
                HttpStatusCode.OK,
                AuthResponse(user = user.toResponse(), token = accessToken, refreshToken = "") // Don't send refresh token in response
            )
        }

        /**
         * GET /api/user/verify-email?token=xxx
         * Verify email address and activate user account
         */
        get("/verify-email") {
            val token = call.request.queryParameters["token"]

            if (token.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Verification token is required"))
                return@get
            }

            // Verify the token and get user ID
            val verifyResult = storageService.verifyActivationToken(token)
            val verifyError = verifyResult.leftOrNull()
            if (verifyError != null) {
                logger.error("Failed to verify activation token: ${verifyError.message}")
                call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid or expired verification token")
                )
                return@get
            }

            val userId = verifyResult.getOrNull()!!

            // Activate the user account
            val activateResult = storageService.activateUser(userId)
            val activateError = activateResult.leftOrNull()
            if (activateError != null) {
                logger.error("Failed to activate user ID '$userId': ${activateError.message}")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to "Failed to activate account. Please try again or contact support.")
                )
                return@get
            }

            val user = activateResult.getOrNull()!!

            // Generate JWT tokens for the newly activated user
            val accessToken = jwtService.generateAccessToken(user.username, user.id.toString())
            val refreshToken = jwtService.generateRefreshToken(user.username, user.id.toString())

            // Set refresh token as HttpOnly cookie
            call.response.cookies.append(
                io.ktor.http.Cookie(
                    name = "refresh_token",
                    value = refreshToken,
                    maxAge = 30 * 24 * 60 * 60, // 30 days in seconds
                    httpOnly = true,
                    secure = false, // Set to true in production with HTTPS
                    path = "/",
                    extensions = mapOf("SameSite" to "Strict")
                )
            )

            logger.debug("Successfully verified and activated user '${user.username}'")
            call.respond(
                HttpStatusCode.OK,
                AuthResponse(user = user.toResponse(), token = accessToken, refreshToken = "")
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

            val result = storageService.updatePassword(request.username, request.password)

            result.fold(
                { error ->
                    logger.error("Failed to update password for user '${request.username}': ${error.message}")
                    // Don't reveal whether user exists or not
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully"))
                },
                {
                    logger.debug("Successfully updated password for user '${request.username}'")
                    call.respond(HttpStatusCode.OK, mapOf("message" to "Password updated successfully"))
                }
            )
        }
    }
}

/**
 * Protected user routes (require JWT authentication)
 */
fun Route.protectedUserRoutes(storageService: StorageService) {
    route("/user") {
        /**
         * GET /api/user/profile
         * Get current user's profile
         */
        get("/profile") {
            val userId = call.getUserId() ?: run {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))
                return@get
            }

            val result = storageService.getUserById(userId)

            result.fold(
                { error ->
                    logger.error("Failed to get profile for user ID '$userId': ${error.message}")
                    // Generic error - don't reveal details about user existence
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to retrieve profile"))
                },
                { user ->
                    logger.debug("Successfully retrieved profile for user ID '$userId' (username: '${user.username}')")
                    call.respond(HttpStatusCode.OK, user.toResponse())
                }
            )
        }

        /**
         * PUT /api/user/profile
         * Update current user's profile (email, firstName, lastName)
         */
        put("/profile") {
            val userId = call.getUserId() ?: run {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))
                return@put
            }

            val request = call.receive<dev.gertjanassies.model.request.UpdateProfileRequest>()

            if (request.email.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Email cannot be empty"))
                return@put
            }

            if (request.firstName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "First name cannot be empty"))
                return@put
            }

            if (request.lastName.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Last name cannot be empty"))
                return@put
            }

            // Get user to obtain username for updateProfile (still needs username parameter)
            val userResult = storageService.getUserById(userId)
            if (userResult.isLeft()) {
                logger.error("Failed to get user by ID '$userId' for profile update")
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update profile"))
                return@put
            }
            val username = userResult.getOrNull()!!.username

            val result = storageService.updateProfile(username, request.email, request.firstName, request.lastName)

            result.fold(
                { error ->
                    logger.error("Failed to update profile for user '$username': ${error.message}")
                    // Generic error message - don't reveal specific details
                    call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Failed to update profile. Email may already be in use."))
                },
                { user ->
                    logger.debug("Successfully updated profile for user '$username'")
                    call.respond(HttpStatusCode.OK, user.toResponse())
                }
            )
        }
    }
}
