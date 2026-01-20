package dev.gertjanassies.service

import dev.gertjanassies.model.*
import dev.gertjanassies.model.request.*
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*

/**
 * Test suite for Medicine-related operations in RedisService.
 *
 * Tests cover:
 * - getMedicine: retrieval and deserialization
 * - createMedicine: creation with validation
 * - updateMedicine: updates with existence checks
 * - deleteMedicine: deletion with error handling
 * - getAllMedicines: scan cursor pagination and filtering
 */
class MedicineServiceTest : FunSpec({

    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"
    val testUsername = "testuser"
    val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001") // Fixed UUID for consistent testing

    // Helper function to mock getUser() call
    fun mockGetUser() {
        val usernameIndexKey = "medicate:$environment:user:username:$testUsername"
        val userKey = "medicate:$environment:user:id:$testUserId"
        val userJson = """{"id":"$testUserId","username":"$testUsername","email":"test@example.com","passwordHash":"hash"}"""

        every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(testUserId.toString())
        every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
    }

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    context("getMedicine") {
        test("should successfully retrieve and deserialize a medicine") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicineJson = json.encodeToString(medicine)
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(medicineJson)

            val result = redisService.getMedicine(testUsername, medicineId.toString())

            result.isRight() shouldBe true
            result.getOrNull()?.shouldBe(medicine)
            result.getOrNull()?.name shouldBe "Aspirin"
            result.getOrNull()?.dose shouldBe 500.0
            result.getOrNull()?.stock shouldBe 100.0

            verify(atLeast = 1) { mockConnection.async() }
            verify(exactly = 1) { mockAsyncCommands.get(key) }
        }

        test("should return NotFound error when medicine doesn't exist") {
            val medicineId = UUID.randomUUID()
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(null as String?)

            val result = redisService.getMedicine(testUsername, medicineId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "Medicine with id $medicineId not found"

            verify(atLeast = 1) { mockConnection.async() }
            verify(exactly = 1) { mockAsyncCommands.get(key) }
        }

        test("should return SerializationError when medicine JSON is invalid") {
            val medicineId = UUID.randomUUID()
            val invalidJson = """{"invalid": "data"}"""
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(invalidJson)

            val result = redisService.getMedicine(testUsername, medicineId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.SerializationError>()
            result.leftOrNull()?.message?.contains("Failed to deserialize medicine") shouldBe true

            verify(atLeast = 1) { mockConnection.async() }
            verify(exactly = 1) { mockAsyncCommands.get(key) }
        }

        test("should handle RedisFuture that throws an exception") {
            val medicineId = UUID.randomUUID()
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createFailedRedisFutureMock(RuntimeException("Redis connection error"))

            val result = redisService.getMedicine(testUsername, medicineId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "Medicine with id $medicineId not found"

            verify(atLeast = 1) { mockConnection.async() }
            verify(exactly = 1) { mockAsyncCommands.get(key) }
        }

        test("should handle malformed JSON causing deserialization error") {
            val medicineId = UUID.randomUUID()
            val malformedJson = """not valid json at all"""
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(malformedJson)

            val result = redisService.getMedicine(testUsername, medicineId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.SerializationError>()
            result.leftOrNull()?.message?.contains("Failed to deserialize medicine") shouldBe true

            verify(atLeast = 1) { mockConnection.async() }
            verify(exactly = 1) { mockAsyncCommands.get(key) }
        }

        test("should handle different medicine instances with various data") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Ibuprofen",
                dose = 400.0,
                unit = "mg",
                stock = 50.5
            )
            val medicineJson = json.encodeToString(medicine)
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(medicineJson)

            val result = redisService.getMedicine(testUsername, medicineId.toString())

            val retrieved = result.getOrNull()!!
            retrieved.id shouldBe medicineId
            retrieved.name shouldBe "Ibuprofen"
            retrieved.dose shouldBe 400.0
            retrieved.unit shouldBe "mg"
            retrieved.stock shouldBe 50.5
        }
    }

    context("createMedicine") {
        test("should successfully create a new medicine") {
            val medicineRequest = MedicineRequest(
                name = "Paracetamol",
                dose = 1000.0,
                unit = "mg",
                stock = 200.0
            )

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            val result = redisService.createMedicine(testUsername, medicineRequest)

            result.isRight() shouldBe true
            val created = result.getOrNull()!!
            created.name shouldBe "Paracetamol"
            created.dose shouldBe 1000.0
            created.unit shouldBe "mg"
            created.stock shouldBe 200.0

            verify(exactly = 1) { mockAsyncCommands.set(any(), any()) }
        }

        test("should return OperationError when connection fails during create") {
            val medicineRequest = MedicineRequest(
                name = "Metformin",
                dose = 500.0,
                unit = "mg",
                stock = 150.0
            )

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.set(any(), any()) } returns createFailedRedisFutureMock(RuntimeException("Connection lost"))

            val result = redisService.createMedicine(testUsername, medicineRequest)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message?.contains("Failed to create medicine") shouldBe true
        }
    }

    context("updateMedicine") {
        test("should successfully update an existing medicine") {
            val medicineId = UUID.randomUUID()
            val updatedMedicine = Medicine(
                id = medicineId,
                name = "Aspirin Updated",
                dose = 750.0,
                unit = "mg",
                stock = 150.0
            )
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"
            val existingJson = json.encodeToString(updatedMedicine.copy(name = "Aspirin"))
            val updatedJson = json.encodeToString(updatedMedicine)

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(existingJson)
            every { mockAsyncCommands.set(key, updatedJson) } returns createRedisFutureMock("OK")

            val result = redisService.updateMedicine(testUsername, medicineId.toString(), updatedMedicine)

            result.isRight() shouldBe true
            result.getOrNull()?.name shouldBe "Aspirin Updated"
            result.getOrNull()?.dose shouldBe 750.0

            verify(exactly = 1) { mockAsyncCommands.get(key) }
            verify(exactly = 1) { mockAsyncCommands.set(key, updatedJson) }
        }

        test("should return NotFound when updating non-existent medicine") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Phantom Medicine",
                dose = 100.0,
                unit = "mg",
                stock = 0.0
            )
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(null as String?)

            val result = redisService.updateMedicine(testUsername, medicineId.toString(), medicine)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }
    }

    context("deleteMedicine") {
        test("should successfully delete an existing medicine") {
            val medicineId = UUID.randomUUID()
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.del(key) } returns createRedisFutureMock(1L)

            val result = redisService.deleteMedicine(testUsername, medicineId.toString())

            result.isRight() shouldBe true

            verify(exactly = 1) { mockAsyncCommands.del(key) }
        }

        test("should return NotFound when deleting non-existent medicine") {
            val medicineId = UUID.randomUUID()
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.del(key) } returns createRedisFutureMock(0L)

            val result = redisService.deleteMedicine(testUsername, medicineId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("should return OperationError when delete fails") {
            val medicineId = UUID.randomUUID()
            val key = "medicate:$environment:user:$testUserId:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.del(key) } returns createFailedRedisFutureMock(RuntimeException("Redis error"))

            val result = redisService.deleteMedicine(testUsername, medicineId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
        }
    }

    context("getAllMedicines") {
        test("should successfully retrieve all medicines with single scan cursor page") {
            val medicine1 = Medicine(
                id = UUID.randomUUID(),
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicine2 = Medicine(
                id = UUID.randomUUID(),
                name = "Ibuprofen",
                dose = 400.0,
                unit = "mg",
                stock = 50.0
            )
            val key1 = "medicate:$environment:user:$testUserId:medicine:${medicine1.id}"
            val key2 = "medicate:$environment:user:$testUserId:medicine:${medicine2.id}"
            val json1 = json.encodeToString(medicine1)
            val json2 = json.encodeToString(medicine2)

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(key1, key2)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(key1) } returns createRedisFutureMock(json1)
            every { mockAsyncCommands.get(key2) } returns createRedisFutureMock(json2)

            val result = redisService.getAllMedicines(testUsername)

            result.isRight() shouldBe true
            val medicines = result.getOrNull()!!
            medicines.size shouldBe 2
            medicines[0].name shouldBe "Aspirin"
            medicines[1].name shouldBe "Ibuprofen"

            verify(exactly = 1) { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) }
            verify(exactly = 1) { mockAsyncCommands.get(key1) }
            verify(exactly = 1) { mockAsyncCommands.get(key2) }
        }

        test("should successfully retrieve medicines with multiple scan cursor pages") {
            val medicine1 = Medicine(
                id = UUID.randomUUID(),
                name = "Medicine1",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )
            val medicine2 = Medicine(
                id = UUID.randomUUID(),
                name = "Medicine2",
                dose = 200.0,
                unit = "mg",
                stock = 20.0
            )
            val key1 = "medicate:$environment:user:$testUserId:medicine:${medicine1.id}"
            val key2 = "medicate:$environment:user:$testUserId:medicine:${medicine2.id}"
            val json1 = json.encodeToString(medicine1)
            val json2 = json.encodeToString(medicine2)

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()

            val mockScanCursor1 = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor1.keys } returns listOf(key1)
            every { mockScanCursor1.isFinished } returns false
            every { mockScanCursor1.cursor } returns "1234567890"

            val mockScanCursor2 = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor2.keys } returns listOf(key2)
            every { mockScanCursor2.isFinished } returns true

            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor1)
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanCursor>(), any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor2)

            every { mockAsyncCommands.get(key1) } returns createRedisFutureMock(json1)
            every { mockAsyncCommands.get(key2) } returns createRedisFutureMock(json2)

            val result = redisService.getAllMedicines(testUsername)

            result.isRight() shouldBe true
            val medicines = result.getOrNull()!!
            medicines.size shouldBe 2
            medicines.any { it.name == "Medicine1" } shouldBe true
            medicines.any { it.name == "Medicine2" } shouldBe true

            verify(exactly = 1) { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) }
            verify(exactly = 1) { mockAsyncCommands.scan(any<io.lettuce.core.ScanCursor>(), any<io.lettuce.core.ScanArgs>()) }
        }

        test("should return empty list when no medicines exist") {
            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns emptyList()
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            val result = redisService.getAllMedicines(testUsername)

            result.isRight() shouldBe true
            result.getOrNull()!!.size shouldBe 0
        }

        test("should skip invalid medicines during deserialization") {
            val validMedicine = Medicine(
                id = UUID.randomUUID(),
                name = "Valid",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )
            val keyValid = "medicate:$environment:user:$testUserId:medicine:${validMedicine.id}"
            val keyInvalid = "medicate:$environment:user:$testUserId:medicine:invalid-key"
            val validJson = json.encodeToString(validMedicine)

            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(keyValid, keyInvalid)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(keyValid) } returns createRedisFutureMock(validJson)
            every { mockAsyncCommands.get(keyInvalid) } returns createRedisFutureMock("""not valid json""")

            val result = redisService.getAllMedicines(testUsername)

            result.isRight() shouldBe true
            result.getOrNull()!!.size shouldBe 1
            result.getOrNull()!![0].name shouldBe "Valid"
        }

        test("should return OperationError when scan fails") {
            every { mockConnection.async() } returns mockAsyncCommands
            mockGetUser()
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createFailedRedisFutureMock(RuntimeException("Scan failed"))

            val result = redisService.getAllMedicines(testUsername)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message?.contains("Failed to retrieve medicines") shouldBe true
        }
    }
})
