package dev.gertjanassies.routes

import dev.gertjanassies.model.DosageHistory
import dev.gertjanassies.model.User
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
import java.time.LocalDateTime
import java.util.*

class DosageHistoryRoutesTest : FunSpec({
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

    context("GET /history") {
        test("should return empty list when no histories exist") {
            mockGetUser()

            // Mock scan to return empty list
            every { mockConnection.async() } returns mockAsyncCommands
            val emptyCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { emptyCursor.keys } returns emptyList()
            every { emptyCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(emptyCursor)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
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
            }
        }

        test("should return all dosage histories sorted by datetime descending") {
            mockGetUser()
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

            // Mock scan for dosage histories
            every { mockConnection.async() } returns mockAsyncCommands
            val historyKeys = histories.map { "medicate:$environment:user:$testUserId:dosagehistory:${it.id}" }
            val historyCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { historyCursor.keys } returns historyKeys
            every { historyCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(historyCursor)

            // Mock get for each history
            histories.forEach { history ->
                val key = "medicate:$environment:user:$testUserId:dosagehistory:${history.id}"
                val historyJson = json.encodeToString(history)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(historyJson)
            }

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
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
            }
        }

        test("should handle multiple medicines") {
            mockGetUser()
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

            // Mock scan for dosage histories
            every { mockConnection.async() } returns mockAsyncCommands
            val historyKeys = histories.map { "medicate:$environment:user:$testUserId:dosagehistory:${it.id}" }
            val historyCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { historyCursor.keys } returns historyKeys
            every { historyCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(historyCursor)

            // Mock get for each history
            histories.forEach { history ->
                val key = "medicate:$environment:user:$testUserId:dosagehistory:${history.id}"
                val historyJson = json.encodeToString(history)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(historyJson)
            }

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
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
            }
        }

        test("should return 500 on error") {
            mockGetUser()

            // Mock scan to fail
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createFailedRedisFutureMock(RuntimeException("Database error"))

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
                        }
                    }
                }

                val response = client.get("/api/history") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("DELETE /history/{id}") {
        test("should delete dosage history successfully") {
            mockGetUser()
            val medicineId = UUID.randomUUID()
            val dosageHistoryId = UUID.randomUUID()
            val dosageHistory = DosageHistory(
                id = dosageHistoryId,
                datetime = LocalDateTime.now(),
                medicineId = medicineId,
                amount = 1.0
            )
            val dosageKey = "medicate:$environment:user:$testUserId:dosagehistory:$dosageHistoryId"
            val medicineKey = "medicate:$environment:user:$testUserId:medicine:$medicineId"
            val dosageJson = json.encodeToString(dosageHistory)
            val medicine = dev.gertjanassies.model.Medicine(medicineId, "Test Medicine", 500.0, "mg", 99.0)
            val medicineJson = json.encodeToString(medicine)
            val updatedMedicine = medicine.copy(stock = 100.0)
            val updatedMedicineJson = json.encodeToString(updatedMedicine)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(dosageKey) } returns createRedisFutureMock(dosageJson)
            every { mockAsyncCommands.watch(medicineKey, dosageKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(medicineKey, updatedMedicineJson) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.del(dosageKey) } returns createRedisFutureMock(1L)
            val mockTransactionResult = mockk<io.lettuce.core.TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.NoContent
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
                            dosageHistoryRoutes(redisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId")
                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("should return 400 when id is invalid") {
            mockGetUser()
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
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
            mockGetUser()
            val dosageHistoryId = UUID.randomUUID()
            val dosageKey = "medicate:$environment:user:$testUserId:dosagehistory:$dosageHistoryId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(dosageKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("should return 500 on operation error") {
            mockGetUser()
            val dosageHistoryId = UUID.randomUUID()
            val dosageKey = "medicate:$environment:user:$testUserId:dosagehistory:$dosageHistoryId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(dosageKey) } returns createFailedRedisFutureMock(RuntimeException("Database error"))

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    route("/api") {
                        authenticate("auth-jwt") {
                            dosageHistoryRoutes(redisService)
                        }
                    }
                }

                val response = client.delete("/api/history/$dosageHistoryId") {
                    header("Authorization", "Bearer $jwtToken")
                }
                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
})
