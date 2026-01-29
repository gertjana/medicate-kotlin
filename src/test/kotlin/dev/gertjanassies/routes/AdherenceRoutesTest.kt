package dev.gertjanassies.routes

import dev.gertjanassies.model.*
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
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
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.UUID

class AdherenceRoutesTest : FunSpec({
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

    context("GET /adherence") {
        test("should return weekly adherence for user") {
            mockGetUser()

            // Create test data
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()

            val schedule1 = Schedule(UUID.randomUUID(), medicineId1, "08:00", 1.0, daysOfWeek = emptyList())
            val schedule2 = Schedule(UUID.randomUUID(), medicineId2, "20:00", 1.0, daysOfWeek = emptyList())

            val today = java.time.LocalDate.now()
            val yesterday = today.minusDays(1)
            val twoDaysAgo = today.minusDays(2)

            val dosageHistory1 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = yesterday.atTime(8, 0),
                medicineId = medicineId1,
                amount = 1.0
            )
            val dosageHistory2 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = yesterday.atTime(20, 0),
                medicineId = medicineId2,
                amount = 1.0
            )
            val dosageHistory3 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = twoDaysAgo.atTime(8, 0),
                medicineId = medicineId1,
                amount = 1.0
            )

            // Mock scan for schedules (first call)
            every { mockConnection.async() } returns mockAsyncCommands
            val scheduleKeys = listOf(schedule1, schedule2).map {
                "medicate:$environment:user:$testUserId:schedule:${it.id}"
            }
            val scheduleCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scheduleCursor.keys } returns scheduleKeys
            every { scheduleCursor.isFinished } returns true

            // Mock scan for dosage histories (second call)
            val dosageKeys = listOf(dosageHistory1, dosageHistory2, dosageHistory3).map {
                "medicate:$environment:user:$testUserId:dosagehistory:${it.id}"
            }
            val dosageCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { dosageCursor.keys } returns dosageKeys
            every { dosageCursor.isFinished } returns true

            // Return schedule cursor first, then dosage cursor
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(scheduleCursor) andThen createRedisFutureMock(dosageCursor)

            // Mock get for each schedule
            listOf(schedule1, schedule2).forEach { schedule ->
                val key = "medicate:$environment:user:$testUserId:schedule:${schedule.id}"
                val scheduleJson = json.encodeToString(schedule)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(scheduleJson)
            }

            // Mock get for each dosage history
            listOf(dosageHistory1, dosageHistory2, dosageHistory3).forEach { history ->
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
                routing { authenticate("auth-jwt") { adherenceRoutes(redisService) } }

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
                body.days.size shouldBe 7
                // endDate is yesterday, so index 6 = yesterday
                body.days[6].status shouldBe AdherenceStatus.COMPLETE
                body.days[6].expectedCount shouldBe 2
                body.days[6].takenCount shouldBe 2
                // Two days ago is index 5
                body.days[5].status shouldBe AdherenceStatus.PARTIAL
                body.days[5].expectedCount shouldBe 2
                body.days[5].takenCount shouldBe 1
            }
        }

        test("should return 401 if no username header") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing { authenticate("auth-jwt") { adherenceRoutes(redisService) } }

                val response = client.get("/adherence")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("should return 500 on service error") {
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
                routing { authenticate("auth-jwt") { adherenceRoutes(redisService) } }

                val response = client.get("/adherence") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
})
