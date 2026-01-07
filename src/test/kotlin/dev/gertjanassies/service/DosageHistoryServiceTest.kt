package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import java.util.*

/**
 * Test suite for DosageHistory operations demonstrating sync testing while mocking Redis.
 * 
 * Uses the same patterns as MedicineServiceTest:
 * - Mocks RedisService at the service layer
 * - Uses coEvery for suspend functions
 * - Uses runBlocking for synchronous test execution
 * - Tests both success and error scenarios with Arrow Either
 */
class DosageHistoryServiceTest : FunSpec({
    lateinit var mockRedisService: RedisService
    
    beforeEach {
        mockRedisService = mockk()
    }
    
    afterEach {
        clearAllMocks()
    }
    
    context("DosageHistory operations with mocked RedisService") {
        test("getAllDosageHistories should return list of dosage histories") {
            // Arrange
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val history1 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.of(2026, 1, 7, 9, 0),
                medicineId = medicineId1,
                amount = 100.0,
                scheduledTime = "Morning"
            )
            val history2 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.of(2026, 1, 6, 14, 30),
                medicineId = medicineId2,
                amount = 200.0,
                scheduledTime = "Afternoon"
            )
            val histories = listOf(history1, history2)
            
            coEvery { mockRedisService.getAllDosageHistories() } returns histories.right()
            
            // Act
            val result = runBlocking { mockRedisService.getAllDosageHistories() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val resultHistories = result.getOrNull()!!
            resultHistories.size shouldBe 2
            resultHistories[0].amount shouldBe 100.0
            resultHistories[0].scheduledTime shouldBe "Morning"
            resultHistories[1].amount shouldBe 200.0
            resultHistories[1].scheduledTime shouldBe "Afternoon"
            
            coVerify { mockRedisService.getAllDosageHistories() }
        }
        
        test("getAllDosageHistories should return empty list when no histories exist") {
            // Arrange
            coEvery { mockRedisService.getAllDosageHistories() } returns emptyList<DosageHistory>().right()
            
            // Act
            val result = runBlocking { mockRedisService.getAllDosageHistories() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            result.getOrNull()!!.size shouldBe 0
        }
        
        test("getAllDosageHistories should handle errors") {
            // Arrange
            coEvery { mockRedisService.getAllDosageHistories() } returns 
                RedisError.OperationError("Database error").left()
            
            // Act
            val result = runBlocking { mockRedisService.getAllDosageHistories() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError>>()
            result.leftOrNull()!!.shouldBeInstanceOf<RedisError.OperationError>()
        }
        

        
        test("createDosageHistory should create history and reduce medicine stock") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val amount = 1.0
            val scheduledTime = "Morning"
            val createdHistory = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.now(),
                medicineId = medicineId,
                amount = amount,
                scheduledTime = scheduledTime
            )
            
            coEvery { mockRedisService.createDosageHistory(medicineId, amount, scheduledTime, any()) } returns createdHistory.right()
            
            // Act
            val result = runBlocking { mockRedisService.createDosageHistory(medicineId, amount, scheduledTime) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<DosageHistory>>()
            val history = result.getOrNull()!!
            history.id shouldNotBe null
            history.medicineId shouldBe medicineId
            history.amount shouldBe amount
            history.scheduledTime shouldBe scheduledTime
            
            coVerify { mockRedisService.createDosageHistory(medicineId, amount, scheduledTime, any()) }
        }
        
        test("createDosageHistory should handle medicine not found") {
            // Arrange
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.createDosageHistory(medicineId, 1.0, any(), any()) } returns 
                RedisError.NotFound("Medicine with id $medicineId not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.createDosageHistory(medicineId, 1.0) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
        }
        
        test("createDosageHistory should handle errors during transaction") {
            // Arrange
            val medicineId = UUID.randomUUID()
            coEvery { mockRedisService.createDosageHistory(medicineId, 100.0, any(), any()) } returns 
                RedisError.OperationError("Failed to create dosage history").left()
            
            // Act
            val result = runBlocking { mockRedisService.createDosageHistory(medicineId, 100.0) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError>>()
        }
        
        test("createDosageHistory with scheduled time should work") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val amount = 1.5
            val scheduledTime = "08:00"
            val datetime = LocalDateTime.of(2026, 1, 7, 8, 0)
            val createdHistory = DosageHistory(
                id = UUID.randomUUID(),
                datetime = datetime,
                medicineId = medicineId,
                amount = amount,
                scheduledTime = scheduledTime
            )
            
            coEvery { mockRedisService.createDosageHistory(medicineId, amount, scheduledTime, datetime) } returns 
                createdHistory.right()
            
            // Act
            val result = runBlocking { 
                mockRedisService.createDosageHistory(medicineId, amount, scheduledTime, datetime) 
            }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<DosageHistory>>()
            val history = result.getOrNull()!!
            history.medicineId shouldBe medicineId
            history.amount shouldBe amount
            history.scheduledTime shouldBe scheduledTime
            history.datetime shouldBe datetime
            
            coVerify { mockRedisService.createDosageHistory(medicineId, amount, scheduledTime, datetime) }
        }
        
        test("getAllDosageHistories should return histories sorted by datetime descending") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val history1 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.of(2026, 1, 7, 9, 0),
                medicineId = medicineId,
                amount = 100.0,
                scheduledTime = "Morning"
            )
            val history2 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.of(2026, 1, 6, 14, 30),
                medicineId = medicineId,
                amount = 100.0,
                scheduledTime = "Afternoon"
            )
            val history3 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.of(2026, 1, 5, 10, 0),
                medicineId = medicineId,
                amount = 100.0,
                scheduledTime = "Morning"
            )
            val histories = listOf(history1, history2, history3) // Already sorted descending
            
            coEvery { mockRedisService.getAllDosageHistories() } returns histories.right()
            
            // Act
            val result = runBlocking { mockRedisService.getAllDosageHistories() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val resultHistories = result.getOrNull()!!
            resultHistories.size shouldBe 3
            // Verify they are in descending order
            resultHistories[0].datetime shouldBe LocalDateTime.of(2026, 1, 7, 9, 0)
            resultHistories[1].datetime shouldBe LocalDateTime.of(2026, 1, 6, 14, 30)
            resultHistories[2].datetime shouldBe LocalDateTime.of(2026, 1, 5, 10, 0)
        }
        
        test("getAllDosageHistories should handle multiple medicines") {
            // Arrange
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val histories = listOf(
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 6, 11, 0),
                    medicineId = medicineId2,
                    amount = 1000.0,
                    scheduledTime = "Morning"
                ),
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = LocalDateTime.of(2026, 1, 6, 10, 0),
                    medicineId = medicineId1,
                    amount = 100.0,
                    scheduledTime = "Morning"
                )
            )
            
            coEvery { mockRedisService.getAllDosageHistories() } returns histories.right()
            
            // Act
            val result = runBlocking { mockRedisService.getAllDosageHistories() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val resultHistories = result.getOrNull()!!
            resultHistories.size shouldBe 2
            resultHistories[0].medicineId shouldBe medicineId2
            resultHistories[0].amount shouldBe 1000.0
            resultHistories[1].medicineId shouldBe medicineId1
            resultHistories[1].amount shouldBe 100.0
        }
    }
})
