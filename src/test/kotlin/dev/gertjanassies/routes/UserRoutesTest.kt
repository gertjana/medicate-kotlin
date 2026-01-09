package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.UserRequest
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
            val request = UserRequest(username)
            val user = User(username)
            coEvery { mockRedisService.registerUser(username) } returns user.right()

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
                val body = response.body<User>()
                body.username shouldBe username
                coVerify { mockRedisService.registerUser(username) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("")

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
                coVerify(exactly = 0) { mockRedisService.registerUser(any()) }
            }
        }

        test("should return 400 on registration error") {
            val username = "existinguser"
            val request = UserRequest(username)
            coEvery { mockRedisService.registerUser(username) } returns
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
                coVerify { mockRedisService.registerUser(username) }
            }
        }
    }

    context("POST /user/login") {
        test("should login user successfully") {
            val username = "testuser"
            val request = UserRequest(username)
            val user = User(username)
            coEvery { mockRedisService.loginUser(username) } returns user.right()

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
                val body = response.body<User>()
                body.username shouldBe username
                coVerify { mockRedisService.loginUser(username) }
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("")

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
                coVerify(exactly = 0) { mockRedisService.loginUser(any()) }
            }
        }

        test("should return 401 on login error") {
            val username = "nonexistent"
            val request = UserRequest(username)
            coEvery { mockRedisService.loginUser(username) } returns
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
                coVerify { mockRedisService.loginUser(username) }
            }
        }
    }
})
