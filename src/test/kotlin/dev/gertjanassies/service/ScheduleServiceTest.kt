package dev.gertjanassies.service

import arrow.core.Either
import dev.gertjanassies.model.Schedule
import dev.gertjanassies.model.ScheduleRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import java.util.*

class ScheduleServiceTest : FunSpec({
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

    context("Schedule CRUD operations") {
        val scheduleId = UUID.randomUUID()
        val medicineId = UUID.randomUUID()
        val schedule = Schedule(
            id = scheduleId,
            medicineId = medicineId,
            time = "08:00",
            amount = 1.0
        )
        val scheduleJson = """{"id":"$scheduleId","medicineId":"$medicineId","time":"08:00","amount":1.0}"""
        val key = "test:schedule:$scheduleId"

        test("getSchedule should return schedule when it exists") {
            every { mockCommands.get(key) } returns scheduleJson

            val result = redisService.getSchedule(scheduleId.toString())

            result.shouldBeInstanceOf<Either.Right<Schedule>>()
            result.getOrNull()!!.apply {
                id shouldBe scheduleId
                this.medicineId shouldBe medicineId
                time shouldBe "08:00"
            }
            verify { mockCommands.get(key) }
        }

        test("getSchedule should return NotFound error when schedule doesn't exist") {
            every { mockCommands.get(key) } returns null

            val result = redisService.getSchedule(scheduleId.toString())

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("createSchedule should create schedule and generate UUID") {
            every { mockCommands.set(any(), any()) } returns "OK"

            val request = ScheduleRequest(medicineId, "08:00", 1.0)
            val result = redisService.createSchedule(request)

            result.shouldBeInstanceOf<Either.Right<Schedule>>()
            result.getOrNull()?.let { created ->
                created.id shouldNotBe null
                created.medicineId shouldBe medicineId
                created.time shouldBe "08:00"
                created.amount shouldBe 1.0
            }
            verify { mockCommands.set(match { it.startsWith("test:schedule:") }, any()) }
        }

        test("updateSchedule should update schedule when it exists") {
            val updatedSchedule = schedule.copy(time = "20:00")
            every { mockCommands.get(key) } returns scheduleJson
            every { mockCommands.set(key, any()) } returns "OK"

            val result = redisService.updateSchedule(scheduleId.toString(), updatedSchedule)

            result.shouldBeInstanceOf<Either.Right<Schedule>>()
            result.getOrNull()!!.time shouldBe "20:00"
            verify { mockCommands.get(key) }
            verify { mockCommands.set(key, any()) }
        }

        test("updateSchedule should return NotFound when schedule doesn't exist") {
            every { mockCommands.get(key) } returns null

            val result = redisService.updateSchedule(scheduleId.toString(), schedule)

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            verify { mockCommands.get(key) }
            verify(exactly = 0) { mockCommands.set(any(), any()) }
        }

        test("deleteSchedule should delete schedule when it exists") {
            every { mockCommands.del(key) } returns 1L

            val result = redisService.deleteSchedule(scheduleId.toString())

            result.shouldBeInstanceOf<Either.Right<Unit>>()
            verify { mockCommands.del(key) }
        }

        test("deleteSchedule should return NotFound when schedule doesn't exist") {
            every { mockCommands.del(key) } returns 0L

            val result = redisService.deleteSchedule(scheduleId.toString())

            result.shouldBeInstanceOf<Either.Left<RedisError.NotFound>>()
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            verify { mockCommands.del(key) }
        }

        test("getAllSchedules should return list of schedules") {
            val schedule1Id = UUID.randomUUID()
            val schedule2Id = UUID.randomUUID()
            val json1 = """{"id":"$schedule1Id","medicineId":"$medicineId","time":"08:00","amount":1.0}"""
            val json2 = """{"id":"$schedule2Id","medicineId":"$medicineId","time":"20:00","amount":2.0}"""
            
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf("test:schedule:$schedule1Id", "test:schedule:$schedule2Id")
            every { mockScanCursor.isFinished } returns true
            every { mockScanCursor.cursor } returns "0"
            every { mockCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns mockScanCursor
            every { mockCommands.get("test:schedule:$schedule1Id") } returns json1
            every { mockCommands.get("test:schedule:$schedule2Id") } returns json2

            val result = redisService.getAllSchedules()

            result.shouldBeInstanceOf<Either.Right<List<Schedule>>>()
            result.getOrNull()!!.size shouldBe 2
            verify { mockCommands.scan(any<io.lettuce.core.ScanArgs>()) }
        }
    }

    context("Daily schedule") {
        test("getDailySchedule should return medicines grouped by time") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val schedule1Id = UUID.randomUUID()
            val schedule2Id = UUID.randomUUID()
            val schedule3Id = UUID.randomUUID()
            
            // Two schedules at 08:00, one at 12:00
            val scheduleJson1 = """{"id":"$schedule1Id","medicineId":"$medicineId1","time":"08:00","amount":1.0}"""
            val scheduleJson2 = """{"id":"$schedule2Id","medicineId":"$medicineId2","time":"08:00","amount":2.0}"""
            val scheduleJson3 = """{"id":"$schedule3Id","medicineId":"$medicineId1","time":"12:00","amount":1.5}"""
            
            val medicineJson1 = """{"id":"$medicineId1","name":"Aspirin","dose":500.0,"unit":"mg","stock":100.0}"""
            val medicineJson2 = """{"id":"$medicineId2","name":"Ibuprofen","dose":400.0,"unit":"mg","stock":75.0}"""
            
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(
                "test:schedule:$schedule1Id",
                "test:schedule:$schedule2Id",
                "test:schedule:$schedule3Id"
            )
            every { mockScanCursor.isFinished } returns true
            every { mockScanCursor.cursor } returns "0"
            every { mockCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns mockScanCursor
            every { mockCommands.get("test:schedule:$schedule1Id") } returns scheduleJson1
            every { mockCommands.get("test:schedule:$schedule2Id") } returns scheduleJson2
            every { mockCommands.get("test:schedule:$schedule3Id") } returns scheduleJson3
            every { mockCommands.get("test:medicine:$medicineId1") } returns medicineJson1
            every { mockCommands.get("test:medicine:$medicineId2") } returns medicineJson2

            val result = redisService.getDailySchedule()

            result.shouldBeInstanceOf<Either.Right<*>>()
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 2 // Two time slots
            dailySchedule.schedule[0].time shouldBe "08:00"
            dailySchedule.schedule[0].medicines.size shouldBe 2
            dailySchedule.schedule[1].time shouldBe "12:00"
            dailySchedule.schedule[1].medicines.size shouldBe 1
            dailySchedule.schedule[1].medicines[0].amount shouldBe 1.5
        }
    }
})
