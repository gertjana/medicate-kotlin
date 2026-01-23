package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.PasswordResetRequest
import dev.gertjanassies.model.request.VerifyResetTokenRequest
import dev.gertjanassies.service.EmailError
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.*
import io.mockk.*

class AuthRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    lateinit var mockEmailService: EmailService
    lateinit var mockJwtService: JwtService

    beforeEach {
        mockRedisService = mockk()
        mockEmailService = mockk()
        mockJwtService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("POST /auth/resetPassword") {
        test("should send reset password email successfully") {
            val username = "testuser"
            val email = "testuser@example.com"
            val request = PasswordResetRequest(email)
            val user = User(id = java.util.UUID.randomUUID(), username = username, email = email, passwordHash = "hashedpassword")
            val emailId = "email-id-123"

            coEvery { mockRedisService.getUserByEmail(email) } returns user.right()
            coEvery { mockEmailService.resetPassword(user) } returns emailId.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body shouldContainKey "message"
                body shouldContainKey "emailId"
                body["emailId"] shouldBe emailId
                body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."

                coVerify { mockRedisService.getUserByEmail(email) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }

        test("should return 400 when email is blank") {
            val request = PasswordResetRequest("")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Email cannot be empty"

                coVerify(exactly = 0) { mockRedisService.getUserByEmail(any()) }
                coVerify(exactly = 0) { mockEmailService.resetPassword(any()) }
            }
        }

        test("should return OK with generic message when user not found by email") {
            val email = "nonexistent@example.com"
            val request = PasswordResetRequest(email)

            coEvery { mockRedisService.getUserByEmail(email) } returns RedisError.NotFound("No user found with email: $email").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // Should return OK to avoid leaking information about user existence
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                body["emailId"] shouldBe "no-email-sent"

                coVerify { mockRedisService.getUserByEmail(email) }
                coVerify(exactly = 0) { mockEmailService.resetPassword(any()) }
            }
        }

        test("should return OK with generic message when Redis operation fails") {
            val email = "testuser@example.com"
            val request = PasswordResetRequest(email)

            coEvery { mockRedisService.getUserByEmail(email) } returns RedisError.OperationError("Redis connection failed").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // Should return OK to avoid leaking internal errors
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                body["emailId"] shouldBe "no-email-sent"

                coVerify { mockRedisService.getUserByEmail(email) }
                coVerify(exactly = 0) { mockEmailService.resetPassword(any()) }
            }
        }

        test("should return 400 when email is invalid") {
            val username = "testuser"
            val email = "invalid-email"
            val request = PasswordResetRequest(email)
            val user = User(id = java.util.UUID.randomUUID(), username = username, email = email, passwordHash = "hashedpassword")

            coEvery { mockRedisService.getUserByEmail(email) } returns user.right()
            coEvery { mockEmailService.resetPassword(user) } returns EmailError.InvalidEmail(email).left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                body["emailId"] shouldBe "email-send-failed"

                coVerify { mockRedisService.getUserByEmail(email) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }

        test("should return 500 when email send fails") {
            val username = "testuser"
            val email = "testuser@example.com"
            val request = PasswordResetRequest(email)
            val user = User(id = java.util.UUID.randomUUID(), username = username, email = email, passwordHash = "hashedpassword")

            coEvery { mockRedisService.getUserByEmail(email) } returns user.right()
            coEvery { mockEmailService.resetPassword(user) } returns EmailError.SendFailed("SMTP error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                body["emailId"] shouldBe "email-send-failed"

                coVerify { mockRedisService.getUserByEmail(email) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }

        test("should return 500 when token generation fails") {
            val username = "testuser"
            val email = "testuser@example.com"
            val request = PasswordResetRequest(email)
            val user = User(id = java.util.UUID.randomUUID(), username = username, email = email, passwordHash = "hashedpassword")

            coEvery { mockRedisService.getUserByEmail(email) } returns user.right()
            coEvery { mockEmailService.resetPassword(user) } returns EmailError.TokenGenerationFailed("Random generator failed").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                body["emailId"] shouldBe "email-send-failed"

                coVerify { mockRedisService.getUserByEmail(email) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }
    }

    context("POST /auth/refresh") {
        test("should refresh access token with valid refresh token") {
            val refreshToken = "valid-refresh-token"
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = "hash")
            val newAccessToken = "new-access-token"

            every { mockJwtService.validateRefreshToken(refreshToken) } returns username
            coEvery { mockRedisService.getUser(username) } returns user.right()
            every { mockJwtService.generateAccessToken(username, userId.toString()) } returns newAccessToken

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/refresh") {
                    cookie("refresh_token", refreshToken)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body shouldContainKey "token"
                body["token"] shouldBe newAccessToken
                // Refresh token should not be in response (it's in HttpOnly cookie)
                (body.containsKey("refreshToken")) shouldBe false

                verify { mockJwtService.validateRefreshToken(refreshToken) }
                coVerify { mockRedisService.getUser(username) }
                verify { mockJwtService.generateAccessToken(username, userId.toString()) }
            }
        }

        test("should return 401 when refresh token is invalid, expired, or wrong type") {
            // Test various scenarios where validateRefreshToken returns null
            // (invalid token, expired token, or access token with wrong type claim)
            val invalidToken = "invalid-or-expired-or-access-token"

            every { mockJwtService.validateRefreshToken(invalidToken) } returns null

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/refresh") {
                    cookie("refresh_token", invalidToken)
                }

                response.status shouldBe HttpStatusCode.Unauthorized
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Invalid or expired refresh token"

                verify { mockJwtService.validateRefreshToken(invalidToken) }
                coVerify(exactly = 0) { mockRedisService.getUser(any()) }
            }
        }

        test("should return 400 when refresh token is missing") {

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/refresh") {
                    // No cookie sent
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Refresh token is required"

                verify(exactly = 0) { mockJwtService.validateRefreshToken(any()) }
                coVerify(exactly = 0) { mockRedisService.getUser(any()) }
            }
        }

        test("should return 400 when refresh token is blank") {

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/refresh") {
                    cookie("refresh_token", "")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Refresh token is required"

                verify(exactly = 0) { mockJwtService.validateRefreshToken(any()) }
                coVerify(exactly = 0) { mockRedisService.getUser(any()) }
            }
        }
    }

    context("POST /auth/verifyResetToken") {
        test("should verify token and return username successfully") {
            val token = "valid-token-123"
            val username = "testuser"
            val request = VerifyResetTokenRequest(token)

            coEvery { mockRedisService.verifyPasswordResetToken(token) } returns username.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/verifyResetToken") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["username"] shouldBe username

                coVerify { mockRedisService.verifyPasswordResetToken(token) }
            }
        }

        test("should return 400 when token is blank") {
            val request = VerifyResetTokenRequest("")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/verifyResetToken") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Token cannot be empty"

                coVerify(exactly = 0) { mockRedisService.verifyPasswordResetToken(any()) }
            }
        }

        test("should return 404 when token is not found or expired") {
            val token = "invalid-or-expired-token"
            val request = VerifyResetTokenRequest(token)

            coEvery { mockRedisService.verifyPasswordResetToken(token) } returns RedisError.NotFound("Invalid or expired password reset token").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/verifyResetToken") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.NotFound
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Invalid or expired"

                coVerify { mockRedisService.verifyPasswordResetToken(token) }
            }
        }

        test("should return 500 when Redis operation fails") {
            val token = "valid-token-123"
            val request = VerifyResetTokenRequest(token)

            coEvery { mockRedisService.verifyPasswordResetToken(token) } returns RedisError.OperationError("Redis connection failed").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/verifyResetToken") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Redis connection failed"

                coVerify { mockRedisService.verifyPasswordResetToken(token) }
            }
        }

        test("should delete token after successful verification") {
            val token = "valid-token-123"
            val username = "testuser"
            val request = VerifyResetTokenRequest(token)

            coEvery { mockRedisService.verifyPasswordResetToken(token) } returns username.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { authRoutes(mockRedisService, mockEmailService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/auth/verifyResetToken") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK

                // Verify that verifyPasswordResetToken was called (which internally deletes the token)
                coVerify(exactly = 1) { mockRedisService.verifyPasswordResetToken(token) }
            }
        }
    }
})
