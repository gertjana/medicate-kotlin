package dev.gertjanassies.service

import dev.gertjanassies.model.*
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
 * Test suite for Schedule-related operations in RedisService.
 *
 * Tests cover:
 * - getSchedule: retrieval and deserialization
 * - createSchedule: creation with unique IDs
 * - updateSchedule: updates with existence checks
 * - deleteSchedule: deletion with error handling
 * - getAllSchedules: scan cursor pagination and filtering
 */
class ScheduleServiceTest : FunSpec({

    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    context("getSchedule") {
        test("should successfully retrieve and deserialize a schedule") {
            val medicineId = UUID.randomUUID()
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val scheduleJson = json.encodeToString(schedule)
            val key = "$environment:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(scheduleJson)

            val result = redisService.getSchedule(scheduleId.toString())

            result.isRight() shouldBe true
            result.getOrNull()?.id shouldBe scheduleId
            result.getOrNull()?.time shouldBe "08:00"
            result.getOrNull()?.amount shouldBe 1.0

            verify(exactly = 1) { mockAsyncCommands.get(key) }
        }

        test("should return NotFound when schedule doesn't exist") {
            val scheduleId = UUID.randomUUID()
            val key = "$environment:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(null as String?)

            val result = redisService.getSchedule(scheduleId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("should handle invalid schedule JSON") {
            val scheduleId = UUID.randomUUID()
            val invalidJson = """{"invalid": "json"}"""
            val key = "$environment:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(invalidJson)

            val result = redisService.getSchedule(scheduleId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.SerializationError>()
        }
    }

    context("createSchedule") {
        test("should successfully create a new schedule") {
            val medicineId = UUID.randomUUID()
            val scheduleRequest = ScheduleRequest(
                medicineId = medicineId,
                time = "12:00",
                amount = 2.0,
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            )

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            val result = redisService.createSchedule(scheduleRequest)

            result.isRight() shouldBe true
            val created = result.getOrNull()!!
            created.medicineId shouldBe medicineId
            created.time shouldBe "12:00"
            created.amount shouldBe 2.0

            verify(exactly = 1) { mockAsyncCommands.set(any(), any()) }
        }

        test("should return OperationError when create fails") {
            val medicineId = UUID.randomUUID()
            val scheduleRequest = ScheduleRequest(
                medicineId = medicineId,
                time = "16:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createFailedRedisFutureMock(RuntimeException("Create failed"))

            val result = redisService.createSchedule(scheduleRequest)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
        }
    }

    context("updateSchedule") {
        test("should successfully update an existing schedule") {
            val medicineId = UUID.randomUUID()
            val scheduleId = UUID.randomUUID()
            val originalSchedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val updatedSchedule = originalSchedule.copy(time = "09:00", amount = 2.0)
            val key = "$environment:schedule:$scheduleId"
            val originalJson = json.encodeToString(originalSchedule)
            val updatedJson = json.encodeToString(updatedSchedule)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(originalJson)
            every { mockAsyncCommands.set(key, updatedJson) } returns createRedisFutureMock("OK")

            val result = redisService.updateSchedule(scheduleId.toString(), updatedSchedule)

            result.isRight() shouldBe true
            result.getOrNull()?.time shouldBe "09:00"
            result.getOrNull()?.amount shouldBe 2.0
        }

        test("should return NotFound when updating non-existent schedule") {
            val scheduleId = UUID.randomUUID()
            val schedule = Schedule(
                id = scheduleId,
                medicineId = UUID.randomUUID(),
                time = "10:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val key = "$environment:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(null as String?)

            val result = redisService.updateSchedule(scheduleId.toString(), schedule)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }
    }

    context("deleteSchedule") {
        test("should successfully delete an existing schedule") {
            val scheduleId = UUID.randomUUID()
            val key = "$environment:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.del(key) } returns createRedisFutureMock(1L)

            val result = redisService.deleteSchedule(scheduleId.toString())

            result.isRight() shouldBe true

            verify(exactly = 1) { mockAsyncCommands.del(key) }
        }

        test("should return NotFound when deleting non-existent schedule") {
            val scheduleId = UUID.randomUUID()
            val key = "$environment:schedule:$scheduleId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.del(key) } returns createRedisFutureMock(0L)

            val result = redisService.deleteSchedule(scheduleId.toString())

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }
    }

    context("getAllSchedules") {
        test("should successfully retrieve all schedules") {
            val medicineId = UUID.randomUUID()
            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "20:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val key1 = "$environment:schedule:${schedule1.id}"
            val key2 = "$environment:schedule:${schedule2.id}"
            val json1 = json.encodeToString(schedule1)
            val json2 = json.encodeToString(schedule2)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(key1, key2)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(key1) } returns createRedisFutureMock(json1)
            every { mockAsyncCommands.get(key2) } returns createRedisFutureMock(json2)

            val result = redisService.getAllSchedules()

            result.isRight() shouldBe true
            val schedules = result.getOrNull()!!
            schedules.size shouldBe 2
            schedules[0].time shouldBe "08:00"
            schedules[1].time shouldBe "20:00"

            verify(exactly = 1) { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) }
        }

        test("should return empty list when no schedules exist") {
            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns emptyList()
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            val result = redisService.getAllSchedules()

            result.isRight() shouldBe true
            result.getOrNull()!!.size shouldBe 0
        }

        test("should skip invalid schedules during deserialization") {
            val validSchedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "12:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )
            val keyValid = "$environment:schedule:${validSchedule.id}"
            val keyInvalid = "$environment:schedule:invalid"
            val validJson = json.encodeToString(validSchedule)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(keyValid, keyInvalid)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(keyValid) } returns createRedisFutureMock(validJson)
            every { mockAsyncCommands.get(keyInvalid) } returns createRedisFutureMock("""invalid json""")

            val result = redisService.getAllSchedules()

            result.isRight() shouldBe true
            result.getOrNull()!!.size shouldBe 1
            result.getOrNull()!![0].time shouldBe "12:00"
        }
    }

    context("getDailySchedule") {
        test("should successfully build daily schedule for today with multiple time slots") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(
                id = medicineId1,
                name = "Medicine1",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )
            val medicine2 = Medicine(
                id = medicineId2,
                name = "Medicine2",
                dose = 200.0,
                unit = "mg",
                stock = 20.0
            )

            // Create schedules for today
            val today = java.time.LocalDate.now()
            val todayDayOfWeek = DayOfWeek.fromJavaDay(today.dayOfWeek)

            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId1,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )
            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId2,
                time = "20:00",
                amount = 2.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )

            val scheduleKey1 = "$environment:schedule:${schedule1.id}"
            val scheduleKey2 = "$environment:schedule:${schedule2.id}"
            val medicineKey1 = "$environment:medicine:$medicineId1"
            val medicineKey2 = "$environment:medicine:$medicineId2"

            val scheduleJson1 = json.encodeToString(schedule1)
            val scheduleJson2 = json.encodeToString(schedule2)
            val medicineJson1 = json.encodeToString(medicine1)
            val medicineJson2 = json.encodeToString(medicine2)

            every { mockConnection.async() } returns mockAsyncCommands

            // Mock scan for schedules
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKey1, scheduleKey2)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            // Mock get for schedules
            every { mockAsyncCommands.get(scheduleKey1) } returns createRedisFutureMock(scheduleJson1)
            every { mockAsyncCommands.get(scheduleKey2) } returns createRedisFutureMock(scheduleJson2)

            // Mock get for medicines
            every { mockAsyncCommands.get(medicineKey1) } returns createRedisFutureMock(medicineJson1)
            every { mockAsyncCommands.get(medicineKey2) } returns createRedisFutureMock(medicineJson2)

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 2
            dailySchedule.schedule[0].time shouldBe "08:00"
            dailySchedule.schedule[0].medicines.size shouldBe 1
            dailySchedule.schedule[0].medicines[0].medicine.name shouldBe "Medicine1"
            dailySchedule.schedule[1].time shouldBe "20:00"
            dailySchedule.schedule[1].medicines.size shouldBe 1
            dailySchedule.schedule[1].medicines[0].medicine.name shouldBe "Medicine2"
        }

        test("should filter schedules by today's day of week") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Daily Medicine",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )

            val today = java.time.LocalDate.now()
            val todayDayOfWeek = DayOfWeek.fromJavaDay(today.dayOfWeek)
            val tomorrowDayOfWeek = DayOfWeek.fromJavaDay(today.plusDays(1).dayOfWeek)

            // Create schedule for today
            val scheduleToday = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )
            // Create schedule for tomorrow
            val scheduleTomorrow = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "12:00",
                amount = 1.0,
                daysOfWeek = listOf(tomorrowDayOfWeek)
            )

            val scheduleKeyToday = "$environment:schedule:${scheduleToday.id}"
            val scheduleKeyTomorrow = "$environment:schedule:${scheduleTomorrow.id}"
            val medicineKey = "$environment:medicine:$medicineId"

            val scheduleTodayJson = json.encodeToString(scheduleToday)
            val scheduleTomorrowJson = json.encodeToString(scheduleTomorrow)
            val medicineJson = json.encodeToString(medicine)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKeyToday, scheduleKeyTomorrow)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(scheduleKeyToday) } returns createRedisFutureMock(scheduleTodayJson)
            every { mockAsyncCommands.get(scheduleKeyTomorrow) } returns createRedisFutureMock(scheduleTomorrowJson)
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 1
            dailySchedule.schedule[0].time shouldBe "08:00"
        }

        test("should handle schedules with empty daysOfWeek (daily schedules)") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Daily Medicine",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )

            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "10:00",
                amount = 1.0,
                daysOfWeek = emptyList()  // Empty means everyday
            )

            val scheduleKey = "$environment:schedule:${schedule.id}"
            val medicineKey = "$environment:medicine:$medicineId"

            val scheduleJson = json.encodeToString(schedule)
            val medicineJson = json.encodeToString(medicine)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKey)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(scheduleJson)
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 1
            dailySchedule.schedule[0].time shouldBe "10:00"
        }

        test("should group multiple medicines at the same time slot") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(
                id = medicineId1,
                name = "Medicine1",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )
            val medicine2 = Medicine(
                id = medicineId2,
                name = "Medicine2",
                dose = 200.0,
                unit = "mg",
                stock = 20.0
            )

            val today = java.time.LocalDate.now()
            val todayDayOfWeek = DayOfWeek.fromJavaDay(today.dayOfWeek)

            // Two schedules at the same time
            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId1,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )
            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId2,
                time = "08:00",
                amount = 2.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )

            val scheduleKey1 = "$environment:schedule:${schedule1.id}"
            val scheduleKey2 = "$environment:schedule:${schedule2.id}"
            val medicineKey1 = "$environment:medicine:$medicineId1"
            val medicineKey2 = "$environment:medicine:$medicineId2"

            val scheduleJson1 = json.encodeToString(schedule1)
            val scheduleJson2 = json.encodeToString(schedule2)
            val medicineJson1 = json.encodeToString(medicine1)
            val medicineJson2 = json.encodeToString(medicine2)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKey1, scheduleKey2)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(scheduleKey1) } returns createRedisFutureMock(scheduleJson1)
            every { mockAsyncCommands.get(scheduleKey2) } returns createRedisFutureMock(scheduleJson2)
            every { mockAsyncCommands.get(medicineKey1) } returns createRedisFutureMock(medicineJson1)
            every { mockAsyncCommands.get(medicineKey2) } returns createRedisFutureMock(medicineJson2)

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 1
            dailySchedule.schedule[0].time shouldBe "08:00"
            dailySchedule.schedule[0].medicines.size shouldBe 2
            dailySchedule.schedule[0].medicines.map { it.medicine.name } shouldBe listOf("Medicine1", "Medicine2")
        }

        test("should skip schedules with missing medicines") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()
            val medicine1 = Medicine(
                id = medicineId1,
                name = "Medicine1",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )

            val today = java.time.LocalDate.now()
            val todayDayOfWeek = DayOfWeek.fromJavaDay(today.dayOfWeek)

            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId1,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )
            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId2,  // This medicine doesn't exist
                time = "08:00",
                amount = 2.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )

            val scheduleKey1 = "$environment:schedule:${schedule1.id}"
            val scheduleKey2 = "$environment:schedule:${schedule2.id}"
            val medicineKey1 = "$environment:medicine:$medicineId1"
            val medicineKey2 = "$environment:medicine:$medicineId2"

            val scheduleJson1 = json.encodeToString(schedule1)
            val scheduleJson2 = json.encodeToString(schedule2)
            val medicineJson1 = json.encodeToString(medicine1)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKey1, scheduleKey2)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(scheduleKey1) } returns createRedisFutureMock(scheduleJson1)
            every { mockAsyncCommands.get(scheduleKey2) } returns createRedisFutureMock(scheduleJson2)
            every { mockAsyncCommands.get(medicineKey1) } returns createRedisFutureMock(medicineJson1)
            every { mockAsyncCommands.get(medicineKey2) } returns createRedisFutureMock(null as String?)  // Not found

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 1
            dailySchedule.schedule[0].medicines.size shouldBe 1  // Only one medicine available
            dailySchedule.schedule[0].medicines[0].medicine.name shouldBe "Medicine1"
        }

        test("should return empty daily schedule when no schedules for today") {
            val medicineId = UUID.randomUUID()

            val today = java.time.LocalDate.now()
            val tomorrowDayOfWeek = DayOfWeek.fromJavaDay(today.plusDays(1).dayOfWeek)

            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(tomorrowDayOfWeek)  // Only tomorrow
            )

            val scheduleKey = "$environment:schedule:${schedule.id}"
            val scheduleJson = json.encodeToString(schedule)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKey)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(scheduleJson)

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 0
        }

        test("should sort time slots chronologically") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Medicine",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )

            val today = java.time.LocalDate.now()
            val todayDayOfWeek = DayOfWeek.fromJavaDay(today.dayOfWeek)

            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "20:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )
            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )
            val schedule3 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "14:00",
                amount = 1.0,
                daysOfWeek = listOf(todayDayOfWeek)
            )

            val scheduleKey1 = "$environment:schedule:${schedule1.id}"
            val scheduleKey2 = "$environment:schedule:${schedule2.id}"
            val scheduleKey3 = "$environment:schedule:${schedule3.id}"
            val medicineKey = "$environment:medicine:$medicineId"

            val scheduleJson1 = json.encodeToString(schedule1)
            val scheduleJson2 = json.encodeToString(schedule2)
            val scheduleJson3 = json.encodeToString(schedule3)
            val medicineJson = json.encodeToString(medicine)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(scheduleKey1, scheduleKey2, scheduleKey3)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(scheduleKey1) } returns createRedisFutureMock(scheduleJson1)
            every { mockAsyncCommands.get(scheduleKey2) } returns createRedisFutureMock(scheduleJson2)
            every { mockAsyncCommands.get(scheduleKey3) } returns createRedisFutureMock(scheduleJson3)
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)

            val result = redisService.getDailySchedule()

            result.isRight() shouldBe true
            val dailySchedule = result.getOrNull()!!
            dailySchedule.schedule.size shouldBe 3
            dailySchedule.schedule[0].time shouldBe "08:00"
            dailySchedule.schedule[1].time shouldBe "14:00"
            dailySchedule.schedule[2].time shouldBe "20:00"
        }
    }
})
