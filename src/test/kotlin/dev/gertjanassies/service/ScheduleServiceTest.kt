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
 * Test suite for Schedule operations demonstrating sync testing while mocking Redis.
 * 
 * Uses the same patterns as MedicineServiceTest:
 * - Mocks RedisService at the service layer
 * - Uses coEvery for suspend functions
 * - Uses runBlocking for synchronous test execution
 * - Tests both success and error scenarios with Arrow Either
 */
class ScheduleServiceTest : FunSpec({
    lateinit var mockRedisService: RedisService
    
    beforeEach {
        mockRedisService = mockk()
    }
    
    afterEach {
        clearAllMocks()
    }
    
    context("Schedule operations with mocked RedisService") {
        test("getAllSchedules should return list of schedules") {
            // Arrange
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId1,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId2,
                time = "20:00",
                amount = 2.0,
                daysOfWeek = emptyList()
            )
            val schedules = listOf(schedule1, schedule2)
            
            coEvery { mockRedisService.getAllSchedules() } returns schedules.right()
            
            // Act
            val result = runBlocking { mockRedisService.getAllSchedules() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<List<Schedule>>>()
            val resultSchedules = result.getOrNull()!!
            resultSchedules.size shouldBe 2
            resultSchedules[0].time shouldBe "08:00"
            resultSchedules[1].time shouldBe "20:00"
            
            coVerify { mockRedisService.getAllSchedules() }
        }
        
        test("getAllSchedules should handle errors") {
            // Arrange
            coEvery { mockRedisService.getAllSchedules() } returns 
                RedisError.OperationError("Failed to retrieve schedules").left()
            
            // Act
            val result = runBlocking { mockRedisService.getAllSchedules() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError>>()
            result.leftOrNull()!!.shouldBeInstanceOf<RedisError.OperationError>()
        }
        
        test("getSchedule should return schedule when it exists") {
            // Arrange
            val scheduleId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val schedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "12:00",
                amount = 1.5,
                daysOfWeek = emptyList()
            )
            
            coEvery { mockRedisService.getSchedule(scheduleId.toString()) } returns schedule.right()
            
            // Act
            val result = runBlocking { mockRedisService.getSchedule(scheduleId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Schedule>>()
            val retrievedSchedule = result.getOrNull()!!
            retrievedSchedule.id shouldBe scheduleId
            retrievedSchedule.time shouldBe "12:00"
            retrievedSchedule.amount shouldBe 1.5
            
            coVerify { mockRedisService.getSchedule(scheduleId.toString()) }
        }
        
        test("getSchedule should return NotFound when schedule doesn't exist") {
            // Arrange
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.getSchedule(scheduleId.toString()) } returns 
                RedisError.NotFound("Schedule with id $scheduleId not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.getSchedule(scheduleId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull()!!.message shouldBe "Schedule with id $scheduleId not found"
        }
        
        test("createSchedule should create and return new schedule") {
            // Arrange
            val medicineId = UUID.randomUUID()
            val request = ScheduleRequest(
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val createdSchedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            
            coEvery { mockRedisService.createSchedule(request) } returns createdSchedule.right()
            
            // Act
            val result = runBlocking { mockRedisService.createSchedule(request) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Schedule>>()
            val schedule = result.getOrNull()!!
            schedule.time shouldBe "08:00"
            schedule.amount shouldBe 1.0
            schedule.medicineId shouldBe medicineId
            
            coVerify { mockRedisService.createSchedule(request) }
        }
        
        test("createSchedule should handle errors") {
            // Arrange
            val request = ScheduleRequest(
                medicineId = UUID.randomUUID(),
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            
            coEvery { mockRedisService.createSchedule(request) } returns 
                RedisError.OperationError("Failed to create schedule").left()
            
            // Act
            val result = runBlocking { mockRedisService.createSchedule(request) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError>>()
        }
        
        test("updateSchedule should update existing schedule") {
            // Arrange
            val scheduleId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val updatedSchedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "14:00",
                amount = 2.0,
                daysOfWeek = emptyList()
            )
            
            coEvery { mockRedisService.updateSchedule(scheduleId.toString(), updatedSchedule) } returns 
                updatedSchedule.right()
            
            // Act
            val result = runBlocking { mockRedisService.updateSchedule(scheduleId.toString(), updatedSchedule) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Schedule>>()
            val schedule = result.getOrNull()!!
            schedule.time shouldBe "14:00"
            schedule.amount shouldBe 2.0
            
            coVerify { mockRedisService.updateSchedule(scheduleId.toString(), updatedSchedule) }
        }
        
        test("updateSchedule should return NotFound when schedule doesn't exist") {
            // Arrange
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                id = scheduleId,
                medicineId = UUID.randomUUID(),
                time = "14:00",
                amount = 2.0,
                daysOfWeek = emptyList()
            )
            
            coEvery { mockRedisService.updateSchedule(scheduleId.toString(), schedule) } returns 
                RedisError.NotFound("Schedule not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.updateSchedule(scheduleId.toString(), schedule) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
        }
        
        test("deleteSchedule should delete existing schedule") {
            // Arrange
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.deleteSchedule(scheduleId.toString()) } returns Unit.right()
            
            // Act
            val result = runBlocking { mockRedisService.deleteSchedule(scheduleId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<Unit>>()
            coVerify { mockRedisService.deleteSchedule(scheduleId.toString()) }
        }
        
        test("deleteSchedule should return NotFound when schedule doesn't exist") {
            // Arrange
            val scheduleId = UUID.randomUUID()
            coEvery { mockRedisService.deleteSchedule(scheduleId.toString()) } returns 
                RedisError.NotFound("Schedule not found").left()
            
            // Act
            val result = runBlocking { mockRedisService.deleteSchedule(scheduleId.toString()) }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
        }
        
        test("getDailySchedule should return grouped schedules by time") {
            // Arrange
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(medicineId1, "Aspirin", 500.0, "mg", 100.0)
            val medicine2 = Medicine(medicineId2, "Ibuprofen", 200.0, "mg", 50.0)
            
            val dailySchedule = DailySchedule(
                schedule = listOf(
                    TimeSlot(
                        time = "08:00",
                        medicines = listOf(
                            MedicineScheduleItem(medicine1, 1.0),
                            MedicineScheduleItem(medicine2, 1.0)
                        )
                    ),
                    TimeSlot(
                        time = "20:00",
                        medicines = listOf(
                            MedicineScheduleItem(medicine1, 2.0)
                        )
                    )
                )
            )
            
            coEvery { mockRedisService.getDailySchedule() } returns dailySchedule.right()
            
            // Act
            val result = runBlocking { mockRedisService.getDailySchedule() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Right<DailySchedule>>()
            val schedule = result.getOrNull()!!
            schedule.schedule.size shouldBe 2
            schedule.schedule[0].time shouldBe "08:00"
            schedule.schedule[0].medicines.size shouldBe 2
            schedule.schedule[1].time shouldBe "20:00"
            schedule.schedule[1].medicines.size shouldBe 1
            
            coVerify { mockRedisService.getDailySchedule() }
        }
        
        test("getDailySchedule should handle errors") {
            // Arrange
            coEvery { mockRedisService.getDailySchedule() } returns 
                RedisError.OperationError("Failed to retrieve daily schedule").left()
            
            // Act
            val result = runBlocking { mockRedisService.getDailySchedule() }
            
            // Assert
            result.shouldBeInstanceOf<Either.Left<RedisError>>()
        }
    }
})
