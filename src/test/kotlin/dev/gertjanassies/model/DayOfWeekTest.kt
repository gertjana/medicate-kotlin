package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DayOfWeekTest : FunSpec({

    val json = Json { prettyPrint = false }

    context("DayOfWeek enum") {
        test("should have correct code for each day") {
            DayOfWeek.MONDAY.code shouldBe "MO"
            DayOfWeek.TUESDAY.code shouldBe "TU"
            DayOfWeek.WEDNESDAY.code shouldBe "WE"
            DayOfWeek.THURSDAY.code shouldBe "TH"
            DayOfWeek.FRIDAY.code shouldBe "FR"
            DayOfWeek.SATURDAY.code shouldBe "SA"
            DayOfWeek.SUNDAY.code shouldBe "SU"
        }

        test("should contain all 7 days") {
            DayOfWeek.entries.size shouldBe 7
        }

        test("should convert from code correctly") {
            DayOfWeek.fromCode("MO") shouldBe DayOfWeek.MONDAY
            DayOfWeek.fromCode("TU") shouldBe DayOfWeek.TUESDAY
            DayOfWeek.fromCode("WE") shouldBe DayOfWeek.WEDNESDAY
            DayOfWeek.fromCode("TH") shouldBe DayOfWeek.THURSDAY
            DayOfWeek.fromCode("FR") shouldBe DayOfWeek.FRIDAY
            DayOfWeek.fromCode("SA") shouldBe DayOfWeek.SATURDAY
            DayOfWeek.fromCode("SU") shouldBe DayOfWeek.SUNDAY
        }

        test("should handle lowercase codes in fromCode") {
            DayOfWeek.fromCode("mo") shouldBe DayOfWeek.MONDAY
            DayOfWeek.fromCode("tu") shouldBe DayOfWeek.TUESDAY
            DayOfWeek.fromCode("we") shouldBe DayOfWeek.WEDNESDAY
        }

        test("should return null for invalid code") {
            DayOfWeek.fromCode("XX") shouldBe null
            DayOfWeek.fromCode("") shouldBe null
            DayOfWeek.fromCode("MONDAY") shouldBe null
        }

        test("should convert from Java DayOfWeek correctly") {
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.MONDAY) shouldBe DayOfWeek.MONDAY
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.TUESDAY) shouldBe DayOfWeek.TUESDAY
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.WEDNESDAY) shouldBe DayOfWeek.WEDNESDAY
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.THURSDAY) shouldBe DayOfWeek.THURSDAY
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.FRIDAY) shouldBe DayOfWeek.FRIDAY
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.SATURDAY) shouldBe DayOfWeek.SATURDAY
            DayOfWeek.fromJavaDay(java.time.DayOfWeek.SUNDAY) shouldBe DayOfWeek.SUNDAY
        }
    }

    context("DayOfWeek serialization") {
        test("should serialize DayOfWeek to JSON") {
            val day = DayOfWeek.MONDAY

            val jsonString = json.encodeToString(day)

            jsonString shouldBe "\"MONDAY\""
        }

        test("should deserialize JSON to DayOfWeek") {
            val jsonString = "\"WEDNESDAY\""

            val day = json.decodeFromString<DayOfWeek>(jsonString)

            day shouldBe DayOfWeek.WEDNESDAY
        }

        test("should round-trip serialize and deserialize all days") {
            DayOfWeek.entries.forEach { original ->
                val jsonString = json.encodeToString(original)
                val deserialized = json.decodeFromString<DayOfWeek>(jsonString)
                deserialized shouldBe original
            }
        }

        test("should serialize list of DayOfWeek") {
            val days = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)

            val jsonString = json.encodeToString(days)
            val deserialized = json.decodeFromString<List<DayOfWeek>>(jsonString)

            deserialized shouldBe days
        }
    }

    context("DayOfWeek equality and comparison") {
        test("should support equality") {
            val day1 = DayOfWeek.MONDAY
            val day2 = DayOfWeek.MONDAY
            val day3 = DayOfWeek.TUESDAY

            day1 shouldBe day2
            day1 shouldNotBe day3
        }

        test("should maintain ordinal order") {
            DayOfWeek.MONDAY.ordinal shouldBe 0
            DayOfWeek.SUNDAY.ordinal shouldBe 6
            DayOfWeek.FRIDAY.ordinal shouldBe 4
        }
    }
})
