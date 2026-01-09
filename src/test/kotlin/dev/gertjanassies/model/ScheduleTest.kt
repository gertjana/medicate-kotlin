package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ScheduleTest : FunSpec({

    val json = Json { prettyPrint = false }

    context("Schedule serialization") {
        test("should serialize Schedule to JSON with all days") {
            val scheduleId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val medicineId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000")

            val schedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "08:00",
                amount = 500.0,
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            )

            val jsonString = json.encodeToString(schedule)

            jsonString shouldNotBe null
            jsonString.contains("550e8400-e29b-41d4-a716-446655440000") shouldBe true
            jsonString.contains("660e8400-e29b-41d4-a716-446655440000") shouldBe true
            jsonString.contains("08:00") shouldBe true
            jsonString.contains("500.0") shouldBe true
            jsonString.contains("MO") shouldBe true
            jsonString.contains("WE") shouldBe true
            jsonString.contains("FR") shouldBe true
        }

        test("should serialize Schedule with empty daysOfWeek (all days)") {
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "12:00",
                amount = 100.0,
                daysOfWeek = emptyList()
            )

            val jsonString = json.encodeToString(schedule)
            val deserialized = json.decodeFromString<Schedule>(jsonString)

            deserialized.daysOfWeek shouldBe emptyList()
        }

        test("should deserialize JSON to Schedule") {
            val jsonString = """{"id":"550e8400-e29b-41d4-a716-446655440000","medicineId":"660e8400-e29b-41d4-a716-446655440000","time":"20:00","amount":250.0,"daysOfWeek":"MO,TU,WE,TH,FR"}"""

            val schedule = json.decodeFromString<Schedule>(jsonString)

            schedule.id shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            schedule.medicineId shouldBe UUID.fromString("660e8400-e29b-41d4-a716-446655440000")
            schedule.time shouldBe "20:00"
            schedule.amount shouldBe 250.0
            schedule.daysOfWeek.size shouldBe 5
            schedule.daysOfWeek shouldBe listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY
            )
        }

        test("should round-trip serialize and deserialize Schedule") {
            val original = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "14:30",
                amount = 750.0,
                daysOfWeek = listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<Schedule>(jsonString)

            deserialized shouldBe original
        }

        test("should serialize Schedule with all days of week") {
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "06:00",
                amount = 300.0,
                daysOfWeek = DayOfWeek.entries.toList()
            )

            val jsonString = json.encodeToString(schedule)
            val deserialized = json.decodeFromString<Schedule>(jsonString)

            deserialized.daysOfWeek.size shouldBe 7
            deserialized.daysOfWeek shouldBe DayOfWeek.entries.toList()
        }
    }

    context("Schedule data class") {
        test("should create Schedule with all properties") {
            val scheduleId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()

            val schedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "09:00",
                amount = 500.0,
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)
            )

            schedule.id shouldBe scheduleId
            schedule.medicineId shouldBe medicineId
            schedule.time shouldBe "09:00"
            schedule.amount shouldBe 500.0
            schedule.daysOfWeek.size shouldBe 2
        }

        test("should support copy with modified properties") {
            val original = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "08:00",
                amount = 100.0,
                daysOfWeek = listOf(DayOfWeek.MONDAY)
            )

            val modified = original.copy(
                time = "20:00",
                amount = 200.0,
                daysOfWeek = listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
            )

            modified.id shouldBe original.id
            modified.medicineId shouldBe original.medicineId
            modified.time shouldBe "20:00"
            modified.amount shouldBe 200.0
            modified.daysOfWeek shouldBe listOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)
        }

        test("should support equality comparison") {
            val id = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val days = listOf(DayOfWeek.MONDAY)

            val schedule1 = Schedule(id, medicineId, "12:00", 100.0, days)
            val schedule2 = Schedule(id, medicineId, "12:00", 100.0, days)
            val schedule3 = Schedule(UUID.randomUUID(), medicineId, "12:00", 100.0, days)

            schedule1 shouldBe schedule2
            schedule1 shouldNotBe schedule3
        }

        test("should default to empty daysOfWeek when not specified") {
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "10:00",
                amount = 150.0
            )

            schedule.daysOfWeek shouldBe emptyList()
        }
    }
})
