package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class MedicineTest : FunSpec({

    val json = Json { prettyPrint = false }

    context("Medicine serialization") {
        test("should serialize Medicine to JSON") {
            val medicineId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )

            val jsonString = json.encodeToString(medicine)

            jsonString shouldNotBe null
            jsonString.contains("550e8400-e29b-41d4-a716-446655440000") shouldBe true
            jsonString.contains("Aspirin") shouldBe true
            jsonString.contains("500.0") shouldBe true
            jsonString.contains("mg") shouldBe true
            jsonString.contains("100.0") shouldBe true
        }

        test("should serialize Medicine with description to JSON") {
            val medicineId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0,
                description = "Pain reliever and fever reducer"
            )

            val jsonString = json.encodeToString(medicine)

            jsonString shouldNotBe null
            jsonString.contains("Pain reliever and fever reducer") shouldBe true
        }

        test("should deserialize JSON to Medicine") {
            val jsonString = """{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Ibuprofen","dose":200.0,"unit":"mg","stock":50.0}"""

            val medicine = json.decodeFromString<Medicine>(jsonString)

            medicine.id shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            medicine.name shouldBe "Ibuprofen"
            medicine.dose shouldBe 200.0
            medicine.unit shouldBe "mg"
            medicine.stock shouldBe 50.0
            medicine.description shouldBe null
        }

        test("should deserialize JSON with description to Medicine") {
            val jsonString = """{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Ibuprofen","dose":200.0,"unit":"mg","stock":50.0,"description":"Anti-inflammatory"}"""

            val medicine = json.decodeFromString<Medicine>(jsonString)

            medicine.id shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            medicine.name shouldBe "Ibuprofen"
            medicine.dose shouldBe 200.0
            medicine.unit shouldBe "mg"
            medicine.stock shouldBe 50.0
            medicine.description shouldBe "Anti-inflammatory"
        }

        test("should round-trip serialize and deserialize Medicine") {
            val original = Medicine(
                id = UUID.randomUUID(),
                name = "Paracetamol",
                dose = 1000.0,
                unit = "mg",
                stock = 75.5
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<Medicine>(jsonString)

            deserialized shouldBe original
        }

        test("should round-trip serialize and deserialize Medicine with description") {
            val original = Medicine(
                id = UUID.randomUUID(),
                name = "Paracetamol",
                dose = 1000.0,
                unit = "mg",
                stock = 75.5,
                description = "Acetaminophen for pain and fever"
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<Medicine>(jsonString)

            deserialized shouldBe original
        }
    }

    context("Medicine data class") {
        test("should create Medicine with all properties") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Vitamin C",
                dose = 1000.0,
                unit = "mg",
                stock = 200.0
            )

            medicine.id shouldBe medicineId
            medicine.name shouldBe "Vitamin C"
            medicine.dose shouldBe 1000.0
            medicine.unit shouldBe "mg"
            medicine.stock shouldBe 200.0
            medicine.description shouldBe null
        }

        test("should create Medicine with description") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Vitamin C",
                dose = 1000.0,
                unit = "mg",
                stock = 200.0,
                description = "Essential vitamin supplement"
            )

            medicine.id shouldBe medicineId
            medicine.name shouldBe "Vitamin C"
            medicine.dose shouldBe 1000.0
            medicine.unit shouldBe "mg"
            medicine.stock shouldBe 200.0
            medicine.description shouldBe "Essential vitamin supplement"
        }

        test("should support copy with modified properties") {
            val original = Medicine(
                id = UUID.randomUUID(),
                name = "Original",
                dose = 100.0,
                unit = "mg",
                stock = 50.0
            )

            val modified = original.copy(stock = 75.0, description = "Updated description")

            modified.id shouldBe original.id
            modified.name shouldBe original.name
            modified.dose shouldBe original.dose
            modified.unit shouldBe original.unit
            modified.stock shouldBe 75.0
            modified.description shouldBe "Updated description"
        }

        test("should support equality comparison") {
            val id = UUID.randomUUID()
            val medicine1 = Medicine(id, "Test", 100.0, "mg", 50.0)
            val medicine2 = Medicine(id, "Test", 100.0, "mg", 50.0)
            val medicine3 = Medicine(UUID.randomUUID(), "Test", 100.0, "mg", 50.0)

            medicine1 shouldBe medicine2
            medicine1 shouldNotBe medicine3
        }
    }
})
