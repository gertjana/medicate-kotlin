package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class WeeklyAdherenceTest : FunSpec({

    val json = Json { prettyPrint = false }

    context("AdherenceStatus enum") {
        test("should have all status values") {
            val values = AdherenceStatus.entries

            values.size shouldBe 3
            values.contains(AdherenceStatus.NONE) shouldBe true
            values.contains(AdherenceStatus.PARTIAL) shouldBe true
            values.contains(AdherenceStatus.COMPLETE) shouldBe true
        }

        test("should serialize AdherenceStatus to JSON") {
            json.encodeToString(AdherenceStatus.NONE) shouldBe "\"NONE\""
            json.encodeToString(AdherenceStatus.PARTIAL) shouldBe "\"PARTIAL\""
            json.encodeToString(AdherenceStatus.COMPLETE) shouldBe "\"COMPLETE\""
        }

        test("should deserialize JSON to AdherenceStatus") {
            json.decodeFromString<AdherenceStatus>("\"NONE\"") shouldBe AdherenceStatus.NONE
            json.decodeFromString<AdherenceStatus>("\"PARTIAL\"") shouldBe AdherenceStatus.PARTIAL
            json.decodeFromString<AdherenceStatus>("\"COMPLETE\"") shouldBe AdherenceStatus.COMPLETE
        }
    }

    context("DayAdherence serialization") {
        test("should serialize DayAdherence to JSON") {
            val dayAdherence = DayAdherence(
                date = "2026-01-09",
                dayOfWeek = "Thursday",
                dayNumber = 9,
                month = 1,
                status = AdherenceStatus.COMPLETE,
                expectedCount = 3,
                takenCount = 3
            )

            val jsonString = json.encodeToString(dayAdherence)

            jsonString shouldNotBe null
            jsonString.contains("2026-01-09") shouldBe true
            jsonString.contains("Thursday") shouldBe true
            jsonString.contains("COMPLETE") shouldBe true
        }

        test("should deserialize JSON to DayAdherence") {
            val jsonString = """{"date":"2026-01-08","dayOfWeek":"Wednesday","dayNumber":8,"month":1,"status":"PARTIAL","expectedCount":4,"takenCount":2}"""

            val dayAdherence = json.decodeFromString<DayAdherence>(jsonString)

            dayAdherence.date shouldBe "2026-01-08"
            dayAdherence.dayOfWeek shouldBe "Wednesday"
            dayAdherence.dayNumber shouldBe 8
            dayAdherence.month shouldBe 1
            dayAdherence.status shouldBe AdherenceStatus.PARTIAL
            dayAdherence.expectedCount shouldBe 4
            dayAdherence.takenCount shouldBe 2
        }

        test("should round-trip serialize and deserialize DayAdherence") {
            val original = DayAdherence(
                date = "2026-01-10",
                dayOfWeek = "Friday",
                dayNumber = 10,
                month = 1,
                status = AdherenceStatus.NONE,
                expectedCount = 5,
                takenCount = 0
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<DayAdherence>(jsonString)

            deserialized shouldBe original
        }

        test("should serialize DayAdherence with different statuses") {
            val noneDay = DayAdherence("2026-01-01", "Thursday", 1, 1, AdherenceStatus.NONE, 3, 0)
            val partialDay = DayAdherence("2026-01-02", "Friday", 2, 1, AdherenceStatus.PARTIAL, 3, 2)
            val completeDay = DayAdherence("2026-01-03", "Saturday", 3, 1, AdherenceStatus.COMPLETE, 3, 3)

            val noneJson = json.encodeToString(noneDay)
            val partialJson = json.encodeToString(partialDay)
            val completeJson = json.encodeToString(completeDay)

            noneJson.contains("NONE") shouldBe true
            partialJson.contains("PARTIAL") shouldBe true
            completeJson.contains("COMPLETE") shouldBe true
        }
    }

    context("WeeklyAdherence serialization") {
        test("should serialize WeeklyAdherence to JSON") {
            val days = listOf(
                DayAdherence("2026-01-06", "Monday", 6, 1, AdherenceStatus.COMPLETE, 3, 3),
                DayAdherence("2026-01-07", "Tuesday", 7, 1, AdherenceStatus.PARTIAL, 3, 2),
                DayAdherence("2026-01-08", "Wednesday", 8, 1, AdherenceStatus.NONE, 3, 0),
                DayAdherence("2026-01-09", "Thursday", 9, 1, AdherenceStatus.COMPLETE, 3, 3),
                DayAdherence("2026-01-10", "Friday", 10, 1, AdherenceStatus.PARTIAL, 3, 1),
                DayAdherence("2026-01-11", "Saturday", 11, 1, AdherenceStatus.COMPLETE, 2, 2),
                DayAdherence("2026-01-12", "Sunday", 12, 1, AdherenceStatus.COMPLETE, 2, 2)
            )

            val weeklyAdherence = WeeklyAdherence(days = days)

            val jsonString = json.encodeToString(weeklyAdherence)

            jsonString shouldNotBe null
            jsonString.contains("2026-01-06") shouldBe true
            jsonString.contains("2026-01-12") shouldBe true
            jsonString.contains("Monday") shouldBe true
            jsonString.contains("Sunday") shouldBe true
        }

        test("should deserialize JSON to WeeklyAdherence") {
            val jsonString = """{"days":[{"date":"2026-01-06","dayOfWeek":"Monday","dayNumber":6,"month":1,"status":"COMPLETE","expectedCount":3,"takenCount":3},{"date":"2026-01-07","dayOfWeek":"Tuesday","dayNumber":7,"month":1,"status":"PARTIAL","expectedCount":3,"takenCount":2}]}"""

            val weeklyAdherence = json.decodeFromString<WeeklyAdherence>(jsonString)

            weeklyAdherence.days.size shouldBe 2
            weeklyAdherence.days[0].date shouldBe "2026-01-06"
            weeklyAdherence.days[1].date shouldBe "2026-01-07"
        }

        test("should handle empty days list") {
            val weeklyAdherence = WeeklyAdherence(days = emptyList())

            val jsonString = json.encodeToString(weeklyAdherence)
            val deserialized = json.decodeFromString<WeeklyAdherence>(jsonString)

            deserialized.days shouldBe emptyList()
        }

        test("should round-trip serialize and deserialize WeeklyAdherence") {
            val original = WeeklyAdherence(
                days = listOf(
                    DayAdherence("2026-01-06", "Monday", 6, 1, AdherenceStatus.COMPLETE, 4, 4),
                    DayAdherence("2026-01-07", "Tuesday", 7, 1, AdherenceStatus.PARTIAL, 4, 3),
                    DayAdherence("2026-01-08", "Wednesday", 8, 1, AdherenceStatus.NONE, 4, 0)
                )
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<WeeklyAdherence>(jsonString)

            deserialized shouldBe original
        }

        test("should serialize full week of adherence data") {
            val fullWeek = WeeklyAdherence(
                days = (6..12).map { day ->
                    val dayOfWeek = when (day) {
                        6 -> "Monday"
                        7 -> "Tuesday"
                        8 -> "Wednesday"
                        9 -> "Thursday"
                        10 -> "Friday"
                        11 -> "Saturday"
                        12 -> "Sunday"
                        else -> "Unknown"
                    }
                    DayAdherence(
                        date = "2026-01-${day.toString().padStart(2, '0')}",
                        dayOfWeek = dayOfWeek,
                        dayNumber = day,
                        month = 1,
                        status = if (day % 2 == 0) AdherenceStatus.COMPLETE else AdherenceStatus.PARTIAL,
                        expectedCount = 3,
                        takenCount = if (day % 2 == 0) 3 else 2
                    )
                }
            )

            val jsonString = json.encodeToString(fullWeek)
            val deserialized = json.decodeFromString<WeeklyAdherence>(jsonString)

            deserialized.days.size shouldBe 7
            deserialized shouldBe fullWeek
        }
    }

    context("WeeklyAdherence data classes") {
        test("should support equality comparison for DayAdherence") {
            val day1 = DayAdherence("2026-01-09", "Thursday", 9, 1, AdherenceStatus.COMPLETE, 3, 3)
            val day2 = DayAdherence("2026-01-09", "Thursday", 9, 1, AdherenceStatus.COMPLETE, 3, 3)
            val day3 = DayAdherence("2026-01-09", "Thursday", 9, 1, AdherenceStatus.PARTIAL, 3, 2)

            day1 shouldBe day2
            day1 shouldNotBe day3
        }

        test("should support copy for DayAdherence") {
            val original = DayAdherence("2026-01-09", "Thursday", 9, 1, AdherenceStatus.PARTIAL, 3, 2)
            val modified = original.copy(status = AdherenceStatus.COMPLETE, takenCount = 3)

            modified.date shouldBe original.date
            modified.dayOfWeek shouldBe original.dayOfWeek
            modified.status shouldBe AdherenceStatus.COMPLETE
            modified.takenCount shouldBe 3
        }

        test("should support equality comparison for WeeklyAdherence") {
            val days = listOf(
                DayAdherence("2026-01-06", "Monday", 6, 1, AdherenceStatus.COMPLETE, 3, 3)
            )
            val week1 = WeeklyAdherence(days)
            val week2 = WeeklyAdherence(days)
            val week3 = WeeklyAdherence(emptyList())

            week1 shouldBe week2
            week1 shouldNotBe week3
        }

        test("should calculate adherence metrics correctly") {
            val days = listOf(
                DayAdherence("2026-01-06", "Monday", 6, 1, AdherenceStatus.COMPLETE, 3, 3),
                DayAdherence("2026-01-07", "Tuesday", 7, 1, AdherenceStatus.PARTIAL, 3, 2),
                DayAdherence("2026-01-08", "Wednesday", 8, 1, AdherenceStatus.NONE, 3, 0),
                DayAdherence("2026-01-09", "Thursday", 9, 1, AdherenceStatus.COMPLETE, 3, 3)
            )

            val totalExpected = days.sumOf { it.expectedCount }
            val totalTaken = days.sumOf { it.takenCount }

            totalExpected shouldBe 12
            totalTaken shouldBe 8
        }
    }
})
