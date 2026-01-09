package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.UUID

class DosageHistoryTest : FunSpec({

    val json = Json { prettyPrint = false }

    context("DosageHistory serialization") {
        test("should serialize DosageHistory to JSON with all fields") {
            val dosageId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val medicineId = UUID.fromString("660e8400-e29b-41d4-a716-446655440000")
            val datetime = LocalDateTime.of(2026, 1, 9, 8, 30, 0)

            val dosage = DosageHistory(
                id = dosageId,
                datetime = datetime,
                medicineId = medicineId,
                amount = 500.0,
                scheduledTime = "08:00"
            )

            val jsonString = json.encodeToString(dosage)

            jsonString shouldNotBe null
            jsonString.contains("550e8400-e29b-41d4-a716-446655440000") shouldBe true
            jsonString.contains("660e8400-e29b-41d4-a716-446655440000") shouldBe true
            jsonString.contains("2026-01-09T08:30:00") shouldBe true
            jsonString.contains("500.0") shouldBe true
            jsonString.contains("08:00") shouldBe true
        }

        test("should serialize DosageHistory without optional scheduledTime") {
            val dosage = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.now(),
                medicineId = UUID.randomUUID(),
                amount = 100.0,
                scheduledTime = null
            )

            val jsonString = json.encodeToString(dosage)
            val deserialized = json.decodeFromString<DosageHistory>(jsonString)

            deserialized.scheduledTime shouldBe null
        }

        test("should deserialize JSON to DosageHistory") {
            val jsonString = """{"id":"550e8400-e29b-41d4-a716-446655440000","datetime":"2026-01-09T08:30:00","medicineId":"660e8400-e29b-41d4-a716-446655440000","amount":250.0,"scheduledTime":"08:00"}"""

            val dosage = json.decodeFromString<DosageHistory>(jsonString)

            dosage.id shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            dosage.datetime shouldBe LocalDateTime.of(2026, 1, 9, 8, 30, 0)
            dosage.medicineId shouldBe UUID.fromString("660e8400-e29b-41d4-a716-446655440000")
            dosage.amount shouldBe 250.0
            dosage.scheduledTime shouldBe "08:00"
        }

        test("should round-trip serialize and deserialize DosageHistory") {
            val original = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.of(2026, 1, 9, 14, 15, 30),
                medicineId = UUID.randomUUID(),
                amount = 750.0,
                scheduledTime = "14:00"
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<DosageHistory>(jsonString)

            deserialized shouldBe original
        }

        test("should handle datetime precision in serialization") {
            val datetime = LocalDateTime.of(2026, 1, 9, 23, 59, 59)
            val dosage = DosageHistory(
                id = UUID.randomUUID(),
                datetime = datetime,
                medicineId = UUID.randomUUID(),
                amount = 100.0
            )

            val jsonString = json.encodeToString(dosage)
            val deserialized = json.decodeFromString<DosageHistory>(jsonString)

            deserialized.datetime shouldBe datetime
        }
    }

    context("DosageHistory data class") {
        test("should create DosageHistory with all properties") {
            val dosageId = UUID.randomUUID()
            val medicineId = UUID.randomUUID()
            val datetime = LocalDateTime.now()

            val dosage = DosageHistory(
                id = dosageId,
                datetime = datetime,
                medicineId = medicineId,
                amount = 500.0,
                scheduledTime = "12:00"
            )

            dosage.id shouldBe dosageId
            dosage.datetime shouldBe datetime
            dosage.medicineId shouldBe medicineId
            dosage.amount shouldBe 500.0
            dosage.scheduledTime shouldBe "12:00"
        }

        test("should support copy with modified properties") {
            val original = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.now(),
                medicineId = UUID.randomUUID(),
                amount = 100.0,
                scheduledTime = "08:00"
            )

            val modified = original.copy(amount = 200.0, scheduledTime = "20:00")

            modified.id shouldBe original.id
            modified.datetime shouldBe original.datetime
            modified.medicineId shouldBe original.medicineId
            modified.amount shouldBe 200.0
            modified.scheduledTime shouldBe "20:00"
        }

        test("should support equality comparison") {
            val id = UUID.randomUUID()
            val datetime = LocalDateTime.of(2026, 1, 9, 12, 0)
            val medicineId = UUID.randomUUID()

            val dosage1 = DosageHistory(id, datetime, medicineId, 100.0, "12:00")
            val dosage2 = DosageHistory(id, datetime, medicineId, 100.0, "12:00")
            val dosage3 = DosageHistory(UUID.randomUUID(), datetime, medicineId, 100.0, "12:00")

            dosage1 shouldBe dosage2
            dosage1 shouldNotBe dosage3
        }
    }
})
