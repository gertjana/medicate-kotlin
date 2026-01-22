package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.MedicineWithExpiry
import dev.gertjanassies.model.User
import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
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
import java.time.LocalDateTime
import java.util.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

class MedicineExpiryRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"
    val testUserId = UUID.randomUUID()
    val jwtToken = TestJwtConfig.generateToken(testUsername, testUserId.toString())

    beforeEach { mockRedisService = mockk(relaxed = true) }
    afterEach { clearAllMocks() }

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

    context("GET /medicineExpiry") {
        test("should return expiry list for user") {
            mockGetUser()
            val now = LocalDateTime.of(2026, 1, 20, 0, 0)
            val medicines = listOf(
                MedicineWithExpiry(
                    id = UUID.randomUUID(),
                    name = "Aspirin",
                    dose = 100.0,
                    unit = "mg",
                    stock = 10.0,
                    description = null,
                    expiryDate = now
                ),
                MedicineWithExpiry(
                    id = UUID.randomUUID(),
                    name = "Ibuprofen",
                    dose = 200.0,
                    unit = "mg",
                    stock = 5.0,
                    description = null,
                    expiryDate = now.plusDays(3)
                )
            )
            coEvery { mockRedisService.medicineExpiry(testUsername, any()) } returns medicines.right()

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
                        medicineRoutes(mockRedisService)
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
                coVerify { mockRedisService.medicineExpiry(testUsername, any()) }
            }
        }

        test("should return empty list when no medicines expiring") {
            mockGetUser()
            coEvery { mockRedisService.medicineExpiry(testUsername, any()) } returns emptyList<MedicineWithExpiry>().right()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(mockRedisService)
                    }
                }

                val response = client.get("/medicineExpiry") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.medicineExpiry(testUsername, any()) }
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
                        medicineRoutes(mockRedisService)
                    }
                }

                val response = client.get("/medicineExpiry")

                response.status shouldBe HttpStatusCode.Unauthorized
                coVerify(exactly = 0) { mockRedisService.medicineExpiry(any(), any()) }
            }
        }

        test("should return 500 on service error") {
            mockGetUser()
            coEvery { mockRedisService.medicineExpiry(testUsername, any()) } returns
                dev.gertjanassies.service.RedisError.OperationError("Database error").left()

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(mockRedisService)
                    }
                }

                val response = client.get("/medicineExpiry") {
                    header("Authorization", "Bearer $jwtToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify { mockRedisService.medicineExpiry(testUsername, any()) }
            }
        }
    }
})
