package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.UpdateProfileRequest
import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.response.AuthResponse
import dev.gertjanassies.model.response.UserResponse
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*

class UserRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    lateinit var mockJwtService: JwtService
    lateinit var mockEmailService: EmailService

    beforeEach {
        mockRedisService = mockk()
        mockJwtService = mockk()
        mockEmailService = mockk()

        // Mock JWT token generation with any username, userId, and isAdmin flag
        every { mockJwtService.generateAccessToken(any(), any(), any()) } returns "test-access-token-123"
        every { mockJwtService.generateRefreshToken(any(), any(), any()) } returns "test-refresh-token-456"
    }

    afterEach {
        clearAllMocks()
    }

    context("POST /user/register") {
        test("should register a new user successfully and send activation email") {
            val username = "testuser"
            val email = "testuser@example.com"
            val password = "password123"
            val userId = java.util.UUID.randomUUID()
            val request = UserRequest(username, email, password)
            val user = User(id = userId, username = username, email = email, passwordHash = "hashedpassword", isActive = false)
            coEvery { mockRedisService.registerUser(username, email, password) } returns user.right()
            coEvery { mockEmailService.sendVerificationEmail(any()) } returns "email-id-123".right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Registration successful! Please check your email to verify your account."
                body["email"] shouldBe email

                coVerify { mockRedisService.registerUser(username, email, password) }
                coVerify { mockEmailService.sendVerificationEmail(any()) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "test@example.com", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.registerUser(any(), any(), any()) }
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "test@example.com", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.registerUser(any(), any(), any()) }
            }
        }

        test("should return 400 when password is too short") {
            val request = UserRequest("testuser", "test@example.com", "12345")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.registerUser(any(), any(), any()) }
            }
        }

        test("should return 400 on registration error") {
            val username = "existinguser"
            val email = "existing@example.com"
            val password = "password123"
            val request = UserRequest(username, email, password)
            coEvery { mockRedisService.registerUser(username, email, password) } returns
                RedisError.OperationError("User already exists").left()


            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify { mockRedisService.registerUser(username, email, password) }
            }
        }
    }

    context("POST /user/login") {
        test("should login user successfully and return JWT token") {
            val username = "testuser"
            val password = "password123"
            val userId = java.util.UUID.randomUUID()
            val request = UserRequest(username, "", password)
            val user = User(id = userId, username = username, email = "", passwordHash = "hashedpassword", isActive = true)
            coEvery { mockRedisService.loginUser(username, password) } returns user.right()
            coEvery { mockRedisService.isUserAdmin(userId.toString()) } returns false.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<AuthResponse>()
                body.user.username shouldBe username
                body.token shouldBe "test-access-token-123"
                // Refresh token should not be in response body (empty string)
                body.refreshToken shouldBe ""

                // Verify refresh token cookie was set
                val cookies = response.setCookie()
                val refreshCookie = cookies.find { it.name == "refresh_token" }
                refreshCookie shouldNotBe null
                refreshCookie?.value shouldBe "test-refresh-token-456"
                refreshCookie?.httpOnly shouldBe true

                coVerify { mockRedisService.loginUser(username, password) }
                coVerify { mockRedisService.isUserAdmin(userId.toString()) }
                verify { mockJwtService.generateAccessToken(username, userId.toString(), false) }
                verify { mockJwtService.generateRefreshToken(username, userId.toString()) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.loginUser(any(), any()) }
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.loginUser(any(), any()) }
            }
        }

        test("should return 401 on login error") {
            val username = "nonexistent"
            val password = "wrongpassword"
            val request = UserRequest(username, "", password)
            coEvery { mockRedisService.loginUser(username, password) } returns
                RedisError.NotFound("User not found").left()


            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Unauthorized
                coVerify { mockRedisService.loginUser(username, password) }
            }
        }
    }

    context("PUT /user/password") {
        test("should update password successfully") {
            val username = "testuser"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)

            coEvery { mockRedisService.updatePassword(username, newPassword) } returns Unit.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Password updated successfully"
                coVerify { mockRedisService.updatePassword(username, newPassword) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "", "newpassword123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updatePassword(any(), any()) }
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updatePassword(any(), any()) }
            }
        }

        test("should return 400 when password is too short") {
            val request = UserRequest("testuser", "", "short")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updatePassword(any(), any()) }
            }
        }

        test("should return OK with generic message when user not found") {
            val username = "nonexistent"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)

            coEvery { mockRedisService.updatePassword(username, newPassword) } returns
                RedisError.NotFound("User not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // Should return OK to avoid revealing user existence
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Password updated successfully"

                coVerify { mockRedisService.updatePassword(username, newPassword) }
            }
        }

        test("should return OK with generic message on update error") {
            val username = "testuser"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)

            coEvery { mockRedisService.updatePassword(username, newPassword) } returns
                RedisError.OperationError("Redis connection failed").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // Should return OK to avoid revealing internal errors
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Password updated successfully"

                coVerify { mockRedisService.updatePassword(username, newPassword) }
            }
        }
    }

    context("Authentication Requirements") {
        test("POST /user/register should NOT require authentication (public endpoint)") {
            val request = UserRequest("testuser", "test@example.com", "password123")
            val user = User(id = java.util.UUID.randomUUID(), username = "testuser", email = "test@example.com", passwordHash = "hashedpassword", isActive = false)
            coEvery { mockRedisService.registerUser(any(), any(), any()) } returns user.right()
            coEvery { mockEmailService.sendVerificationEmail(any()) } returns "email-id-123".right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                // Should succeed WITHOUT authentication header
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    // No Authorization header!
                }

                // Should succeed (201 Created) without authentication
                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("POST /user/login should NOT require authentication (public endpoint)") {
            val request = UserRequest("testuser", "", "password123")
            val userId = java.util.UUID.randomUUID()
            val user = User(id = userId, username = "testuser", email = "", passwordHash = "hashedpassword", isActive = true)
            coEvery { mockRedisService.loginUser(any(), any()) } returns user.right()
            coEvery { mockRedisService.isUserAdmin(userId.toString()) } returns false.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                // Should succeed WITHOUT authentication header
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    // No Authorization header!
                }

                // Should succeed (200 OK) without authentication
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("PUT /user/password should work without authentication (currently public for password reset flow)") {
            val request = UserRequest("testuser", "", "newpassword123")
            coEvery { mockRedisService.updatePassword(any(), any()) } returns Unit.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService, mockEmailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                // Can work without authentication (for password reset)
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    // No Authorization header
                }

                // Should succeed without authentication (currently public)
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    context("GET /user/profile") {
        test("should retrieve user profile successfully with valid JWT") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val user = User(
                id = userId,
                username = username,
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                passwordHash = "hashedpassword"
            )

            coEvery { mockRedisService.getUserById(userId.toString()) } returns user.right()
            coEvery { mockRedisService.isUserAdmin(userId.toString()) } returns false.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UserResponse>()
                body.username shouldBe username
                body.email shouldBe "test@example.com"
                body.firstName shouldBe "Test"
                body.lastName shouldBe "User"

                coVerify { mockRedisService.getUserById(userId.toString()) }
                coVerify { mockRedisService.isUserAdmin(userId.toString()) }
            }
        }

        test("should return InternalServerError with generic message when user not found") {
            val username = "nonexistent"
            val userId = java.util.UUID.randomUUID()

            coEvery { mockRedisService.getUserById(userId.toString()) } returns
                RedisError.NotFound("User not found").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Return generic error message without revealing user existence
                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to retrieve profile"

                coVerify { mockRedisService.getUserById(userId.toString()) }
            }
        }

        test("should return 500 on Redis error") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()

            coEvery { mockRedisService.getUserById(userId.toString()) } returns
                RedisError.OperationError("Redis connection failed").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.getUserById(userId.toString()) }
            }
        }
    }

    context("PUT /user/profile") {
        test("should update user profile successfully with valid JWT") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val updateRequest = UpdateProfileRequest(
                email = "updated@example.com",
                firstName = "Updated",
                lastName = "Name"
            )
            val existingUser = User(
                id = userId,
                username = username,
                email = "old@example.com",
                firstName = "Old",
                lastName = "Name",
                passwordHash = "hashedpassword"
            )
            val updatedUser = User(
                id = userId,
                username = username,
                email = updateRequest.email,
                firstName = updateRequest.firstName,
                lastName = updateRequest.lastName,
                passwordHash = "hashedpassword"
            )

            // Mock getUserById first (called to get username)
            coEvery { mockRedisService.getUserById(userId.toString()) } returns existingUser.right()
            // Then mock updateProfile
            coEvery {
                mockRedisService.updateProfile(username, updateRequest.email, updateRequest.firstName, updateRequest.lastName)
            } returns updatedUser.right()
            coEvery { mockRedisService.isUserAdmin(userId.toString()) } returns false.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UserResponse>()
                body.username shouldBe username
                body.email shouldBe updateRequest.email
                body.firstName shouldBe updateRequest.firstName
                body.lastName shouldBe updateRequest.lastName

                coVerify { mockRedisService.getUserById(userId.toString()) }
                coVerify { mockRedisService.updateProfile(username, updateRequest.email, updateRequest.firstName, updateRequest.lastName) }
                coVerify { mockRedisService.isUserAdmin(userId.toString()) }
            }
        }

        test("should return 400 when email is blank") {
            val username = "testuser"
            val updateRequest = UpdateProfileRequest(
                email = "",
                firstName = "Test",
                lastName = "User"
            )

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username)
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updateProfile(any(), any(), any(), any()) }
            }
        }

        test("should return 400 when firstName is blank") {
            val username = "testuser"
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "",
                lastName = "User"
            )

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username)
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updateProfile(any(), any(), any(), any()) }
            }
        }

        test("should return 400 when lastName is blank") {
            val username = "testuser"
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "Test",
                lastName = ""
            )

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username)
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updateProfile(any(), any(), any(), any()) }
            }
        }

        test("should return InternalServerError with generic message when user not found") {
            val username = "nonexistent"
            val userId = java.util.UUID.randomUUID()
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            )

            // Mock getUserById to return NotFound (user doesn't exist)
            coEvery { mockRedisService.getUserById(userId.toString()) } returns
                RedisError.NotFound("User not found").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                // Return generic error message
                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to update profile"

                coVerify { mockRedisService.getUserById(userId.toString()) }
                // updateProfile should not be called since getUserById failed
                coVerify(exactly = 0) { mockRedisService.updateProfile(any(), any(), any(), any()) }
            }
        }

        test("should return BadRequest with generic message on Redis error") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            )
            val existingUser = User(
                id = userId,
                username = username,
                email = "old@example.com",
                firstName = "Old",
                lastName = "Name",
                passwordHash = "hashedpassword"
            )

            // Mock getUserById to succeed
            coEvery { mockRedisService.getUserById(userId.toString()) } returns existingUser.right()
            // Mock updateProfile to fail with OperationError
            coEvery {
                mockRedisService.updateProfile(username, updateRequest.email, updateRequest.firstName, updateRequest.lastName)
            } returns RedisError.OperationError("Redis connection failed").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                // Return generic error message
                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to update profile. Email may already be in use."

                coVerify { mockRedisService.getUserById(userId.toString()) }
                coVerify { mockRedisService.updateProfile(username, updateRequest.email, updateRequest.firstName, updateRequest.lastName) }
            }
        }
    }
})
