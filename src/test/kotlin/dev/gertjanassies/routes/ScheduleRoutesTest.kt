package dev.gertjanassies.routes

import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.ScheduleRequest
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

class ScheduleRoutesTest : FunSpec({
    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"
    val testUsername = "testuser"
    val testUserId = UUID.randomUUID()
    val jwtToken = TestJwtConfig.generateToken(testUsername, testUserId.toString())

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    // Helper function to mock getUserById call
    fun mockGetUser() {
        val testUser = User(
            id = testUserId,
            username = testUsername,
            email = "test@example.com",
            firstName = "Test",
            lastName = "User",
            passwordHash = "hashedpassword"
        )
        val userJson = json.encodeToString(testUser)
        val userKey = "medicate:$environment:user:id:$testUserId"

        every { mockConnection.async() } returns mockAsyncCommands
        every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
    }

    context("GET /schedule") {
        test("should return list of schedules") {
            mockGetUser()
            val medicineId = UUID.randomUUID()
            val schedules = listOf(
                Schedule(UUID.randomUUID(), medicineId, "08:00", 1.0),
                Schedule(UUID.randomUUID(), medicineId, "20:00", 2.0)
            )

            // Mock scan for schedules
            every { mockConnection.async() } returns mockAsyncCommands
            val scheduleKeys = schedules.map { "medicate:$environment:user:$testUserId:schedule:${it.id}" }
            val mockCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockCursor.keys } returns scheduleKeys
            every { mockCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockCursor)

            schedules.forEach { schedule ->
                val key = "medicate:$environment:user:$testUserId:schedule:${schedule.id}"
                val scheduleJson = json.encodeToString(schedule)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(scheduleJson)
            }

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val response = client.get("/schedule") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("should return 500 on error") {
            mockGetUser()

            // Mock scan to fail
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createFailedRedisFutureMock(RuntimeException("Error"))

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val response = client.get("/schedule") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("GET /schedule/{id}") {
        test("should return schedule by id") {
            mockGetUser()
            val scheduleId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, medicineId, "08:00", 1.0)
            val scheduleJson = json.encodeToString(schedule)
            val scheduleKey = "medicate:$environment:user:$testUserId:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(scheduleJson)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Schedule>()
                body.id shouldBe scheduleId
            }
        }

        test("should return 404 when schedule not found") {
            mockGetUser()
            val scheduleId = UUID.randomUUID()
            val scheduleKey = "medicate:$environment:user:$testUserId:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val response = client.get("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    context("POST /schedule") {
        test("should create schedule") {
            mockGetUser()
            val medicineId = UUID.randomUUID()
            val request = ScheduleRequest(medicineId, "08:00", 1.0)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/schedule") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<Schedule>()
                body.time shouldBe "08:00"
            }
        }

        test("should return 500 on create error") {
            mockGetUser()
            val request = ScheduleRequest(UUID.randomUUID(), "12:00", 1.5)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createFailedRedisFutureMock(RuntimeException("Failed to create"))

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/schedule") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("PUT /schedule/{id}") {
        test("should update schedule") {
            mockGetUser()
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, UUID.randomUUID(), "14:00", 2.0)
            val scheduleKey = "medicate:$environment:user:$testUserId:schedule:$scheduleId"
            val existingSchedule = Schedule(scheduleId, schedule.medicineId, "08:00", 1.0)
            val existingJson = json.encodeToString(existingSchedule)
            val updatedJson = json.encodeToString(schedule)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(existingJson)
            every { mockAsyncCommands.set(scheduleKey, updatedJson) } returns createRedisFutureMock("OK")

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(schedule)
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("should return 404 when schedule not found") {
            mockGetUser()
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, UUID.randomUUID(), "14:00", 2.0)
            val scheduleKey = "medicate:$environment:user:$testUserId:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(schedule)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    context("DELETE /schedule/{id}") {
        test("should delete schedule") {
            mockGetUser()
            val scheduleId = UUID.randomUUID()
            val scheduleKey = "medicate:$environment:user:$testUserId:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.del(scheduleKey) } returns createRedisFutureMock(1L)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val response = client.delete("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.NoContent
            }
        }

        test("should return 404 when schedule not found") {
            mockGetUser()
            val scheduleId = UUID.randomUUID()
            val scheduleKey = "medicate:$environment:user:$testUserId:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.del(scheduleKey) } returns createRedisFutureMock(0L)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(redisService)
                    }
                }

                val response = client.delete("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
