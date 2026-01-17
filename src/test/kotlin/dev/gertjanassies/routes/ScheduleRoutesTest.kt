package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.request.ScheduleRequest
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
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
import io.mockk.*
import java.util.*

class ScheduleRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"
    val jwtToken = TestJwtConfig.generateToken(testUsername)

    beforeEach {
        mockRedisService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("GET /schedule") {
        test("should return list of schedules") {
            val medicineId = UUID.randomUUID()
            val schedules = listOf(
                Schedule(UUID.randomUUID(), medicineId, "08:00", 1.0),
                Schedule(UUID.randomUUID(), medicineId, "20:00", 2.0)
            )
            coEvery { mockRedisService.getAllSchedules(any()) } returns schedules.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
                    }
                }

                val response = client.get("/schedule") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.getAllSchedules(any()) }
            }
        }

        test("should return 500 on error") {
            coEvery { mockRedisService.getAllSchedules(any()) } returns RedisError.OperationError("Error").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
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
            val scheduleId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, medicineId, "08:00", 1.0)
            coEvery { mockRedisService.getSchedule(any(), scheduleId.toString()) } returns schedule.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Schedule>()
                body.id shouldBe scheduleId
                coVerify { mockRedisService.getSchedule(any(), scheduleId.toString()) }
            }
        }

        test("should return 404 when schedule not found") {
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.getSchedule(any(), scheduleId.toString()) } returns
                RedisError.NotFound("Schedule not found").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
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
            val medicineId = UUID.randomUUID()
            val createdSchedule = Schedule(UUID.randomUUID(), medicineId, "12:00", 1.5)
            val request = ScheduleRequest(medicineId, "12:00", 1.5)
            coEvery { mockRedisService.createSchedule(any(), any<ScheduleRequest>()) } returns createdSchedule.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
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
                body.time shouldBe "12:00"
                coVerify { mockRedisService.createSchedule(any(), any<ScheduleRequest>()) }
            }
        }

        test("should return 500 on create error") {
            val request = ScheduleRequest(UUID.randomUUID(), "12:00", 1.5)
            coEvery { mockRedisService.createSchedule(any(), any<ScheduleRequest>()) } returns
                RedisError.OperationError("Failed to create").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
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
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, UUID.randomUUID(), "14:00", 2.0)
            coEvery { mockRedisService.updateSchedule(any(), scheduleId.toString(), any<Schedule>()) } returns schedule.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                    contentType(ContentType.Application.Json)
                    setBody(schedule)
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.updateSchedule(any(), scheduleId.toString(), any<Schedule>()) }
            }
        }

        test("should return 404 when schedule not found") {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, UUID.randomUUID(), "14:00", 2.0)
            coEvery { mockRedisService.updateSchedule(any(), scheduleId.toString(), any<Schedule>()) } returns
                RedisError.NotFound("Schedule not found").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
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
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.deleteSchedule(any(), scheduleId.toString()) } returns Unit.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
                    }
                }

                val response = client.delete("/schedule/$scheduleId") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.NoContent
                coVerify { mockRedisService.deleteSchedule(any(), scheduleId.toString()) }
            }
        }

        test("should return 404 when schedule not found") {
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.deleteSchedule(any(), scheduleId.toString()) } returns
                RedisError.NotFound("Schedule not found").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
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
