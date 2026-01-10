package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.response.UserResponse
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*

class UserRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService

    beforeEach {
        mockRedisService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("POST /user/register") {
        test("should register a new user successfully") {
            val username = "testuser"
            val password = "password123"
            val request = UserRequest(username, password)
            val user = User(username, passwordHash = "hashedpassword")
            coEvery { mockRedisService.registerUser(username, password) } returns user.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<UserResponse>()
                body.username shouldBe username
                coVerify { mockRedisService.registerUser(username, password) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.registerUser(any(), any()) }
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.registerUser(any(), any()) }
            }
        }

        test("should return 400 when password is too short") {
            val request = UserRequest("testuser", "12345")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.registerUser(any(), any()) }
            }
        }

        test("should return 400 on registration error") {
            val username = "existinguser"
            val password = "password123"
            val request = UserRequest(username, password)
            coEvery { mockRedisService.registerUser(username, password) } returns
                RedisError.OperationError("User already exists").left()


            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify { mockRedisService.registerUser(username, password) }
            }
        }
    }

    context("POST /user/login") {
        test("should login user successfully") {
            val username = "testuser"
            val password = "password123"
            val request = UserRequest(username, password)
            val user = User(username, passwordHash = "hashedpassword")
            coEvery { mockRedisService.loginUser(username, password) } returns user.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UserResponse>()
                body.username shouldBe username
                coVerify { mockRedisService.loginUser(username, password) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.loginUser(any(), any()) }
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
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
            val request = UserRequest(username, password)
            coEvery { mockRedisService.loginUser(username, password) } returns
                RedisError.NotFound("User not found").left()


            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Unauthorized
                coVerify { mockRedisService.loginUser(username, password) }
            }
        }
    }
})
