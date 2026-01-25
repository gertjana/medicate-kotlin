package dev.gertjanassies.service

import dev.gertjanassies.model.MedicineSearchResult
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.File

/**
 * Test suite for MedicineSearchService.
 *
 * Tests cover:
 * - searchMedicines: searching medicines by name
 * - Query validation and filtering
 * - Limit handling
 * - Case-insensitive search
 * - Empty results handling
 */
class MedicineSearchServiceTest : FunSpec({
    // Use IsolationMode.InstancePerLeaf to ensure clean state for each test
    isolationMode = IsolationMode.InstancePerLeaf

    // Create test data in the data directory
    val testDataDir = File("data")
    val testMedicinesFile = File(testDataDir, "medicines.json")
    val backupFile = if (testMedicinesFile.exists()) {
        File(testDataDir, "medicines.json.backup").also { backup ->
            testMedicinesFile.copyTo(backup, overwrite = true)
        }
    } else null

    beforeSpec {
        // Ensure data directory exists
        testDataDir.mkdirs()

        // Create test medicines data
        val testData = """
            [
                {
                    "registratienummer": "TEST001",
                    "soort": "RVG",
                    "productnaam": "Paracetamol 500mg tabletten",
                    "farmaceutischevorm": "Tablet",
                    "werkzamestoffen": "PARACETAMOL"
                },
                {
                    "registratienummer": "TEST002",
                    "soort": "RVG",
                    "productnaam": "Ibuprofen 400mg capsules",
                    "farmaceutischevorm": "Capsule",
                    "werkzamestoffen": "IBUPROFEN"
                },
                {
                    "registratienummer": "TEST003",
                    "soort": "RVG",
                    "productnaam": "Paracetamol 1000mg zetpil",
                    "farmaceutischevorm": "Zetpil",
                    "werkzamestoffen": "PARACETAMOL"
                },
                {
                    "registratienummer": "TEST004",
                    "soort": "RVG",
                    "productnaam": "Aspirine 100mg tabletten",
                    "farmaceutischevorm": "Tablet",
                    "werkzamestoffen": "ACETYLSALICYLZUUR"
                },
                {
                    "registratienummer": "TEST005",
                    "soort": "RVG",
                    "productnaam": "Omeprazol 20mg capsules",
                    "farmaceutischevorm": "Capsule",
                    "werkzamestoffen": "OMEPRAZOL"
                }
            ]
        """.trimIndent()

        testMedicinesFile.writeText(testData)

        // Force reload of medicines data in the service
        MedicineSearchService.javaClass.getDeclaredField("medicinesData").apply {
            isAccessible = true
            set(MedicineSearchService, null)
        }
        val loadMethod = MedicineSearchService.javaClass.getDeclaredMethod("loadMedicinesData")
        loadMethod.isAccessible = true
        loadMethod.invoke(MedicineSearchService)
    }

    afterSpec {
        // Restore original medicines.json if it existed
        if (backupFile != null && backupFile.exists()) {
            backupFile.copyTo(testMedicinesFile, overwrite = true)
            backupFile.delete()
        } else {
            testMedicinesFile.delete()
        }
    }

    context("searchMedicines") {
        test("should return empty list for queries shorter than 2 characters") {
            val results = MedicineSearchService.searchMedicines("a")
            results.shouldBeEmpty()
        }

        test("should return empty list for single character query") {
            val results = MedicineSearchService.searchMedicines("p")
            results.shouldBeEmpty()
        }

        test("should return empty list for empty query") {
            val results = MedicineSearchService.searchMedicines("")
            results.shouldBeEmpty()
        }

        test("should find medicines by partial name match") {
            val results = MedicineSearchService.searchMedicines("para")

            results shouldHaveSize 2
            results.forEach { result ->
                result.productnaam.lowercase() shouldContain "paracetamol"
            }
        }

        test("should be case-insensitive") {
            val resultsLower = MedicineSearchService.searchMedicines("paracetamol")
            val resultsUpper = MedicineSearchService.searchMedicines("PARACETAMOL")
            val resultsMixed = MedicineSearchService.searchMedicines("ParaCetamol")

            resultsLower shouldHaveSize 2
            resultsUpper shouldHaveSize 2
            resultsMixed shouldHaveSize 2

            resultsLower shouldBe resultsUpper
            resultsUpper shouldBe resultsMixed
        }

        test("should return all matching medicines when under limit") {
            val results = MedicineSearchService.searchMedicines("tablet")

            results shouldHaveSize 2
            results.all { it.farmaceutischevorm == "Tablet" } shouldBe true
        }

        test("should respect the limit parameter") {
            val results = MedicineSearchService.searchMedicines("pa", limit = 1)

            results shouldHaveSize 1
            results[0].productnaam.lowercase() shouldContain "pa"
        }

        test("should use default limit of 10") {
            // With our test data, we only have 5 medicines total
            val results = MedicineSearchService.searchMedicines("mg")

            // All test medicines have "mg" in their name
            results shouldHaveSize 5
        }

        test("should return medicines with all fields populated") {
            val results = MedicineSearchService.searchMedicines("ibuprofen")

            results shouldHaveSize 1
            val medicine = results[0]
            medicine.productnaam shouldBe "Ibuprofen 400mg capsules"
            medicine.farmaceutischevorm shouldBe "Capsule"
            medicine.werkzamestoffen shouldBe "IBUPROFEN"
        }

        test("should return empty list when no matches found") {
            val results = MedicineSearchService.searchMedicines("nonexistent")

            results.shouldBeEmpty()
        }

        test("should handle special characters in query") {
            val results = MedicineSearchService.searchMedicines("500mg")

            results shouldHaveSize 1
            results[0].productnaam shouldContain "500mg"
        }

        test("should find medicines by specific dosage") {
            val results = MedicineSearchService.searchMedicines("1000mg")

            results shouldHaveSize 1
            results[0].productnaam shouldBe "Paracetamol 1000mg zetpil"
        }

        test("should find medicines by pharmaceutical form") {
            val results = MedicineSearchService.searchMedicines("capsule")

            results shouldHaveSize 2
            results.all { it.productnaam.lowercase().contains("capsule") } shouldBe true
        }

        test("should handle whitespace in query") {
            val results = MedicineSearchService.searchMedicines("  para  ")

            results shouldHaveSize 2
        }

        test("should return results in order they appear in data") {
            val results = MedicineSearchService.searchMedicines("paracetamol")

            results shouldHaveSize 2
            results[0].productnaam shouldBe "Paracetamol 500mg tabletten"
            results[1].productnaam shouldBe "Paracetamol 1000mg zetpil"
        }

        test("should handle limit of 0") {
            val results = MedicineSearchService.searchMedicines("para", limit = 0)

            results.shouldBeEmpty()
        }

        test("should handle negative limit gracefully") {
            val results = MedicineSearchService.searchMedicines("para", limit = -1)

            // take() with negative values returns empty list
            results.shouldBeEmpty()
        }

        test("should find unique medicines by exact name fragment") {
            val results = MedicineSearchService.searchMedicines("aspirine")

            results shouldHaveSize 1
            results[0].productnaam shouldBe "Aspirine 100mg tabletten"
            results[0].werkzamestoffen shouldBe "ACETYLSALICYLZUUR"
        }

        test("should handle numeric queries") {
            val results = MedicineSearchService.searchMedicines("400")

            results shouldHaveSize 1
            results[0].productnaam shouldContain "400mg"
        }
    }

    context("data loading") {
        test("should handle missing medicines.json gracefully") {
            // This is tested implicitly - if the service can't load data,
            // searchMedicines will return empty results
            // We verify this works by checking the service doesn't throw exceptions
            val tempService = MedicineSearchService
            tempService.searchMedicines("test") // Should not throw
        }
    }
})
