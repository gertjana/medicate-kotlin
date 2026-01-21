package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.AdherenceStatus
import dev.gertjanassies.model.DayAdherence
import dev.gertjanassies.model.WeeklyAdherence
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
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

class AdherenceRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"
    val jwtToken = TestJwtConfig.generateToken(testUsername)

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
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing { authenticate("auth-jwt") { adherenceRoutes(mockRedisService) } }

                val client = createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json()
                    }
                }

                val response = client.get("/adherence") {
                    header("Authorization", "Bearer $jwtToken")
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
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing { authenticate("auth-jwt") { adherenceRoutes(mockRedisService) } }

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
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing { authenticate("auth-jwt") { adherenceRoutes(mockRedisService) } }

                val response = client.get("/adherence") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.getWeeklyAdherence(testUsername) }
            }
        }
    }
})
