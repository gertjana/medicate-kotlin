package dev.gertjanassies.service

import dev.gertjanassies.model.*
import dev.gertjanassies.model.request.*
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.TransactionResult
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.*

/**
 * Test suite for DosageHistory and Stock-related operations in RedisService.
 *
 * Tests cover:
 * - createDosageHistory: transaction creation with WATCH/MULTI/EXEC semantics
 * - addStock: stock adjustments with transaction retry logic
 * - getAllDosageHistories: scan cursor pagination with datetime sorting
 */
class DosageHistoryServiceTest : FunSpec({

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

    context("createDosageHistory") {
        test("should successfully create dosage history with watch/multi/exec transaction") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicineKey = "$environment:medicine:$medicineId"
            val medicineJson = json.encodeToString(medicine)
            val amount = 50.0
            val datetime = LocalDateTime.now()

            every { mockConnection.async() } returns mockAsyncCommands

            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("QUEUED")

            val mockTransactionResult = mockk<TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            every { mockAsyncCommands.unwatch() } returns createRedisFutureMock("OK")

            val result = redisService.createDosageHistory(medicineId, amount, datetime = datetime)

            result.isRight() shouldBe true
            val dosage = result.getOrNull()!!
            dosage.medicineId shouldBe medicineId
            dosage.amount shouldBe amount
            dosage.datetime shouldBe datetime

            verify(exactly = 1) { mockAsyncCommands.watch(medicineKey) }
            verify(exactly = 1) { mockAsyncCommands.get(medicineKey) }
            verify(exactly = 1) { mockAsyncCommands.multi() }
            verify(exactly = 2) { mockAsyncCommands.set(any(), any()) }
            verify(exactly = 1) { mockAsyncCommands.exec() }
        }

        test("should return NotFound when medicine doesn't exist during dosage creation") {
            val medicineId = UUID.randomUUID()
            val medicineKey = "$environment:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.createDosageHistory(medicineId, 50.0)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()

            verify(exactly = 1) { mockAsyncCommands.watch(medicineKey) }
        }

        test("should retry transaction when WATCH detects concurrent modification") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicineKey = "$environment:medicine:$medicineId"
            val medicineJson = json.encodeToString(medicine)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("QUEUED")

            val discardedResult = mockk<TransactionResult>()
            every { discardedResult.wasDiscarded() } returns true

            val successResult = mockk<TransactionResult>()
            every { successResult.wasDiscarded() } returns false

            every { mockAsyncCommands.exec() } returns createRedisFutureMock(discardedResult) andThen createRedisFutureMock(successResult)
            every { mockAsyncCommands.unwatch() } returns createRedisFutureMock("OK")

            val result = redisService.createDosageHistory(medicineId, 50.0)

            result.isRight() shouldBe true

            verify(atLeast = 2) { mockAsyncCommands.watch(medicineKey) }
            verify(atLeast = 2) { mockAsyncCommands.get(medicineKey) }
            verify(atLeast = 2) { mockAsyncCommands.multi() }
        }

        test("should fail after max retries exceeded") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicineKey = "$environment:medicine:$medicineId"
            val medicineJson = json.encodeToString(medicine)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("QUEUED")

            val discardedResult = mockk<TransactionResult>()
            every { discardedResult.wasDiscarded() } returns true
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(discardedResult)
            every { mockAsyncCommands.unwatch() } returns createRedisFutureMock("OK")

            val result = redisService.createDosageHistory(medicineId, 50.0)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message?.contains("retries") shouldBe true

            verify(exactly = 10) { mockAsyncCommands.exec() }
        }
    }

    context("addStock") {
        test("should successfully add stock to medicine with watch/multi/exec transaction") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicineKey = "$environment:medicine:$medicineId"
            val medicineJson = json.encodeToString(medicine)
            val amountToAdd = 50.0

            every { mockConnection.async() } returns mockAsyncCommands

            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("QUEUED")

            val mockTransactionResult = mockk<TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            every { mockAsyncCommands.unwatch() } returns createRedisFutureMock("OK")

            val result = redisService.addStock(medicineId, amountToAdd)

            result.isRight() shouldBe true
            result.getOrNull()?.stock shouldBe 150.0

            verify(exactly = 1) { mockAsyncCommands.watch(medicineKey) }
            verify(exactly = 1) { mockAsyncCommands.get(medicineKey) }
            verify(exactly = 1) { mockAsyncCommands.multi() }
            verify(exactly = 1) { mockAsyncCommands.set(any(), any()) }
            verify(exactly = 1) { mockAsyncCommands.exec() }
        }

        test("should return NotFound when medicine doesn't exist for addStock") {
            val medicineId = UUID.randomUUID()
            val medicineKey = "$environment:medicine:$medicineId"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.addStock(medicineId, 50.0)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("should retry addStock when transaction is discarded") {
            val medicineId = UUID.randomUUID()
            val medicine = Medicine(
                id = medicineId,
                name = "Aspirin",
                dose = 500.0,
                unit = "mg",
                stock = 100.0
            )
            val medicineKey = "$environment:medicine:$medicineId"
            val medicineJson = json.encodeToString(medicine)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.watch(medicineKey) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("QUEUED")

            val discardedResult = mockk<TransactionResult>()
            every { discardedResult.wasDiscarded() } returns true

            val successResult = mockk<TransactionResult>()
            every { successResult.wasDiscarded() } returns false

            every { mockAsyncCommands.exec() } returns createRedisFutureMock(discardedResult) andThen createRedisFutureMock(successResult)
            every { mockAsyncCommands.unwatch() } returns createRedisFutureMock("OK")

            val result = redisService.addStock(medicineId, 50.0)

            result.isRight() shouldBe true

            verify(atLeast = 2) { mockAsyncCommands.watch(medicineKey) }
            verify(atLeast = 2) { mockAsyncCommands.get(medicineKey) }
            verify(atLeast = 2) { mockAsyncCommands.multi() }
        }
    }

    context("getAllDosageHistories") {
        test("should successfully retrieve all dosage histories sorted by datetime descending") {
            val now = LocalDateTime.now()
            val earlier = now.minusHours(1)

            val dosage1 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = now,
                medicineId = UUID.randomUUID(),
                amount = 50.0,
                scheduledTime = "08:00"
            )
            val dosage2 = DosageHistory(
                id = UUID.randomUUID(),
                datetime = earlier,
                medicineId = UUID.randomUUID(),
                amount = 100.0,
                scheduledTime = "20:00"
            )
            val key1 = "$environment:dosagehistory:${dosage1.id}"
            val key2 = "$environment:dosagehistory:${dosage2.id}"
            val json1 = json.encodeToString(dosage1)
            val json2 = json.encodeToString(dosage2)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(key2, key1)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(key1) } returns createRedisFutureMock(json1)
            every { mockAsyncCommands.get(key2) } returns createRedisFutureMock(json2)

            val result = redisService.getAllDosageHistories()

            result.isRight() shouldBe true
            val dosages = result.getOrNull()!!
            dosages.size shouldBe 2
            dosages[0].datetime shouldBe now
            dosages[1].datetime shouldBe earlier

            verify(exactly = 1) { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) }
        }

        test("should return empty list when no dosage histories exist") {
            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns emptyList()
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            val result = redisService.getAllDosageHistories()

            result.isRight() shouldBe true
            result.getOrNull()!!.size shouldBe 0
        }

        test("should skip invalid dosage histories during deserialization") {
            val validDosage = DosageHistory(
                id = UUID.randomUUID(),
                datetime = LocalDateTime.now(),
                medicineId = UUID.randomUUID(),
                amount = 50.0,
                scheduledTime = "08:00"
            )
            val keyValid = "$environment:dosagehistory:${validDosage.id}"
            val keyInvalid = "$environment:dosagehistory:invalid"
            val validJson = json.encodeToString(validDosage)

            every { mockConnection.async() } returns mockAsyncCommands

            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(keyValid, keyInvalid)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)

            every { mockAsyncCommands.get(keyValid) } returns createRedisFutureMock(validJson)
            every { mockAsyncCommands.get(keyInvalid) } returns createRedisFutureMock("""completely invalid""")

            val result = redisService.getAllDosageHistories()

            result.isRight() shouldBe true
            result.getOrNull()!!.size shouldBe 1
            result.getOrNull()!![0].amount shouldBe 50.0
        }

        test("should return OperationError when dosage retrieval fails") {
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createFailedRedisFutureMock(RuntimeException("Scan error"))

            val result = redisService.getAllDosageHistories()

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message?.contains("Failed to retrieve dosage histories") shouldBe true
        }
    }
})
