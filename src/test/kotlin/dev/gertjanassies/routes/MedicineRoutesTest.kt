package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.DosageHistory
import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.request.AddStockRequest
import dev.gertjanassies.model.request.DosageHistoryRequest
import dev.gertjanassies.model.request.MedicineRequest
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
import java.time.LocalDateTime
import java.util.*

class MedicineRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"

    beforeEach {
        mockRedisService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("GET /medicine") {
        test("should return list of medicines") {
            val medicines = listOf(
                Medicine(UUID.randomUUID(), "Medicine A", 100.0, "mg", 50.0),
                Medicine(UUID.randomUUID(), "Medicine B", 200.0, "mg", 75.0)
            )
            coEvery { mockRedisService.getAllMedicines(testUsername) } returns medicines.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val response = client.get("/medicine") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.getAllMedicines(testUsername) }
            }
        }

        test("should return 500 on error") {
            coEvery { mockRedisService.getAllMedicines(testUsername) } returns RedisError.OperationError("Error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val response = client.get("/medicine") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("GET /medicine/{id}") {
        test("should return medicine by id") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(medicineId, "Test Medicine", 500.0, "mg", 100.0)
            coEvery { mockRedisService.getMedicine(testUsername, medicineId.toString()) } returns medicine.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/medicine/$medicineId") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Medicine>()
                body.id shouldBe medicineId
                coVerify { mockRedisService.getMedicine(testUsername, medicineId.toString()) }
            }
        }

        test("should return 404 when medicine not found") {
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.getMedicine(testUsername, medicineId.toString()) } returns
                RedisError.NotFound("Medicine not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val response = client.get("/medicine/$medicineId") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    context("POST /medicine") {
        test("should create medicine") {
            val createdMedicine = Medicine(UUID.randomUUID(), "New Medicine", 250.0, "mg", 60.0)
            val request = MedicineRequest("New Medicine", 250.0, "mg", 60.0)
            coEvery { mockRedisService.createMedicine(testUsername, any()) } returns createdMedicine.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/medicine") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<Medicine>()
                body.name shouldBe "New Medicine"
                coVerify { mockRedisService.createMedicine(testUsername, any()) }
            }
        }

        test("should return 500 on create error") {
            val request = MedicineRequest("New Medicine", 250.0, "mg", 60.0)
            coEvery { mockRedisService.createMedicine(testUsername, any()) } returns
                RedisError.OperationError("Failed to create").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/medicine") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("PUT /medicine/{id}") {
        test("should update medicine") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(medicineId, "Updated Medicine", 750.0, "mg", 150.0)
            coEvery { mockRedisService.updateMedicine(testUsername, medicineId.toString(), any()) } returns medicine.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/medicine/$medicineId") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(medicine)
                }

                response.status shouldBe HttpStatusCode.OK
                coVerify { mockRedisService.updateMedicine(testUsername, medicineId.toString(), any()) }
            }
        }

        test("should return 404 when medicine not found") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(medicineId, "Updated Medicine", 750.0, "mg", 150.0)
            coEvery { mockRedisService.updateMedicine(testUsername, medicineId.toString(), any()) } returns
                RedisError.NotFound("Medicine not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/medicine/$medicineId") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(medicine)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    context("DELETE /medicine/{id}") {
        test("should delete medicine") {
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.deleteMedicine(testUsername, medicineId.toString()) } returns Unit.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val response = client.delete("/medicine/$medicineId") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.NoContent
                coVerify { mockRedisService.deleteMedicine(testUsername, medicineId.toString()) }
            }
        }

        test("should return 404 when medicine not found") {
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.deleteMedicine(testUsername, medicineId.toString()) } returns
                RedisError.NotFound("Medicine not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val response = client.delete("/medicine/$medicineId") {
                    header("X-Username", testUsername)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    context("POST /takedose") {
        test("should create dosage history and reduce stock") {
            val medicineId = UUID.randomUUID()
            val dosageHistory = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.now(),
                medicineId = medicineId,
                amount = 1.0
            )
            val request = DosageHistoryRequest(medicineId, 1.0)
            coEvery { mockRedisService.createDosageHistory(testUsername, medicineId, 1.0, null, null) } returns dosageHistory.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/takedose") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<DosageHistory>()
                body.medicineId shouldBe medicineId
                body.amount shouldBe 1.0
                coVerify { mockRedisService.createDosageHistory(testUsername, medicineId, 1.0, null, null) }
            }
        }

        test("should return 404 when medicine not found") {
            val medicineId = UUID.randomUUID()
            val request = DosageHistoryRequest(medicineId, 1.0)
            coEvery { mockRedisService.createDosageHistory(testUsername, medicineId, 1.0, null, null) } returns
                RedisError.NotFound("Medicine with id $medicineId not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/takedose") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("should return 500 on error") {
            val medicineId = UUID.randomUUID()
            val request = DosageHistoryRequest(medicineId, 1.0)
            coEvery { mockRedisService.createDosageHistory(testUsername, medicineId, 1.0, null, null) } returns
                RedisError.OperationError("Failed to create dosage history").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/takedose") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("POST /addstock") {
        test("should add stock to medicine") {
            val medicineId = UUID.randomUUID()
            val updatedMedicine = Medicine(medicineId, "Test Medicine", 500.0, "mg", 110.0)
            val request = AddStockRequest(medicineId, 10.0)
            coEvery { mockRedisService.addStock(testUsername, medicineId, 10.0) } returns updatedMedicine.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/addstock") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Medicine>()
                body.stock shouldBe 110.0
                coVerify { mockRedisService.addStock(testUsername, medicineId, 10.0) }
            }
        }

        test("should return 404 when medicine not found") {
            val medicineId = UUID.randomUUID()
            val request = AddStockRequest(medicineId, 10.0)
            coEvery { mockRedisService.addStock(testUsername, medicineId, 10.0) } returns
                RedisError.NotFound("Medicine with id $medicineId not found").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/addstock") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }

        test("should return 500 on error") {
            val medicineId = UUID.randomUUID()
            val request = AddStockRequest(medicineId, 10.0)
            coEvery { mockRedisService.addStock(testUsername, medicineId, 10.0) } returns
                RedisError.OperationError("Failed to add stock").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { medicineRoutes(mockRedisService) }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.post("/addstock") {
                    header("X-Username", testUsername)
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
})
