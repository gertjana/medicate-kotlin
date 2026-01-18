package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.response.AuthResponse
import dev.gertjanassies.model.response.UserResponse
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.*
import io.mockk.*

class UserRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    lateinit var mockJwtService: JwtService

    beforeEach {
        mockRedisService = mockk()
        mockJwtService = mockk()

        // Mock JWT token generation
        every { mockJwtService.generateToken(any()) } returns "test-jwt-token-123"
        every { mockJwtService.generateAccessToken(any()) } returns "test-access-token-123"
        every { mockJwtService.generateRefreshToken(any()) } returns "test-refresh-token-456"
    }

    afterEach {
        clearAllMocks()
    }

    context("POST /user/register") {
        test("should register a new user successfully and return JWT token") {
            val username = "testuser"
            val email = "testuser@example.com"
            val password = "password123"
            val request = UserRequest(username, email, password)
            val user = User(username, email = email, passwordHash = "hashedpassword")
            coEvery { mockRedisService.registerUser(username, email, password) } returns user.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<AuthResponse>()
                body.user.username shouldBe username
                body.user.email shouldBe email
                body.token shouldBe "test-access-token-123"
                body.refreshToken shouldBe "test-refresh-token-456"

                coVerify { mockRedisService.registerUser(username, email, password) }
                verify { mockJwtService.generateAccessToken(username) }
                verify { mockJwtService.generateRefreshToken(username) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "test@example.com", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
            val request = UserRequest(username, "", password)
            val user = User(username, email = "", passwordHash = "hashedpassword")
            coEvery { mockRedisService.loginUser(username, password) } returns user.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<AuthResponse>()
                body.user.username shouldBe username
                body.token shouldBe "test-access-token-123"
                body.refreshToken shouldBe "test-refresh-token-456"

                coVerify { mockRedisService.loginUser(username, password) }
                verify { mockJwtService.generateAccessToken(username) }
                verify { mockJwtService.generateRefreshToken(username) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

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
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.updatePassword(any(), any()) }
            }
        }

        test("should return 404 when user not found") {
            val username = "nonexistent"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)

            coEvery { mockRedisService.updatePassword(username, newPassword) } returns
                RedisError.NotFound("User not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.NotFound
                coVerify { mockRedisService.updatePassword(username, newPassword) }
            }
        }

        test("should return 500 on update error") {
            val username = "testuser"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)

            coEvery { mockRedisService.updatePassword(username, newPassword) } returns
                RedisError.OperationError("Redis connection failed").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ServerContentNegotiation) { json() }
                routing { userRoutes(mockRedisService, mockJwtService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.updatePassword(username, newPassword) }
            }
        }
    }
})
