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
            val request = PasswordResetRequest(username)
            val user = User(username, email = email, passwordHash = "hashedpassword")
            val emailId = "email-id-123"

            coEvery { mockRedisService.getUser(username) } returns user.right()
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
                body["message"] shouldContain "Password reset email sent"

                coVerify { mockRedisService.getUser(username) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }

        test("should return 400 when username is blank") {
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
                body["error"] shouldContain "Username cannot be empty"

                coVerify(exactly = 0) { mockRedisService.getUser(any()) }
                coVerify(exactly = 0) { mockEmailService.resetPassword(any()) }
            }
        }

        test("should return 404 when user not found") {
            val username = "nonexistent"
            val request = PasswordResetRequest(username)

            coEvery { mockRedisService.getUser(username) } returns RedisError.NotFound("User not found").left()

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

                response.status shouldBe HttpStatusCode.NotFound
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "User not found"

                coVerify { mockRedisService.getUser(username) }
                coVerify(exactly = 0) { mockEmailService.resetPassword(any()) }
            }
        }

        test("should return 500 when Redis operation fails") {
            val username = "testuser"
            val request = PasswordResetRequest(username)

            coEvery { mockRedisService.getUser(username) } returns RedisError.OperationError("Redis connection failed").left()

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

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Redis connection failed"

                coVerify { mockRedisService.getUser(username) }
                coVerify(exactly = 0) { mockEmailService.resetPassword(any()) }
            }
        }

        test("should return 400 when email is invalid") {
            val username = "testuser"
            val email = "invalid-email"
            val request = PasswordResetRequest(username)
            val user = User(username, email = email, passwordHash = "hashedpassword")

            coEvery { mockRedisService.getUser(username) } returns user.right()
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

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Invalid email address"

                coVerify { mockRedisService.getUser(username) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }

        test("should return 500 when email send fails") {
            val username = "testuser"
            val email = "testuser@example.com"
            val request = PasswordResetRequest(username)
            val user = User(username, email = email, passwordHash = "hashedpassword")

            coEvery { mockRedisService.getUser(username) } returns user.right()
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

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Failed to send email"

                coVerify { mockRedisService.getUser(username) }
                coVerify { mockEmailService.resetPassword(user) }
            }
        }

        test("should return 500 when token generation fails") {
            val username = "testuser"
            val email = "testuser@example.com"
            val request = PasswordResetRequest(username)
            val user = User(username, email = email, passwordHash = "hashedpassword")

            coEvery { mockRedisService.getUser(username) } returns user.right()
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

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldContain "Failed to generate token"

                coVerify { mockRedisService.getUser(username) }
                coVerify { mockEmailService.resetPassword(user) }
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
