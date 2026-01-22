package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.*
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

class DailyRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"
    val testUserId = UUID.randomUUID()
    val jwtToken = TestJwtConfig.generateToken(testUsername, testUserId.toString())

    beforeEach {
        mockRedisService = mockk()
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
        coEvery { mockRedisService.getUserById(testUserId.toString()) } returns testUser.right()
    }

    context("GET /daily") {
        test("should return daily schedule grouped by time") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(medicineId1, "Aspirin", 500.0, "mg", 100.0)
            val medicine2 = Medicine(medicineId2, "Ibuprofen", 400.0, "mg", 75.0)

            val dailySchedule = DailySchedule(
                schedule = listOf(
                    TimeSlot(
                        time = "08:00",
                        medicines = listOf(
                            MedicineScheduleItem(medicine1, 1.0),
                            MedicineScheduleItem(medicine2, 2.0)
                        )
                    ),
                    TimeSlot(
                        time = "12:00",
                        medicines = listOf(
                            MedicineScheduleItem(medicine1, 1.5)
                        )
                    )
                )
            )

            mockGetUser()
            coEvery { mockRedisService.getDailySchedule(testUsername) } returns dailySchedule.right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        dailyRoutes(mockRedisService)
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
                coVerify { mockRedisService.getDailySchedule(testUsername) }
            }
        }

        test("should return 500 on error") {
            mockGetUser()
            coEvery { mockRedisService.getDailySchedule(testUsername) } returns
                RedisError.OperationError("Failed to retrieve schedule").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        dailyRoutes(mockRedisService)
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
