package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.ScheduleRequest
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import io.mockk.coEvery
import java.util.*

class ScheduleRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService

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
            coEvery { mockRedisService.getAllSchedules() } returns schedules.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val response = client.get("/schedule")
                
                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.getAllSchedules() }
            }
        }

        test("should return 500 on error") {
            coEvery { mockRedisService.getAllSchedules() } returns RedisError.OperationError("Error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val response = client.get("/schedule")
                
                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("GET /schedule/{id}") {
        test("should return schedule by id") {
            val scheduleId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, medicineId, "08:00", 1.0)
            coEvery { mockRedisService.getSchedule(scheduleId.toString()) } returns schedule.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/schedule/$scheduleId")
                
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Schedule>()
                body.id shouldBe scheduleId
                coVerify { mockRedisService.getSchedule(scheduleId.toString()) }
            }
        }

        test("should return 404 when schedule not found") {
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.getSchedule(scheduleId.toString()) } returns 
                RedisError.NotFound("Schedule not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val response = client.get("/schedule/$scheduleId")
                
                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    context("POST /schedule") {
        test("should create schedule") {
            val medicineId = UUID.randomUUID()
            val createdSchedule = Schedule(UUID.randomUUID(), medicineId, "12:00", 1.5)
            val request = ScheduleRequest(medicineId, "12:00", 1.5)
            coEvery { mockRedisService.createSchedule(any()) } returns createdSchedule.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/schedule") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                response.status shouldBe HttpStatusCode.Created
                val body = response.body<Schedule>()
                body.time shouldBe "12:00"
                coVerify { mockRedisService.createSchedule(any()) }
            }
        }

        test("should return 500 on create error") {
            val request = ScheduleRequest(UUID.randomUUID(), "12:00", 1.5)
            coEvery { mockRedisService.createSchedule(any()) } returns 
                RedisError.OperationError("Failed to create").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/schedule") {
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
            coEvery { mockRedisService.updateSchedule(scheduleId.toString(), any()) } returns schedule.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/schedule/$scheduleId") {
                    contentType(ContentType.Application.Json)
                    setBody(schedule)
                }
                
                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.updateSchedule(scheduleId.toString(), any()) }
            }
        }

        test("should return 404 when schedule not found") {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(scheduleId, UUID.randomUUID(), "14:00", 2.0)
            coEvery { mockRedisService.updateSchedule(scheduleId.toString(), any()) } returns 
                RedisError.NotFound("Schedule not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/schedule/$scheduleId") {
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
            coEvery { mockRedisService.deleteSchedule(scheduleId.toString()) } returns Unit.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val response = client.delete("/schedule/$scheduleId")
                
                response.status shouldBe HttpStatusCode.NoContent
                coVerify { mockRedisService.deleteSchedule(scheduleId.toString()) }
            }
        }

        test("should return 404 when schedule not found") {
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.deleteSchedule(scheduleId.toString()) } returns 
                RedisError.NotFound("Schedule not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { scheduleRoutes(mockRedisService) }
                
                val response = client.delete("/schedule/$scheduleId")
                
                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})
