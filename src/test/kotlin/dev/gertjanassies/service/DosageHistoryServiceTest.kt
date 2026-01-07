package dev.gertjanassies.service

import arrow.core.Either
import dev.gertjanassies.model.DosageHistory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.KeyScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import io.mockk.*
import java.time.LocalDateTime
import java.util.*

class DosageHistoryServiceTest : FunSpec({
    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockCommands: RedisCommands<String, String>
    lateinit var redisService: RedisService

    beforeEach {
        mockConnection = mockk()
        mockCommands = mockk()
        every { mockConnection.sync() } returns mockCommands
        
        redisService = RedisService("localhost", 6379, "test")
        // Use reflection to inject mock connection
        val connectionField = RedisService::class.java.getDeclaredField("connection")
        connectionField.isAccessible = true
        connectionField.set(redisService, mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    context("DosageHistory operations") {
        val medicineId = UUID.randomUUID()
        val historyId1 = UUID.randomUUID()
        val historyId2 = UUID.randomUUID()
        val historyId3 = UUID.randomUUID()

        test("getAllDosageHistories should return empty list when no histories exist") {
            val emptyCursor = mockk<KeyScanCursor<String>>()
            every { emptyCursor.keys } returns emptyList()
            every { emptyCursor.isFinished } returns true
            every { mockCommands.scan(any<ScanArgs>()) } returns emptyCursor

            val result = redisService.getAllDosageHistories()

            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            result.getOrNull()!! shouldBe emptyList()
            verify { mockCommands.scan(any<ScanArgs>()) }
        }

        test("getAllDosageHistories should return sorted histories") {
            val datetime1 = LocalDateTime.of(2026, 1, 5, 10, 0)
            val datetime2 = LocalDateTime.of(2026, 1, 6, 14, 30)
            val datetime3 = LocalDateTime.of(2026, 1, 7, 9, 0)

            val history1Json = """{"id":"$historyId1","datetime":"2026-01-05T10:00:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Morning"}"""
            val history2Json = """{"id":"$historyId2","datetime":"2026-01-06T14:30:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Afternoon"}"""
            val history3Json = """{"id":"$historyId3","datetime":"2026-01-07T09:00:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Morning"}"""

            val key1 = "test:dosagehistory:$historyId1"
            val key2 = "test:dosagehistory:$historyId2"
            val key3 = "test:dosagehistory:$historyId3"

            val cursor = mockk<KeyScanCursor<String>>()
            every { cursor.keys } returns listOf(key1, key2, key3)
            every { cursor.isFinished } returns true
            every { mockCommands.scan(any<ScanArgs>()) } returns cursor
            every { mockCommands.get(key1) } returns history1Json
            every { mockCommands.get(key2) } returns history2Json
            every { mockCommands.get(key3) } returns history3Json

            val result = redisService.getAllDosageHistories()

            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val histories = result.getOrNull()!!
            histories.size shouldBe 3
            
            // Should be sorted by datetime descending (newest first)
            histories[0].datetime.isAfter(histories[1].datetime) shouldBe true
            histories[1].datetime.isAfter(histories[2].datetime) shouldBe true
            histories[0].id shouldBe historyId3
            histories[1].id shouldBe historyId2
            histories[2].id shouldBe historyId1
        }

        test("getAllDosageHistories should handle multiple medicines") {
            val medicineId2 = UUID.randomUUID()
            val datetime1 = LocalDateTime.of(2026, 1, 6, 10, 0)
            val datetime2 = LocalDateTime.of(2026, 1, 6, 11, 0)

            val history1Json = """{"id":"$historyId1","datetime":"2026-01-06T10:00:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Morning"}"""
            val history2Json = """{"id":"$historyId2","datetime":"2026-01-06T11:00:00","medicineId":"$medicineId2","amount":1000.0,"scheduledTime":"Morning"}"""

            val key1 = "test:dosagehistory:$historyId1"
            val key2 = "test:dosagehistory:$historyId2"

            val cursor = mockk<KeyScanCursor<String>>()
            every { cursor.keys } returns listOf(key1, key2)
            every { cursor.isFinished } returns true
            every { mockCommands.scan(any<ScanArgs>()) } returns cursor
            every { mockCommands.get(key1) } returns history1Json
            every { mockCommands.get(key2) } returns history2Json

            val result = redisService.getAllDosageHistories()

            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val histories = result.getOrNull()!!
            histories.size shouldBe 2
            
            val medicineIds = histories.map { it.medicineId }.toSet()
            medicineIds.size shouldBe 2
        }

        test("getAllDosageHistories should skip invalid entries") {
            val validHistoryJson = """{"id":"$historyId1","datetime":"2026-01-06T10:00:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Morning"}"""
            val invalidJson = """{"invalid":"json"}"""

            val key1 = "test:dosagehistory:$historyId1"
            val key2 = "test:dosagehistory:$historyId2"

            val cursor = mockk<KeyScanCursor<String>>()
            every { cursor.keys } returns listOf(key1, key2)
            every { cursor.isFinished } returns true
            every { mockCommands.scan(any<ScanArgs>()) } returns cursor
            every { mockCommands.get(key1) } returns validHistoryJson
            every { mockCommands.get(key2) } returns invalidJson

            val result = redisService.getAllDosageHistories()

            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val histories = result.getOrNull()!!
            // Should only have the valid entry
            histories.size shouldBe 1
            histories[0].id shouldBe historyId1
        }

        test("getAllDosageHistories should handle pagination") {
            val history1Json = """{"id":"$historyId1","datetime":"2026-01-06T10:00:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Morning"}"""
            val history2Json = """{"id":"$historyId2","datetime":"2026-01-06T11:00:00","medicineId":"$medicineId","amount":100.0,"scheduledTime":"Afternoon"}"""

            val key1 = "test:dosagehistory:$historyId1"
            val key2 = "test:dosagehistory:$historyId2"

            // First page
            val cursor1 = mockk<KeyScanCursor<String>>()
            every { cursor1.keys } returns listOf(key1)
            every { cursor1.isFinished } returns false
            every { cursor1.cursor } returns "cursor1"

            // Second page
            val cursor2 = mockk<KeyScanCursor<String>>()
            every { cursor2.keys } returns listOf(key2)
            every { cursor2.isFinished } returns true

            every { mockCommands.scan(any<ScanArgs>()) } returns cursor1
            every { mockCommands.scan(any<ScanCursor>(), any<ScanArgs>()) } returns cursor2
            every { mockCommands.get(key1) } returns history1Json
            every { mockCommands.get(key2) } returns history2Json

            val result = redisService.getAllDosageHistories()

            result.shouldBeInstanceOf<Either.Right<List<DosageHistory>>>()
            val histories = result.getOrNull()!!
            histories.size shouldBe 2
            verify { mockCommands.scan(any<ScanArgs>()) }
            verify { mockCommands.scan(any<ScanCursor>(), any<ScanArgs>()) }
        }
    }
})
