package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.DosageHistory
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
import java.time.LocalDateTime
import java.util.*

class DosageHistoryRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"
    val jwtToken = TestJwtConfig.generateToken(testUsername)

    beforeEach {
        mockRedisService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("GET /history") {
        test("should return empty list when no histories exist") {
            coEvery { mockRedisService.getAllDosageHistories(testUsername) } returns emptyList<DosageHistory>().right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/api/history") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DosageHistory>>()
                body.size shouldBe 0
                coVerify { mockRedisService.getAllDosageHistories(testUsername) }
            }
        }

        test("should return all dosage histories sorted by datetime descending") {
            val medicineId = UUID.randomUUID()
            val histories = listOf(
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 7, 9, 0),
                    medicineId = medicineId,
                    amount = 100.0,
                    scheduledTime = "Morning"
                ),
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 6, 14, 30),
                    medicineId = medicineId,
                    amount = 100.0,
                    scheduledTime = "Afternoon"
                ),
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 5, 10, 0),
                    medicineId = medicineId,
                    amount = 100.0,
                    scheduledTime = "Morning"
                )
            )

            coEvery { mockRedisService.getAllDosageHistories(testUsername) } returns histories.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/api/history") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DosageHistory>>()
                body.size shouldBe 3
                body[0].datetime shouldBe LocalDateTime.of(2026, 1, 7, 9, 0)
                body[0].amount shouldBe 100.0
                body[1].datetime shouldBe LocalDateTime.of(2026, 1, 6, 14, 30)
                body[2].datetime shouldBe LocalDateTime.of(2026, 1, 5, 10, 0)
                coVerify { mockRedisService.getAllDosageHistories(testUsername) }
            }
        }

        test("should handle multiple medicines") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()

            val histories = listOf(
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 6, 11, 0),
                    medicineId = medicineId2,
                    amount = 1000.0,
                    scheduledTime = "Morning"
                ),
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 6, 10, 0),
                    medicineId = medicineId1,
                    amount = 100.0,
                    scheduledTime = "Morning"
                )
            )

            coEvery { mockRedisService.getAllDosageHistories(testUsername) } returns histories.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/api/history") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<DosageHistory>>()
                body.size shouldBe 2
                body[0].medicineId shouldBe medicineId2
                body[0].amount shouldBe 1000.0
                body[1].medicineId shouldBe medicineId1
                body[1].amount shouldBe 100.0
                coVerify { mockRedisService.getAllDosageHistories(testUsername) }
            }
        }

        test("should return 500 on error") {
            coEvery { mockRedisService.getAllDosageHistories(testUsername) } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val response = client.get("/api/history") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.getAllDosageHistories(testUsername) }
            }
        }
    }

    context("DELETE /history/{id}") {
        test("should delete dosage history successfully") {
            val dosageHistoryId = UUID.randomUUID()
            coEvery { mockRedisService.deleteDosageHistory(testUsername, dosageHistoryId) } returns Unit.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.NoContent
                coVerify { mockRedisService.deleteDosageHistory(testUsername, dosageHistoryId) }
            }
        }

        test("should return 401 when no username header provided") {
            val dosageHistoryId = UUID.randomUUID()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("should return 400 when id is invalid") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val response = client.delete("/api/history/invalid-uuid") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 404 when dosage history not found") {
            val dosageHistoryId = UUID.randomUUID()
            coEvery { mockRedisService.deleteDosageHistory(testUsername, dosageHistoryId) } returns RedisError.NotFound("Not found").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.NotFound
                coVerify { mockRedisService.deleteDosageHistory(testUsername, dosageHistoryId) }
            }
        }

        test("should return 500 on operation error") {
            val dosageHistoryId = UUID.randomUUID()
            coEvery { mockRedisService.deleteDosageHistory(testUsername, dosageHistoryId) } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(mockRedisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.deleteDosageHistory(testUsername, dosageHistoryId) }
            }
        }
    }
})
