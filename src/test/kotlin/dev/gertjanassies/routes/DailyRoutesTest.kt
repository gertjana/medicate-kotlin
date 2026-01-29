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

class DailyRoutesTest : FunSpec({
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

    context("GET /daily") {
        test("should return daily schedule grouped by time") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(medicineId1, "Aspirin", 500.0, "mg", 100.0)
            val medicine2 = Medicine(medicineId2, "Ibuprofen", 400.0, "mg", 75.0)

            val schedule1 = Schedule(UUID.randomUUID(), medicineId1, "08:00", 1.0)
            val schedule2 = Schedule(UUID.randomUUID(), medicineId2, "08:00", 2.0)
            val schedule3 = Schedule(UUID.randomUUID(), medicineId1, "12:00", 1.5)

            mockGetUser()

            // Mock scan - returns schedules first, then medicines
            every { mockConnection.async() } returns mockAsyncCommands
            val scheduleKeys = listOf(schedule1, schedule2, schedule3).map {
                "medicate:$environment:user:$testUserId:schedule:${it.id}"
            }
            val scheduleCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scheduleCursor.keys } returns scheduleKeys
            every { scheduleCursor.isFinished } returns true

            val medicineKeys = listOf(medicine1, medicine2).map {
                "medicate:$environment:user:$testUserId:medicine:${it.id}"
            }
            val medicineCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { medicineCursor.keys } returns medicineKeys
            every { medicineCursor.isFinished } returns true

            // Return schedule cursor first, then medicine cursor
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(scheduleCursor) andThen createRedisFutureMock(medicineCursor)

            // Mock get for each schedule
            listOf(schedule1, schedule2, schedule3).forEach { schedule ->
                val key = "medicate:$environment:user:$testUserId:schedule:${schedule.id}"
                val scheduleJson = json.encodeToString(schedule)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(scheduleJson)
            }

            // Mock get for each medicine
            listOf(medicine1, medicine2).forEach { medicine ->
                val key = "medicate:$environment:user:$testUserId:medicine:${medicine.id}"
                val medicineJson = json.encodeToString(medicine)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(medicineJson)
            }

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        dailyRoutes(redisService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/daily") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<DailySchedule>()
                body.schedule.size shouldBe 2
                body.schedule[0].time shouldBe "08:00"
                body.schedule[0].medicines.size shouldBe 2
                body.schedule[1].time shouldBe "12:00"
                body.schedule[1].medicines.size shouldBe 1
            }
        }

        test("should return 500 on error") {
            mockGetUser()

            // Mock scan to fail
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createFailedRedisFutureMock(RuntimeException("Failed to retrieve schedule"))

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        dailyRoutes(redisService)
                    }
                }

                val response = client.get("/daily") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
})
