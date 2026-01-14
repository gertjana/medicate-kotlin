package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.AdherenceStatus
import dev.gertjanassies.model.DayAdherence
import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.WeeklyAdherence
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.testing.*
import io.mockk.*
import java.util.*

class AdherenceRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"

    beforeEach {
        mockRedisService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("GET /adherence") {
        test("should return weekly adherence for user") {
            val weeklyAdherence = WeeklyAdherence(
                days = listOf(
                    DayAdherence(
                        date = "2026-01-13",
                        dayOfWeek = "MONDAY",
                        dayNumber = 13,
                        month = 1,
                        status = AdherenceStatus.COMPLETE,
                        expectedCount = 2,
                        takenCount = 2
                    ),
                    DayAdherence(
                        date = "2026-01-12",
                        dayOfWeek = "SUNDAY",
                        dayNumber = 12,
                        month = 1,
                        status = AdherenceStatus.PARTIAL,
                        expectedCount = 2,
                        takenCount = 1
                    )
                )
            )
            coEvery { mockRedisService.getWeeklyAdherence(testUsername) } returns weeklyAdherence.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val client = createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json()
                    }
                }

                val response = client.get("/adherence") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<WeeklyAdherence>()
                body.days.size shouldBe 2
                body.days[0].status shouldBe AdherenceStatus.COMPLETE
                body.days[1].status shouldBe AdherenceStatus.PARTIAL
                coVerify { mockRedisService.getWeeklyAdherence(testUsername) }
            }
        }

        test("should return 401 if no username header") {
            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/adherence")

                response.status shouldBe HttpStatusCode.Unauthorized
                coVerify(exactly = 0) { mockRedisService.getWeeklyAdherence(any()) }
            }
        }

        test("should return 500 on service error") {
            coEvery { mockRedisService.getWeeklyAdherence(testUsername) } returns
                RedisError.OperationError("Database error").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/adherence") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.getWeeklyAdherence(testUsername) }
            }
        }
    }

    context("GET /lowstock") {
        test("should return low stock medicines with default threshold") {
            val medicines = listOf(
                Medicine(UUID.randomUUID(), "Low Stock Med 1", 100.0, "mg", 5.0),
                Medicine(UUID.randomUUID(), "Low Stock Med 2", 200.0, "mg", 3.0)
            )
            coEvery { mockRedisService.getLowStockMedicines(testUsername, 10.0) } returns medicines.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val client = createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json()
                    }
                }

                val response = client.get("/lowstock") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<Medicine>>()
                body.size shouldBe 2
                body[0].stock shouldBe 5.0
                body[1].stock shouldBe 3.0
                coVerify { mockRedisService.getLowStockMedicines(testUsername, 10.0) }
            }
        }

        test("should return low stock medicines with custom threshold") {
            val medicines = listOf(
                Medicine(UUID.randomUUID(), "Low Stock Med", 100.0, "mg", 15.0)
            )
            coEvery { mockRedisService.getLowStockMedicines(testUsername, 20.0) } returns medicines.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val client = createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json()
                    }
                }

                val response = client.get("/lowstock?threshold=20.0") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<Medicine>>()
                body.size shouldBe 1
                body[0].stock shouldBe 15.0
                coVerify { mockRedisService.getLowStockMedicines(testUsername, 20.0) }
            }
        }

        test("should return 400 for invalid threshold") {
            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/lowstock?threshold=invalid") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.getLowStockMedicines(any(), any()) }
            }
        }

        test("should return 400 for negative threshold") {
            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/lowstock?threshold=-5.0") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.getLowStockMedicines(any(), any()) }
            }
        }

        test("should return 400 for zero threshold") {
            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/lowstock?threshold=0") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.BadRequest
                coVerify(exactly = 0) { mockRedisService.getLowStockMedicines(any(), any()) }
            }
        }

        test("should return 401 if no username header") {
            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/lowstock")

                response.status shouldBe HttpStatusCode.Unauthorized
                coVerify(exactly = 0) { mockRedisService.getLowStockMedicines(any(), any()) }
            }
        }

        test("should return 500 on service error") {
            coEvery { mockRedisService.getLowStockMedicines(testUsername, 10.0) } returns
                RedisError.OperationError("Database error").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                install(ContentNegotiation) { json() }
                routing { adherenceRoutes(mockRedisService) }

                val response = client.get("/lowstock") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.getLowStockMedicines(testUsername, 10.0) }
            }
        }
    }
})
