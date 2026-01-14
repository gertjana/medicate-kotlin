package dev.gertjanassies.service

import dev.gertjanassies.model.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Test suite for Adherence and Analytics operations in RedisService.
 *
 * Tests cover:
 * - getWeeklyAdherence: Weekly adherence calculation with proper day filtering
 * - getLowStockMedicines: Filtering medicines below stock threshold
 */
class AdherenceServiceTest : FunSpec({

    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"
    val testUsername = "testuser"

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(host = "localhost", port = 6379, environment = environment)

        val connectionField = RedisService::class.java.getDeclaredField("connection")
        connectionField.isAccessible = true
        connectionField.set(redisService, mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    class TestRedisFuture<T>(completableFuture: CompletableFuture<T>) :
        CompletableFuture<T>(), RedisFuture<T> {
        init {
            completableFuture.whenComplete { result, exception ->
                if (exception != null) {
                    completeExceptionally(exception)
                } else {
                    complete(result)
                }
            }
        }

        override fun getError(): String? = null

        override fun await(timeout: Long, unit: java.util.concurrent.TimeUnit): Boolean {
            return try {
                get(timeout, unit)
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    fun <T> createRedisFutureMock(value: T): RedisFuture<T> {
        return TestRedisFuture(CompletableFuture.completedFuture(value))
    }

    fun <T> createFailedRedisFutureMock(exception: Exception): RedisFuture<T> {
        val future = CompletableFuture<T>()
        future.completeExceptionally(exception)
        return TestRedisFuture(future)
    }

    context("getWeeklyAdherence") {
        test("should calculate weekly adherence with all medications taken") {
            val medicineId = UUID.randomUUID()
            val scheduleId = UUID.randomUUID()

            // Current date is Jan 14, 2026
            // getWeeklyAdherence looks at yesterday (Jan 13) back to 7 days ago (Jan 7)
            val currentDate = LocalDate.of(2026, 1, 14)
            val yesterday = currentDate.minusDays(1) // Jan 13

            // Create a schedule for every day
            val schedule = Schedule(
                id = scheduleId,
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList() // Every day
            )

            // Create dosage histories for Jan 13 to Jan 7 (7 days)
            val dosageHistories = (0..6).map { daysAgo ->
                DosageHistory(
                    id = UUID.randomUUID(),
                    datetime = yesterday.minusDays(daysAgo.toLong()).atTime(8, 0),
                    medicineId = medicineId,
                    amount = 1.0,
                    scheduledTime = "08:00"
                )
            }

            every { mockConnection.async() } returns mockAsyncCommands

            // Mock schedule scan
            val scheduleScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            val scheduleKey = "$environment:user:$testUsername:schedule:${schedule.id}"
            every { scheduleScanCursor.keys } returns listOf(scheduleKey)
            every { scheduleScanCursor.isFinished } returns true

            // Mock dosage history scan
            val dosageScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            val dosageKeys = dosageHistories.map { "$environment:user:$testUsername:dosagehistory:${it.id}" }
            every { dosageScanCursor.keys } returns dosageKeys
            every { dosageScanCursor.isFinished } returns true

            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(scheduleScanCursor) andThen
                createRedisFutureMock(dosageScanCursor)

            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(json.encodeToString(schedule))

            dosageHistories.forEachIndexed { index, dosage ->
                every { mockAsyncCommands.get(dosageKeys[index]) } returns createRedisFutureMock(json.encodeToString(dosage))
            }

            val result = redisService.getWeeklyAdherence(testUsername)

            result.isRight() shouldBe true
            val weeklyAdherence = result.getOrNull()!!
            weeklyAdherence.days.size shouldBe 7

            // All days should be complete
            weeklyAdherence.days.forEach { day ->
                day.expectedCount shouldBe 1
                day.takenCount shouldBe 1
                day.status shouldBe AdherenceStatus.COMPLETE
            }
        }

        test("should calculate partial adherence when some medications are missed") {
            val medicineId1 = UUID.randomUUID()
            val medicineId2 = UUID.randomUUID()

            val schedule1 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId1,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )

            val schedule2 = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId2,
                time = "20:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )

            // Only take medicine1 yesterday (not medicine2)
            val dosageHistory = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.now().minusDays(1),
                medicineId = medicineId1,
                amount = 1.0,
                scheduledTime = "08:00"
            )

            every { mockConnection.async() } returns mockAsyncCommands

            val scheduleScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            val scheduleKey1 = "$environment:user:$testUsername:schedule:${schedule1.id}"
            val scheduleKey2 = "$environment:user:$testUsername:schedule:${schedule2.id}"
            every { scheduleScanCursor.keys } returns listOf(scheduleKey1, scheduleKey2)
            every { scheduleScanCursor.isFinished } returns true

            val dosageScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            val dosageKey = "$environment:user:$testUsername:dosagehistory:${dosageHistory.id}"
            every { dosageScanCursor.keys } returns listOf(dosageKey)
            every { dosageScanCursor.isFinished } returns true

            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(scheduleScanCursor) andThen
                createRedisFutureMock(dosageScanCursor)

            every { mockAsyncCommands.get(scheduleKey1) } returns createRedisFutureMock(json.encodeToString(schedule1))
            every { mockAsyncCommands.get(scheduleKey2) } returns createRedisFutureMock(json.encodeToString(schedule2))
            every { mockAsyncCommands.get(dosageKey) } returns createRedisFutureMock(json.encodeToString(dosageHistory))

            val result = redisService.getWeeklyAdherence(testUsername)

            result.isRight() shouldBe true
            val weeklyAdherence = result.getOrNull()!!

            // The most recent day (yesterday) should be partial
            val yesterday = weeklyAdherence.days.last()
            yesterday.expectedCount shouldBe 2
            yesterday.takenCount shouldBe 1
            yesterday.status shouldBe AdherenceStatus.PARTIAL
        }

        test("should respect schedule days of week filtering") {
            val medicineId = UUID.randomUUID()

            // Schedule only for Monday, Wednesday, Friday
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicineId,
                time = "08:00",
                amount = 1.0,
                daysOfWeek = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            )

            every { mockConnection.async() } returns mockAsyncCommands

            val scheduleScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            val scheduleKey = "$environment:user:$testUsername:schedule:${schedule.id}"
            every { scheduleScanCursor.keys } returns listOf(scheduleKey)
            every { scheduleScanCursor.isFinished } returns true

            val dosageScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { dosageScanCursor.keys } returns emptyList()
            every { dosageScanCursor.isFinished } returns true

            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(scheduleScanCursor) andThen
                createRedisFutureMock(dosageScanCursor)

            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(json.encodeToString(schedule))

            val result = redisService.getWeeklyAdherence(testUsername)

            result.isRight() shouldBe true
            val weeklyAdherence = result.getOrNull()!!
            weeklyAdherence.days.size shouldBe 7

            // Only days that match Monday/Wednesday/Friday should have expected count > 0
            weeklyAdherence.days.forEach { day ->
                val javaDay = LocalDate.parse(day.date).dayOfWeek
                val dayOfWeek = DayOfWeek.fromJavaDay(javaDay)

                if (dayOfWeek in listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)) {
                    day.expectedCount shouldBe 1
                } else {
                    day.expectedCount shouldBe 0
                }
            }
        }

        test("should return NONE status when no medications taken") {
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = UUID.randomUUID(),
                time = "08:00",
                amount = 1.0,
                daysOfWeek = emptyList()
            )

            every { mockConnection.async() } returns mockAsyncCommands

            val scheduleScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            val scheduleKey = "$environment:user:$testUsername:schedule:${schedule.id}"
            every { scheduleScanCursor.keys } returns listOf(scheduleKey)
            every { scheduleScanCursor.isFinished } returns true

            val dosageScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { dosageScanCursor.keys } returns emptyList()
            every { dosageScanCursor.isFinished } returns true

            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns
                createRedisFutureMock(scheduleScanCursor) andThen
                createRedisFutureMock(dosageScanCursor)

            every { mockAsyncCommands.get(scheduleKey) } returns createRedisFutureMock(json.encodeToString(schedule))

            val result = redisService.getWeeklyAdherence(testUsername)

            result.isRight() shouldBe true
            val weeklyAdherence = result.getOrNull()!!

            // All days should have NONE status (0 taken, 1 expected)
            weeklyAdherence.days.forEach { day ->
                day.expectedCount shouldBe 1
                day.takenCount shouldBe 0
                day.status shouldBe AdherenceStatus.NONE
            }
        }
    }

    context("getLowStockMedicines") {
        test("should return medicines below default threshold of 10") {
            val lowStockMedicine1 = Medicine(
                id = UUID.randomUUID(),
                name = "Low Stock 1",
                dose = 100.0,
                unit = "mg",
                stock = 5.0
            )

            val lowStockMedicine2 = Medicine(
                id = UUID.randomUUID(),
                name = "Low Stock 2",
                dose = 200.0,
                unit = "mg",
                stock = 8.0
            )

            val normalStockMedicine = Medicine(
                id = UUID.randomUUID(),
                name = "Normal Stock",
                dose = 300.0,
                unit = "mg",
                stock = 50.0
            )

            val medicines = listOf(lowStockMedicine1, lowStockMedicine2, normalStockMedicine)
            val keys = medicines.map { "$environment:medicine:${it.id}" }

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns keys
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            medicines.forEachIndexed { index, medicine ->
                every { mockAsyncCommands.get(keys[index]) } returns createRedisFutureMock(json.encodeToString(medicine))
            }

            val result = redisService.getLowStockMedicines(testUsername)

            result.isRight() shouldBe true
            val lowStock = result.getOrNull()!!
            lowStock.size shouldBe 2
            lowStock.map { it.name } shouldBe listOf("Low Stock 1", "Low Stock 2")
        }

        test("should use custom threshold when provided") {
            val medicine1 = Medicine(
                id = UUID.randomUUID(),
                name = "Medicine 1",
                dose = 100.0,
                unit = "mg",
                stock = 15.0
            )

            val medicine2 = Medicine(
                id = UUID.randomUUID(),
                name = "Medicine 2",
                dose = 200.0,
                unit = "mg",
                stock = 25.0
            )

            val medicines = listOf(medicine1, medicine2)
            val keys = medicines.map { "$environment:medicine:${it.id}" }

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns keys
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            medicines.forEachIndexed { index, medicine ->
                every { mockAsyncCommands.get(keys[index]) } returns createRedisFutureMock(json.encodeToString(medicine))
            }

            val result = redisService.getLowStockMedicines(testUsername, threshold = 20.0)

            result.isRight() shouldBe true
            val lowStock = result.getOrNull()!!
            lowStock.size shouldBe 1
            lowStock[0].name shouldBe "Medicine 1"
            lowStock[0].stock shouldBe 15.0
        }

        test("should return empty list when all medicines have sufficient stock") {
            val medicine = Medicine(
                id = UUID.randomUUID(),
                name = "Well Stocked",
                dose = 100.0,
                unit = "mg",
                stock = 100.0
            )

            val key = "$environment:medicine:${medicine.id}"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(json.encodeToString(medicine))

            val result = redisService.getLowStockMedicines(testUsername)

            result.isRight() shouldBe true
            val lowStock = result.getOrNull()!!
            lowStock.size shouldBe 0
        }

        test("should handle zero stock medicines") {
            val outOfStock = Medicine(
                id = UUID.randomUUID(),
                name = "Out of Stock",
                dose = 100.0,
                unit = "mg",
                stock = 0.0
            )

            val key = "$environment:medicine:${outOfStock.id}"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(json.encodeToString(outOfStock))

            val result = redisService.getLowStockMedicines(testUsername)

            result.isRight() shouldBe true
            val lowStock = result.getOrNull()!!
            lowStock.size shouldBe 1
            lowStock[0].stock shouldBe 0.0
        }
    }

    context("medicineExpiry") {
        test("should calculate expiry date for daily schedule") {
            val today = LocalDate.of(2026, 1, 13)
            val medicine = Medicine(
                id = UUID.randomUUID(),
                name = "Aspirin",
                dose = 100.0,
                unit = "mg",
                stock = 10.0
            )
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicine.id,
                time = "08:00",
                amount = 2.0,
                daysOfWeek = emptyList() // every day
            )
            every { mockConnection.async() } returns mockAsyncCommands
            val medKey = "$environment:user:$testUsername:medicine:${medicine.id}"
            val schedKey = "$environment:user:$testUsername:schedule:${schedule.id}"
            val medScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { medScanCursor.keys } returns listOf(medKey)
            every { medScanCursor.isFinished } returns true
            val schedScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { schedScanCursor.keys } returns listOf(schedKey)
            every { schedScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(medScanCursor) andThen createRedisFutureMock(schedScanCursor)
            every { mockAsyncCommands.get(medKey) } returns createRedisFutureMock(json.encodeToString(medicine))
            every { mockAsyncCommands.get(schedKey) } returns createRedisFutureMock(json.encodeToString(schedule))
            val result = redisService.medicineExpiry(testUsername, today.atStartOfDay())
            result.isRight() shouldBe true
            val expiry = result.getOrNull()!!
            expiry.size shouldBe 1
            expiry[0].name shouldBe "Aspirin"
            expiry[0].expiryDate shouldBe today.plusDays(5).atStartOfDay() // 10/2 = 5 days left
        }
        test("should not include medicine with no schedule") {
            val medicine = Medicine(
                id = UUID.randomUUID(),
                name = "Ibuprofen",
                dose = 200.0,
                unit = "mg",
                stock = 20.0
            )
            every { mockConnection.async() } returns mockAsyncCommands
            val medKey = "$environment:user:$testUsername:medicine:${medicine.id}"
            val medScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { medScanCursor.keys } returns listOf(medKey)
            every { medScanCursor.isFinished } returns true
            val schedScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { schedScanCursor.keys } returns emptyList()
            every { schedScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(medScanCursor) andThen createRedisFutureMock(schedScanCursor)
            every { mockAsyncCommands.get(medKey) } returns createRedisFutureMock(json.encodeToString(medicine))
            val result = redisService.medicineExpiry(testUsername, LocalDate.of(2026, 1, 13).atStartOfDay())
            result.isRight() shouldBe true
            val expiry = result.getOrNull()!!
            expiry.size shouldBe 0 // Should not include medicine with no schedule
        }
        test("should calculate expiry for weekly schedule") {
            val today = LocalDate.of(2026, 1, 13)
            val medicine = Medicine(
                id = UUID.randomUUID(),
                name = "Vitamin C",
                dose = 500.0,
                unit = "mg",
                stock = 7.0
            )
            val schedule = Schedule(
                id = UUID.randomUUID(),
                medicineId = medicine.id,
                time = "09:00",
                amount = 1.0,
                daysOfWeek = listOf(DayOfWeek.MONDAY) // once per week
            )
            every { mockConnection.async() } returns mockAsyncCommands
            val medKey = "$environment:user:$testUsername:medicine:${medicine.id}"
            val schedKey = "$environment:user:$testUsername:schedule:${schedule.id}"
            val medScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { medScanCursor.keys } returns listOf(medKey)
            every { medScanCursor.isFinished } returns true
            val schedScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { schedScanCursor.keys } returns listOf(schedKey)
            every { schedScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(medScanCursor) andThen createRedisFutureMock(schedScanCursor)
            every { mockAsyncCommands.get(medKey) } returns createRedisFutureMock(json.encodeToString(medicine))
            every { mockAsyncCommands.get(schedKey) } returns createRedisFutureMock(json.encodeToString(schedule))
            val result = redisService.medicineExpiry(testUsername, today.atStartOfDay())
            result.isRight() shouldBe true
            val expiry = result.getOrNull()!!
            expiry.size shouldBe 1
            expiry[0].name shouldBe "Vitamin C"
            expiry[0].expiryDate shouldBe today.plusDays(49).atStartOfDay() // 7/0.142857 = 49 days
        }
        test("should not include medicine with zero stock and no schedule") {
            val medicine = Medicine(
                id = UUID.randomUUID(),
                name = "Paracetamol",
                dose = 500.0,
                unit = "mg",
                stock = 0.0
            )
            every { mockConnection.async() } returns mockAsyncCommands
            val medKey = "$environment:user:$testUsername:medicine:${medicine.id}"
            val medScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { medScanCursor.keys } returns listOf(medKey)
            every { medScanCursor.isFinished } returns true
            val schedScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { schedScanCursor.keys } returns emptyList()
            every { schedScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(medScanCursor) andThen createRedisFutureMock(schedScanCursor)
            every { mockAsyncCommands.get(medKey) } returns createRedisFutureMock(json.encodeToString(medicine))
            val result = redisService.medicineExpiry(testUsername, LocalDate.of(2026, 1, 13).atStartOfDay())
            result.isRight() shouldBe true
            val expiry = result.getOrNull()!!
            expiry.size shouldBe 0 // Should not include medicine with zero stock and no schedule
        }
    }
})
