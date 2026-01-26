package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Test suite for MedicineSearchResult data class.
 *
 * Tests cover:
 * - Serialization to JSON
 * - Deserialization from JSON
 * - Data class properties
 */
class MedicineSearchResultTest : FunSpec({
    val json = Json { ignoreUnknownKeys = true }

    context("serialization") {
        test("should serialize to JSON correctly") {
            val result = MedicineSearchResult(
                productnaam = "Paracetamol 500mg tabletten",
                farmaceutischevorm = "Tablet",
                werkzamestoffen = "PARACETAMOL"
            )

            val jsonString = json.encodeToString(result)

            jsonString shouldBe """{"productnaam":"Paracetamol 500mg tabletten","farmaceutischevorm":"Tablet","werkzamestoffen":"PARACETAMOL"}"""
        }

        test("should deserialize from JSON correctly") {
            val jsonString = """{"productnaam":"Ibuprofen 400mg capsules","farmaceutischevorm":"Capsule","werkzamestoffen":"IBUPROFEN"}"""

            val result = json.decodeFromString<MedicineSearchResult>(jsonString)

            result.productnaam shouldBe "Ibuprofen 400mg capsules"
            result.farmaceutischevorm shouldBe "Capsule"
            result.werkzamestoffen shouldBe "IBUPROFEN"
        }

        test("should handle empty strings") {
            val result = MedicineSearchResult(
                productnaam = "Test Medicine",
                farmaceutischevorm = "",
                werkzamestoffen = ""
            )

            val jsonString = json.encodeToString(result)
            val deserialized = json.decodeFromString<MedicineSearchResult>(jsonString)

            deserialized.productnaam shouldBe "Test Medicine"
            deserialized.farmaceutischevorm shouldBe ""
            deserialized.werkzamestoffen shouldBe ""
        }

        test("should handle special characters in Dutch medicine names") {
            val result = MedicineSearchResult(
                productnaam = "Naproxen-natriumsesquihydraat 550mg tabletten",
                farmaceutischevorm = "Tablet, omhulde",
                werkzamestoffen = "NAPROXEN-NATRIUMSESQUIHYDRAAT"
            )

            val jsonString = json.encodeToString(result)
            val deserialized = json.decodeFromString<MedicineSearchResult>(jsonString)

            deserialized shouldBe result
        }
    }

    context("data class properties") {
        test("should create instance with all properties") {
            val result = MedicineSearchResult(
                productnaam = "Aspirine 100mg tabletten",
                farmaceutischevorm = "Tablet",
                werkzamestoffen = "ACETYLSALICYLZUUR"
            )

            result.productnaam shouldBe "Aspirine 100mg tabletten"
            result.farmaceutischevorm shouldBe "Tablet"
            result.werkzamestoffen shouldBe "ACETYLSALICYLZUUR"
        }

        test("should support copy with modifications") {
            val original = MedicineSearchResult(
                productnaam = "Original Name",
                farmaceutischevorm = "Original Form",
                werkzamestoffen = "Original Ingredients"
            )

            val modified = original.copy(productnaam = "Modified Name")

            modified.productnaam shouldBe "Modified Name"
            modified.farmaceutischevorm shouldBe "Original Form"
            modified.werkzamestoffen shouldBe "Original Ingredients"
        }

        test("should support equality comparison") {
            val result1 = MedicineSearchResult(
                productnaam = "Test",
                farmaceutischevorm = "Form",
                werkzamestoffen = "Ingredient"
            )

            val result2 = MedicineSearchResult(
                productnaam = "Test",
                farmaceutischevorm = "Form",
                werkzamestoffen = "Ingredient"
            )

            result1 shouldBe result2
        }
    }

    context("edge cases") {
        test("should handle very long medicine names") {
            val longName = "A".repeat(500)
            val result = MedicineSearchResult(
                productnaam = longName,
                farmaceutischevorm = "Tablet",
                werkzamestoffen = "TEST"
            )

            result.productnaam shouldBe longName
        }

        test("should handle multiple active ingredients separated by hash") {
            val result = MedicineSearchResult(
                productnaam = "Combination Medicine",
                farmaceutischevorm = "Tablet",
                werkzamestoffen = "INGREDIENT1 #INGREDIENT2 #INGREDIENT3"
            )

            result.werkzamestoffen shouldBe "INGREDIENT1 #INGREDIENT2 #INGREDIENT3"
        }

        test("should preserve exact casing of Dutch pharmaceutical terms") {
            val result = MedicineSearchResult(
                productnaam = "Test",
                farmaceutischevorm = "Capsule, maagsapresistent",
                werkzamestoffen = "TEST"
            )

            result.farmaceutischevorm shouldBe "Capsule, maagsapresistent"
        }
    }
})
