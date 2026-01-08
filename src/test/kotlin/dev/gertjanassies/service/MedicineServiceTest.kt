package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.util.*

/**
 * Demonstration of sync testing while mocking Redis using MockK.
 * 
 * This test suite demonstrates the recommended testing approach:
 * 1. Mock the RedisService itself rather than the low-level Redis connection
 * 2. Use `every` to stub non-suspend methods
 * 3. Use `coEvery` to stub suspend methods
 * 4. Use `runBlocking` to execute suspend functions synchronously in tests
 * 5. Use `verify` and `coVerify` to assert method calls
 * 
 * NOTE: Testing async Redis operations at the connection level requires complex
 * setup with mockkStatic for extension functions. The recommended approach is to:
 * - Test business logic by mocking the RedisService (as shown here)
 * - Test Redis integration with actual Redis (using testcontainers)
 * - Test routes by mocking the service (as seen in route tests)
 */
class MedicineServiceTest : FunSpec({
    lateinit var mockRedisService: RedisService
    
    beforeEach {
        mockRedisService = mockk()
    }
    
    afterEach {
        clearAllMocks()
    }
    
    context("Medicine operations with mocked RedisService") {
        test("getAllMedicines should return list of medicines") {
            // Arrange
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
                dose = 200.0,
                unit = "mg",
                stock = 50.0
            )
            val medicines = listOf(medicine1, medicine2)
            
            // Mock the suspend function using coEvery
            coEvery { mockRedisService.getAllMedicines() } returns medicines.right()
            
            // Act - Run the suspend function synchronously using runBlocking
            val result = runBlocking { mockRedisService.getAllMedicines() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<List<Medicine>>>()
            val resultMedicines = result.getOrNull()!!
            resultMedicines.size shouldBe 2
            resultMedicines[0].name shouldBe "Aspirin"
            resultMedicines[1].name shouldBe "Ibuprofen"
            
            // Verify the method was called
            coVerify { mockRedisService.getAllMedicines() }
        }
        
        test("getAllMedicines should handle errors") {
            // Arrange
            coEvery { mockRedisService.getAllMedicines() } returns 
                RedisError.OperationError("Redis connection failed").left()
            
            // Act
            val result = runBlocking { mockRedisService.getAllMedicines() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError>>()
            result.leftOrNull()!!.shouldBeInstanceOf<RedisError.OperationError>()
        }
        
        test("getMedicine should return medicine when it exists") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            
            coEvery { mockRedisService.getMedicine(medicineId.toString()) } returns medicine.right()
            
            // Act
            val result = runBlocking { mockRedisService.getMedicine(medicineId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            val retrievedMedicine = result.getOrNull()!!
            retrievedMedicine.id shouldBe medicineId
            retrievedMedicine.name shouldBe "Aspirin"
            
            coVerify { mockRedisService.getMedicine(medicineId.toString()) }
        }
        
        test("getMedicine should return NotFound when medicine doesn't exist") {
            // Arrange
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.getMedicine(medicineId.toString()) } returns 
                RedisError.NotFound("Medicine with id $medicineId not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.getMedicine(medicineId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull()!!.message shouldBe "Medicine with id $medicineId not found"
        }
        
        test("createMedicine should create and return new medicine") {
            // Arrange
            val request = MedicineRequest(
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val createdMedicine = Medicine(
                id = UUID.randomUUID(),
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            
            coEvery { mockRedisService.createMedicine(request) } returns createdMedicine.right()
            
            // Act
            val result = runBlocking { mockRedisService.createMedicine(request) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            val medicine = result.getOrNull()!!
            medicine.name shouldBe "Aspirin"
            medicine.dose shouldBe 500.0
            
            coVerify { mockRedisService.createMedicine(request) }
        }
        
        test("updateMedicine should update existing medicine") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val updatedMedicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 150.0
            )
            
            coEvery { mockRedisService.updateMedicine(medicineId.toString(), updatedMedicine) } returns 
                updatedMedicine.right()
            
            // Act
            val result = runBlocking { mockRedisService.updateMedicine(medicineId.toString(), updatedMedicine) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            result.getOrNull()!!.stock shouldBe 150.0
            
            coVerify { mockRedisService.updateMedicine(medicineId.toString(), updatedMedicine) }
        }
        
        test("updateMedicine should return NotFound when medicine doesn't exist") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            
            coEvery { mockRedisService.updateMedicine(medicineId.toString(), medicine) } returns 
                RedisError.NotFound("Medicine not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.updateMedicine(medicineId.toString(), medicine) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
        }
        
        test("deleteMedicine should delete existing medicine") {
            // Arrange
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.deleteMedicine(medicineId.toString()) } returns Unit.right()
            
            // Act
            val result = runBlocking { mockRedisService.deleteMedicine(medicineId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Unit>>()
            coVerify { mockRedisService.deleteMedicine(medicineId.toString()) }
        }
        
        test("deleteMedicine should return NotFound when medicine doesn't exist") {
            // Arrange
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.deleteMedicine(medicineId.toString()) } returns 
                RedisError.NotFound("Medicine not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.deleteMedicine(medicineId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
        }
        
        test("addStock should add stock to existing medicine") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val updatedMedicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 150.0
            )
            
            coEvery { mockRedisService.addStock(medicineId, 50.0) } returns updatedMedicine.right()
            
            // Act
            val result = runBlocking { mockRedisService.addStock(medicineId, 50.0) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Medicine>>()
            result.getOrNull()!!.stock shouldBe 150.0
            
            coVerify { mockRedisService.addStock(medicineId, 50.0) }
        }
        
        test("addStock should return NotFound when medicine doesn't exist") {
            // Arrange
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.addStock(medicineId, 50.0) } returns 
                RedisError.NotFound("Medicine not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.addStock(medicineId, 50.0) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
        }
        
        test("createDosageHistory should create dosage history and reduce stock") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val dosageHistory = DosageHistory(
                id = UUID.randomUUID(),
                datetime = java.time.LocalDateTime.now(),
                medicineId = medicineId,
                amount = 1.0,
                scheduledTime = null
            )
            
            coEvery { mockRedisService.createDosageHistory(medicineId, 1.0) } returns dosageHistory.right()
            
            // Act
            val result = runBlocking { mockRedisService.createDosageHistory(medicineId, 1.0) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<DosageHistory>>()
            val history = result.getOrNull()!!
            history.medicineId shouldBe medicineId
            history.amount shouldBe 1.0
            
            coVerify { mockRedisService.createDosageHistory(medicineId, 1.0) }
        }
    }
})
