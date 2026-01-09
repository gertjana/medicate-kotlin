package dev.gertjanassies.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class DailyScheduleTest : FunSpec({

    val json = Json { prettyPrint = false }

    context("MedicineScheduleItem serialization") {
        test("should serialize MedicineScheduleItem to JSON") {
            val medicine = Medicine(
                id = UUID.fromString("550e8400-e29b-41d4-a716-446655440000"),
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val item = MedicineScheduleItem(
                medicine = medicine,
                amount = 2.0
            )

            val jsonString = json.encodeToString(item)

            jsonString shouldNotBe null
            jsonString.contains("550e8400-e29b-41d4-a716-446655440000") shouldBe true
            jsonString.contains("Aspirin") shouldBe true
            jsonString.contains("2.0") shouldBe true
        }

        test("should deserialize JSON to MedicineScheduleItem") {
            val jsonString = """{"medicine":{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Ibuprofen","dose":200.0,"unit":"mg","stock":50.0},"amount":1.0}"""

            val item = json.decodeFromString<MedicineScheduleItem>(jsonString)

            item.medicine.id shouldBe UUID.fromString("550e8400-e29b-41d4-a716-446655440000")
            item.medicine.name shouldBe "Ibuprofen"
            item.amount shouldBe 1.0
        }

        test("should round-trip serialize and deserialize MedicineScheduleItem") {
            val medicine = Medicine(
                id = UUID.randomUUID(),
                name = "Paracetamol",
                dose = 1000.0,
                unit = "mg",
                stock = 75.0
            )
            val original = MedicineScheduleItem(medicine = medicine, amount = 3.0)

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<MedicineScheduleItem>(jsonString)

            deserialized shouldBe original
        }
    }

    context("TimeSlot serialization") {
        test("should serialize TimeSlot to JSON") {
            val medicine1 = Medicine(UUID.randomUUID(), "Medicine1", 100.0, "mg", 50.0)
            val medicine2 = Medicine(UUID.randomUUID(), "Medicine2", 200.0, "mg", 75.0)

            val timeSlot = TimeSlot(
                time = "08:00",
                medicines = listOf(
                    MedicineScheduleItem(medicine1, 1.0),
                    MedicineScheduleItem(medicine2, 2.0)
                )
            )

            val jsonString = json.encodeToString(timeSlot)

            jsonString shouldNotBe null
            jsonString.contains("08:00") shouldBe true
            jsonString.contains("Medicine1") shouldBe true
            jsonString.contains("Medicine2") shouldBe true
        }

        test("should deserialize JSON to TimeSlot") {
            val jsonString = """{"time":"12:00","medicines":[{"medicine":{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Test","dose":100.0,"unit":"mg","stock":10.0},"amount":1.0}]}"""

            val timeSlot = json.decodeFromString<TimeSlot>(jsonString)

            timeSlot.time shouldBe "12:00"
            timeSlot.medicines.size shouldBe 1
            timeSlot.medicines[0].medicine.name shouldBe "Test"
        }

        test("should handle empty medicines list") {
            val timeSlot = TimeSlot(time = "14:00", medicines = emptyList())

            val jsonString = json.encodeToString(timeSlot)
            val deserialized = json.decodeFromString<TimeSlot>(jsonString)

            deserialized.medicines shouldBe emptyList()
        }

        test("should round-trip serialize and deserialize TimeSlot") {
            val medicine = Medicine(UUID.randomUUID(), "Test", 100.0, "mg", 50.0)
            val original = TimeSlot(
                time = "20:00",
                medicines = listOf(MedicineScheduleItem(medicine, 2.0))
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<TimeSlot>(jsonString)

            deserialized shouldBe original
        }
    }

    context("DailySchedule serialization") {
        test("should serialize DailySchedule to JSON") {
            val medicine = Medicine(UUID.randomUUID(), "Aspirin", 500.0, "mg", 100.0)
            val timeSlot1 = TimeSlot("08:00", listOf(MedicineScheduleItem(medicine, 1.0)))
            val timeSlot2 = TimeSlot("20:00", listOf(MedicineScheduleItem(medicine, 1.0)))

            val dailySchedule = DailySchedule(
                schedule = listOf(timeSlot1, timeSlot2)
            )

            val jsonString = json.encodeToString(dailySchedule)

            jsonString shouldNotBe null
            jsonString.contains("08:00") shouldBe true
            jsonString.contains("20:00") shouldBe true
            jsonString.contains("Aspirin") shouldBe true
        }

        test("should deserialize JSON to DailySchedule") {
            val jsonString = """{"schedule":[{"time":"08:00","medicines":[{"medicine":{"id":"550e8400-e29b-41d4-a716-446655440000","name":"Test","dose":100.0,"unit":"mg","stock":10.0},"amount":1.0}]}]}"""

            val dailySchedule = json.decodeFromString<DailySchedule>(jsonString)

            dailySchedule.schedule.size shouldBe 1
            dailySchedule.schedule[0].time shouldBe "08:00"
        }

        test("should handle empty schedule") {
            val dailySchedule = DailySchedule(schedule = emptyList())

            val jsonString = json.encodeToString(dailySchedule)
            val deserialized = json.decodeFromString<DailySchedule>(jsonString)

            deserialized.schedule shouldBe emptyList()
        }

        test("should round-trip serialize and deserialize DailySchedule") {
            val medicine1 = Medicine(UUID.randomUUID(), "Med1", 100.0, "mg", 50.0)
            val medicine2 = Medicine(UUID.randomUUID(), "Med2", 200.0, "mg", 75.0)

            val original = DailySchedule(
                schedule = listOf(
                    TimeSlot("08:00", listOf(MedicineScheduleItem(medicine1, 1.0))),
                    TimeSlot("12:00", listOf(MedicineScheduleItem(medicine2, 2.0))),
                    TimeSlot("20:00", listOf(
                        MedicineScheduleItem(medicine1, 1.0),
                        MedicineScheduleItem(medicine2, 1.0)
                    ))
                )
            )

            val jsonString = json.encodeToString(original)
            val deserialized = json.decodeFromString<DailySchedule>(jsonString)

            deserialized shouldBe original
        }

        test("should serialize complex DailySchedule with multiple medicines per slot") {
            val medicine1 = Medicine(UUID.randomUUID(), "Morning Med", 100.0, "mg", 50.0)
            val medicine2 = Medicine(UUID.randomUUID(), "Afternoon Med", 200.0, "mg", 75.0)
            val medicine3 = Medicine(UUID.randomUUID(), "Evening Med", 150.0, "mg", 60.0)

            val dailySchedule = DailySchedule(
                schedule = listOf(
                    TimeSlot("06:00", listOf(MedicineScheduleItem(medicine1, 1.0))),
                    TimeSlot("12:00", listOf(
                        MedicineScheduleItem(medicine1, 1.0),
                        MedicineScheduleItem(medicine2, 2.0)
                    )),
                    TimeSlot("18:00", listOf(MedicineScheduleItem(medicine3, 1.0))),
                    TimeSlot("22:00", listOf(
                        MedicineScheduleItem(medicine2, 1.0),
                        MedicineScheduleItem(medicine3, 1.0)
                    ))
                )
            )

            val jsonString = json.encodeToString(dailySchedule)
            val deserialized = json.decodeFromString<DailySchedule>(jsonString)

            deserialized.schedule.size shouldBe 4
            deserialized.schedule[1].medicines.size shouldBe 2
            deserialized.schedule[3].medicines.size shouldBe 2
        }
    }

    context("DailySchedule data classes") {
        test("should support equality comparison for MedicineScheduleItem") {
            val medicine = Medicine(UUID.randomUUID(), "Test", 100.0, "mg", 50.0)
            val item1 = MedicineScheduleItem(medicine, 1.0)
            val item2 = MedicineScheduleItem(medicine, 1.0)
            val item3 = MedicineScheduleItem(medicine, 2.0)

            item1 shouldBe item2
            item1 shouldNotBe item3
        }

        test("should support copy for MedicineScheduleItem") {
            val medicine = Medicine(UUID.randomUUID(), "Test", 100.0, "mg", 50.0)
            val original = MedicineScheduleItem(medicine, 1.0)
            val modified = original.copy(amount = 3.0)

            modified.medicine shouldBe original.medicine
            modified.amount shouldBe 3.0
        }

        test("should support equality comparison for TimeSlot") {
            val medicine = Medicine(UUID.randomUUID(), "Test", 100.0, "mg", 50.0)
            val medicines = listOf(MedicineScheduleItem(medicine, 1.0))

            val slot1 = TimeSlot("08:00", medicines)
            val slot2 = TimeSlot("08:00", medicines)
            val slot3 = TimeSlot("20:00", medicines)

            slot1 shouldBe slot2
            slot1 shouldNotBe slot3
        }

        test("should support equality comparison for DailySchedule") {
            val medicine = Medicine(UUID.randomUUID(), "Test", 100.0, "mg", 50.0)
            val schedule = listOf(TimeSlot("08:00", listOf(MedicineScheduleItem(medicine, 1.0))))

            val daily1 = DailySchedule(schedule)
            val daily2 = DailySchedule(schedule)
            val daily3 = DailySchedule(emptyList())

            daily1 shouldBe daily2
            daily1 shouldNotBe daily3
        }
    }
})
