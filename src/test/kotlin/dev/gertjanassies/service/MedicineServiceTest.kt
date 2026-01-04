package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.DosageHistory
import dev.gertjanassies.model.Medicine
import dev.gertjanassies.model.MedicineRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import java.util.*

class MedicineServiceTest : FunSpec({
    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockCommands: RedisCommands<String, String>
    lateinit var redisService: RedisService

    beforeEach {
        mockConnection = mockk()
        mockCommands = mockk()
        every { mockConnection.sync() } returns mockCommands
        
        redisService = RedisService("localhost", 6379, "test")
        // Use reflection to inject mock connection
        val connectionField = RedisService::class.java.getDeclaredField("connection")
        connectionField.isAccessible = true
        connectionField.set(redisService, mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    context("Medicine CRUD operations") {
        val medicineId = UUID.randomUUID()
        val medicine = Medicine(
            id = medicineId,
            name = "Test Medicine",
            dose = 500.0,
            unit = "mg",
            stock = 100.0
        )
        val medicineJson = """{"id":"$medicineId","name":"Test Medicine","dose":500.0,"unit":"mg","stock":100.0}"""
        val key = "test:medicine:$medicineId"

        test("getMedicine should return medicine when it exists") {
            every { mockCommands.get(key) } returns medicineJson

            val result = redisService.getMedicine(medicineId.toString())

            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            result.getOrNull()!!.apply {
                id shouldBe medicineId
                name shouldBe "Test Medicine"
                dose shouldBe 500.0
                unit shouldBe "mg"
                stock shouldBe 100.0
            }
            verify { mockCommands.get(key) }
        }

        test("getMedicine should return NotFound error when medicine doesn't exist") {
            every { mockCommands.get(key) } returns null

            val result = redisService.getMedicine(medicineId.toString())

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("createMedicine should create medicine and generate UUID") {
            every { mockCommands.set(any(), any()) } returns "OK"

            val request = MedicineRequest("Test Medicine", 500.0, "mg", 100.0)
            val result = redisService.createMedicine(request)

            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            result.getOrNull()?.let { created ->
                created.id shouldNotBe null
                created.name shouldBe "Test Medicine"
                created.dose shouldBe 500.0
                created.unit shouldBe "mg"
                created.stock shouldBe 100.0
            }
            verify { mockCommands.set(match { it.startsWith("test:medicine:") }, any()) }
        }

        test("updateMedicine should update medicine when it exists") {
            val updatedMedicine = medicine.copy(dose = 750.0)
            every { mockCommands.get(key) } returns medicineJson
            every { mockCommands.set(key, any()) } returns "OK"

            val result = redisService.updateMedicine(medicineId.toString(), updatedMedicine)

            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            result.getOrNull()!!.dose shouldBe 750.0
            verify { mockCommands.get(key) }
            verify { mockCommands.set(key, any()) }
        }

        test("updateMedicine should return NotFound when medicine doesn't exist") {
            every { mockCommands.get(key) } returns null

            val result = redisService.updateMedicine(medicineId.toString(), medicine)

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            verify { mockCommands.get(key) }
            verify(exactly = 0) { mockCommands.set(any(), any()) }
        }

        test("deleteMedicine should delete medicine when it exists") {
            every { mockCommands.del(key) } returns 1L

            val result = redisService.deleteMedicine(medicineId.toString())

            result.shouldBeInstanceOf<Either.Right<Unit>>()
            verify { mockCommands.del(key) }
        }

        test("deleteMedicine should return NotFound when medicine doesn't exist") {
            every { mockCommands.del(key) } returns 0L

            val result = redisService.deleteMedicine(medicineId.toString())

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            verify { mockCommands.del(key) }
        }

        test("getAllMedicines should return list of medicines") {
            val medicine1Id = UUID.randomUUID()
            val medicine2Id = UUID.randomUUID()
            val json1 = """{"id":"$medicine1Id","name":"Medicine 1","dose":100.0,"unit":"mg","stock":50.0}"""
            val json2 = """{"id":"$medicine2Id","name":"Medicine 2","dose":200.0,"unit":"mg","stock":75.0}"""
            
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf("test:medicine:$medicine1Id", "test:medicine:$medicine2Id")
            every { mockScanCursor.isFinished } returns true
            every { mockScanCursor.cursor } returns "0"
            every { mockCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns mockScanCursor
            every { mockCommands.get("test:medicine:$medicine1Id") } returns json1
            every { mockCommands.get("test:medicine:$medicine2Id") } returns json2

            val result = redisService.getAllMedicines()

            result.shouldBeInstanceOf<Either.Right<List<Medicine>>>()
            result.getOrNull()!!.size shouldBe 2
            verify { mockCommands.scan(any<io.lettuce.core.ScanArgs>()) }
        }
    }

    context("Dosage history operations") {
        test("createDosageHistory should create dosage history and reduce stock") {
            val medicineId = UUID.randomUUID()
            val medicineJson = """{"id":"$medicineId","name":"Test Medicine","dose":500.0,"unit":"mg","stock":100.0}"""
            val updatedMedicineJson = """{"id":"$medicineId","name":"Test Medicine","dose":500.0,"unit":"mg","stock":99.0}"""
            val medicineKey = "test:medicine:$medicineId"
            
            every { mockCommands.get(medicineKey) } returns medicineJson andThen updatedMedicineJson
            every { mockCommands.set(medicineKey, any()) } returns "OK"
            every { mockCommands.set(match { it.startsWith("test:dosagehistory:") }, any()) } returns "OK"

            val result = redisService.createDosageHistory(medicineId, 1.0)

            result.shouldBeInstanceOf<Either.Right<DosageHistory>>()
            val dosageHistory = result.getOrNull()!!
            dosageHistory.medicineId shouldBe medicineId
            dosageHistory.amount shouldBe 1.0
            dosageHistory.id shouldNotBe null
            
            verify { mockCommands.get(medicineKey) }
            verify { mockCommands.set(medicineKey, match { it.contains("\"stock\":99.0") }) }
            verify { mockCommands.set(match { it.startsWith("test:dosagehistory:") }, any()) }
        }

        test("createDosageHistory should return NotFound when medicine does not exist") {
            val medicineId = UUID.randomUUID()
            every { mockCommands.get("test:medicine:$medicineId") } returns null

            val result = redisService.createDosageHistory(medicineId, 1.0)

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            verify { mockCommands.get("test:medicine:$medicineId") }
        }
    }

    context("Stock management") {
        test("addStock should add stock to medicine") {
            val medicineId = UUID.randomUUID()
            val medicineJson = """{"id":"$medicineId","name":"Test Medicine","dose":500.0,"unit":"mg","stock":100.0}"""
            val medicineKey = "test:medicine:$medicineId"
            
            every { mockCommands.get(medicineKey) } returns medicineJson
            every { mockCommands.set(medicineKey, any()) } returns "OK"

            val result = redisService.addStock(medicineId, 10.0)

            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            val updatedMedicine = result.getOrNull()!!
            updatedMedicine.stock shouldBe 110.0
            
            verify { mockCommands.get(medicineKey) }
            verify { mockCommands.set(medicineKey, match { it.contains("\"stock\":110.0") }) }
        }

        test("addStock should return NotFound when medicine does not exist") {
            val medicineId = UUID.randomUUID()
            every { mockCommands.get("test:medicine:$medicineId") } returns null

            val result = redisService.addStock(medicineId, 10.0)

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            verify { mockCommands.get("test:medicine:$medicineId") }
        }
    }
})
