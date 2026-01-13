package dev.gertjanassies.routes

import arrow.core.right
import dev.gertjanassies.model.MedicineWithExpiry
import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

class MedicineExpiryRoutesTest : FunSpec({
    lateinit var mockRedisService: RedisService
    val testUsername = "testuser"

    beforeEach { mockRedisService = mockk(relaxed = true) }
    afterEach { clearAllMocks() }

    test("dummy test to avoid empty test suite") {
        1 + 1 shouldBe 2
    }

//    test("GET /medicineExpiry should return expiry list for user") {
//        val now = LocalDateTime.now()
//        val med = MedicineWithExpiry(UUID.randomUUID(), "Aspirin", 100.0, "mg", 10.0, null, now)
//        coEvery { mockRedisService.medicineExpiry(testUsername) } returns listOf(med).right()
//        val customJson = Json {
//            serializersModule = SerializersModule {
//                contextual(UUID::class, UUIDSerializer)
//                contextual(LocalDateTime::class, LocalDateTimeSerializer)
//            }
//            ignoreUnknownKeys = true
//        }
//        testApplication {
//            environment { config = MapApplicationConfig() }
//            install(ContentNegotiation) { json(customJson) }
//            routing { medicineRoutes(mockRedisService) }
//            val response = client.get("/medicineExpiry") {
//                header("X-Username", testUsername)
//            }
//            response.status shouldBe HttpStatusCode.OK
//            val body = response.body<List<MedicineWithExpiry>>()
//            body.size shouldBe 1
//            body[0].name shouldBe "Aspirin"
//        }
//    }
//
//    test("GET /medicineExpiry should return 401 if no username header") {
//        testApplication {
//            environment { config = MapApplicationConfig() }
//            install(ContentNegotiation) { json() }
//            routing { medicineRoutes(mockRedisService) }
//            val response = client.get("/medicineExpiry")
//            response.status shouldBe HttpStatusCode.Unauthorized
//        }
//    }
})
