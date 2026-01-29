package dev.gertjanassies.routes

import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.MedicineWithExpiry
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.User
import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
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
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import java.time.LocalDateTime
import java.util.*

class MedicineExpiryRoutesTest : FunSpec({
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

    context("GET /medicineExpiry") {
        test("should return expiry list for user") {
            mockGetUser()

            // Create test data
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(medicineId1, "Aspirin", 100.0, "mg", 70.0) // 70 pills
            val medicine2 = Medicine(medicineId2, "Ibuprofen", 200.0, "mg", 35.0) // 35 pills

            // Schedule: take 1 Aspirin daily (70 days left), 1 Ibuprofen daily (35 days left)
            val schedule1 = Schedule(UUID.randomUUID(), medicineId1, "08:00", 1.0, daysOfWeek = emptyList())
            val schedule2 = Schedule(UUID.randomUUID(), medicineId2, "20:00", 1.0, daysOfWeek = emptyList())

            // Mock scan for medicines (first call)
            every { mockConnection.async() } returns mockAsyncCommands
            val medicineKeys = listOf(medicine1, medicine2).map {
                "medicate:$environment:user:$testUserId:medicine:${it.id}"
            }
            val medicineCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { medicineCursor.keys } returns medicineKeys
            every { medicineCursor.isFinished } returns true

            // Mock scan for schedules (second call)
            val scheduleKeys = listOf(schedule1, schedule2).map {
                "medicate:$environment:user:$testUserId:schedule:${it.id}"
            }
            val scheduleCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scheduleCursor.keys } returns scheduleKeys
            every { scheduleCursor.isFinished } returns true

            // Return medicine cursor first, then schedule cursor
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(medicineCursor) andThen createRedisFutureMock(scheduleCursor)

            // Mock get for each medicine
            listOf(medicine1, medicine2).forEach { medicine ->
                val key = "medicate:$environment:user:$testUserId:medicine:${medicine.id}"
                val medicineJson = json.encodeToString(medicine)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(medicineJson)
            }

            // Mock get for each schedule
            listOf(schedule1, schedule2).forEach { schedule ->
                val key = "medicate:$environment:user:$testUserId:schedule:${schedule.id}"
                val scheduleJson = json.encodeToString(schedule)
                every { mockAsyncCommands.get(key) } returns createRedisFutureMock(scheduleJson)
            }

            val customJson = Json {
                serializersModule = SerializersModule {
                    contextual(UUID::class, UUIDSerializer)
                    contextual(LocalDateTime::class, LocalDateTimeSerializer)
                }
                ignoreUnknownKeys = true
            }

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json(customJson) }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(redisService)
                    }
                }

                val client = createClient {
                    install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                        json(customJson)
                    }
                }

                val response = client.get("/medicineExpiry") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<List<MedicineWithExpiry>>()
                body.size shouldBe 2
                body[0].name shouldBe "Aspirin"
                body[1].name shouldBe "Ibuprofen"
                // Aspirin: 70 pills / 1 per day = 70 days
                // Ibuprofen: 35 pills / 1 per day = 35 days
            }
        }

        test("should return empty list when no medicines expiring") {
            mockGetUser()

            // Mock scan for medicines returns empty
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
                    authenticate("auth-jwt") {
                        medicineRoutes(redisService)
                    }
                }

                val response = client.get("/medicineExpiry") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("should return 401 if no username header") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(redisService)
                    }
                }

                val response = client.get("/medicineExpiry")

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
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(redisService)
                    }
                }

                val response = client.get("/medicineExpiry") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
})
