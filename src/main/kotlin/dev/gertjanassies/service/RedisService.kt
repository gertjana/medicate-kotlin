package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import dev.gertjanassies.model.*
import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.sync.RedisCommands
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import java.util.*

/**
 * Redis service using functional programming patterns with Arrow
 */
class RedisService(private val host: String, private val port: Int, private val environment: String = "test") {
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Connect to Redis using Either for error handling
     */
    fun connect(): Either<RedisError, RedisCommands<String, String>> = Either.catch {
        val redisClient = RedisClient.create("redis://$host:$port")
        val conn = redisClient.connect()
        client = redisClient
        connection = conn
        conn.sync()
    }.mapLeft { RedisError.ConnectionError(it.message ?: "Unknown error") }

    /**
     * Get a value from Redis
     */
    fun get(key: String): Either<RedisError, String?> = Either.catch {
        connection?.sync()?.get(key)
    }.mapLeft { RedisError.OperationError(it.message ?: "Unknown error") }

    /**
     * Set a value in Redis
     */
    fun set(key: String, value: String): Either<RedisError, String> = Either.catch {
        connection?.sync()?.set(key, value) ?: throw IllegalStateException("Not connected")
    }.mapLeft { RedisError.OperationError(it.message ?: "Unknown error") }

    /**
     * Get a Medicine object from Redis by ID
     */
    fun getMedicine(id: String): Either<RedisError, Medicine> {
        val key = "$environment:medicine:$id"
        val jsonString = get(key).getOrNull()
        
        return when (jsonString) {
            null -> RedisError.NotFound("Medicine with id $id not found").left()
            else -> Either.catch {
                json.decodeFromString<Medicine>(jsonString)
            }.mapLeft { e ->
                when (e) {
                    is SerializationException -> RedisError.SerializationError("Failed to deserialize medicine: ${e.message}")
                    else -> RedisError.OperationError("Failed to parse medicine: ${e.message}")
                }
            }
        }
    }

    /**
     * Create a new Medicine in Redis
     */
    fun createMedicine(request: MedicineRequest): Either<RedisError, Medicine> {
        val medicine = Medicine(
            id = UUID.randomUUID(),
            name = request.name,
            dose = request.dose,
            unit = request.unit,
            stock = request.stock
        )
        val key = "$environment:medicine:${medicine.id}"
        
        return Either.catch {
            val jsonString = json.encodeToString(medicine)
            connection?.sync()?.set(key, jsonString) ?: throw IllegalStateException("Not connected")
            medicine
        }.mapLeft { e ->
            when (e) {
                is SerializationException -> RedisError.SerializationError("Failed to serialize medicine: ${e.message}")
                else -> RedisError.OperationError("Failed to create medicine: ${e.message}")
            }
        }
    }

    /**
     * Update an existing Medicine in Redis
     */
    fun updateMedicine(id: String, medicine: Medicine): Either<RedisError, Medicine> {
        val key = "$environment:medicine:$id"
        
        // Check if medicine exists
        val existing = get(key).getOrNull()
        
        return when (existing) {
            null -> RedisError.NotFound("Medicine with id $id not found").left()
            else -> {
                // Update the medicine
                Either.catch {
                    val jsonString = json.encodeToString(medicine)
                    connection?.sync()?.set(key, jsonString) ?: throw IllegalStateException("Not connected")
                    medicine
                }.mapLeft { e ->
                    when (e) {
                        is SerializationException -> RedisError.SerializationError("Failed to serialize medicine: ${e.message}")
                        else -> RedisError.OperationError("Failed to update medicine: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Delete a Medicine from Redis
     */
    fun deleteMedicine(id: String): Either<RedisError, Unit> {
        val key = "$environment:medicine:$id"
        
        return Either.catch {
            val deleted = connection?.sync()?.del(key) ?: throw IllegalStateException("Not connected")
            if (deleted == 0L) {
                throw NoSuchElementException("Medicine with id $id not found")
            }
        }.mapLeft { e ->
            when (e) {
                is NoSuchElementException -> RedisError.NotFound(e.message ?: "Medicine not found")
                else -> RedisError.OperationError("Failed to delete medicine: ${e.message}")
            }
        }
    }

    /**
     * Get all Medicines from Redis
     */
    fun getAllMedicines(): Either<RedisError, List<Medicine>> {
        return Either.catch {
            val pattern = "$environment:medicine:*"
            val keys = mutableListOf<String>()
            
            val syncCommands = connection?.sync() ?: throw IllegalStateException("Not connected")
            var scanCursor = syncCommands.scan(ScanArgs.Builder.matches(pattern))
            
            // Iterate through all cursor pages
            while (true) {
                keys.addAll(scanCursor.keys)
                if (scanCursor.isFinished) break
                scanCursor = syncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern))
            }
            
            // Get all values for the keys
            keys.mapNotNull { key ->
                syncCommands.get(key)?.let { jsonString ->
                    try {
                        json.decodeFromString<Medicine>(jsonString)
                    } catch (e: Exception) {
                        null // Skip invalid entries
                    }
                }
            }
        }.mapLeft { e ->
            RedisError.OperationError("Failed to retrieve medicines: ${e.message}")
        }
    }

    // Schedule operations

    /**
     * Get a Schedule object from Redis by ID
     */
    fun getSchedule(id: String): Either<RedisError, Schedule> {
        val key = "$environment:schedule:$id"
        val jsonString = get(key).getOrNull()
        
        return when (jsonString) {
            null -> RedisError.NotFound("Schedule with id $id not found").left()
            else -> Either.catch {
                json.decodeFromString<Schedule>(jsonString)
            }.mapLeft { e ->
                when (e) {
                    is SerializationException -> RedisError.SerializationError("Failed to deserialize schedule: ${e.message}")
                    else -> RedisError.OperationError("Failed to parse schedule: ${e.message}")
                }
            }
        }
    }

    /**
     * Create a new Schedule in Redis
     */
    fun createSchedule(request: ScheduleRequest): Either<RedisError, Schedule> {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            medicineId = request.medicineId,
            time = request.time,
            amount = request.amount
        )
        val key = "$environment:schedule:${schedule.id}"
        
        return Either.catch {
            val jsonString = json.encodeToString(schedule)
            connection?.sync()?.set(key, jsonString) ?: throw IllegalStateException("Not connected")
            schedule
        }.mapLeft { e ->
            when (e) {
                is SerializationException -> RedisError.SerializationError("Failed to serialize schedule: ${e.message}")
                else -> RedisError.OperationError("Failed to create schedule: ${e.message}")
            }
        }
    }

    /**
     * Update an existing Schedule in Redis
     */
    fun updateSchedule(id: String, schedule: Schedule): Either<RedisError, Schedule> {
        val key = "$environment:schedule:$id"
        
        // Check if schedule exists
        val existing = get(key).getOrNull()
        
        return when (existing) {
            null -> RedisError.NotFound("Schedule with id $id not found").left()
            else -> {
                // Update the schedule
                Either.catch {
                    val jsonString = json.encodeToString(schedule)
                    connection?.sync()?.set(key, jsonString) ?: throw IllegalStateException("Not connected")
                    schedule
                }.mapLeft { e ->
                    when (e) {
                        is SerializationException -> RedisError.SerializationError("Failed to serialize schedule: ${e.message}")
                        else -> RedisError.OperationError("Failed to update schedule: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Delete a Schedule from Redis
     */
    fun deleteSchedule(id: String): Either<RedisError, Unit> {
        val key = "$environment:schedule:$id"
        
        return Either.catch {
            val deleted = connection?.sync()?.del(key) ?: throw IllegalStateException("Not connected")
            if (deleted == 0L) {
                throw NoSuchElementException("Schedule with id $id not found")
            }
        }.mapLeft { e ->
            when (e) {
                is NoSuchElementException -> RedisError.NotFound(e.message ?: "Schedule not found")
                else -> RedisError.OperationError("Failed to delete schedule: ${e.message}")
            }
        }
    }

    /**
     * Get all Schedules from Redis
     */
    fun getAllSchedules(): Either<RedisError, List<Schedule>> {
        return Either.catch {
            val pattern = "$environment:schedule:*"
            val keys = mutableListOf<String>()
            
            val syncCommands = connection?.sync() ?: throw IllegalStateException("Not connected")
            var scanCursor = syncCommands.scan(ScanArgs.Builder.matches(pattern))
            
            // Iterate through all cursor pages
            while (true) {
                keys.addAll(scanCursor.keys)
                if (scanCursor.isFinished) break
                scanCursor = syncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern))
            }
            
            // Get all values for the keys
            keys.mapNotNull { key ->
                syncCommands.get(key)?.let { jsonString ->
                    try {
                        json.decodeFromString<Schedule>(jsonString)
                    } catch (e: Exception) {
                        null // Skip invalid entries
                    }
                }
            }
        }.mapLeft { e ->
            RedisError.OperationError("Failed to retrieve schedules: ${e.message}")
        }
    }

    /**
     * Get daily schedule with medicines grouped by time
     */
    fun getDailySchedule(): Either<RedisError, DailySchedule> {
        return either {
            val schedules = getAllSchedules().bind()
            
            // Group schedules by time
            val groupedByTime = schedules.groupBy { it.time }
            
            // For each time slot, get the medicines
            val timeSlots = groupedByTime.map { (time, schedulesAtTime) ->
                val medicineItems = schedulesAtTime.mapNotNull { schedule ->
                    getMedicine(schedule.medicineId.toString()).getOrNull()?.let { medicine ->
                        MedicineScheduleItem(medicine, schedule.amount)
                    }
                }
                TimeSlot(time, medicineItems)
            }.sortedBy { it.time }
            
            DailySchedule(timeSlots)
        }
    }

    /**
     * Create a DosageHistory from a medicine and amount
     */
    fun createDosageHistory(medicineId: UUID, amount: Double): Either<RedisError, DosageHistory> {
        return either {
            // Get medicine and verify it exists
            val medicine = getMedicine(medicineId.toString()).bind()
            
            // Update medicine stock
            val updatedMedicine = medicine.copy(stock = medicine.stock - amount)
            updateMedicine(medicineId.toString(), updatedMedicine).bind()
            
            // Create dosage history
            val dosageHistory = DosageHistory(
                id = UUID.randomUUID(),
                datetime = java.time.LocalDateTime.now(),
                medicineId = medicineId,
                amount = amount
            )
            
            val key = "$environment:dosagehistory:${dosageHistory.id}"
            
            Either.catch {
                val jsonString = json.encodeToString(dosageHistory)
                connection?.sync()?.set(key, jsonString) ?: throw IllegalStateException("Not connected")
                dosageHistory
            }.mapLeft { e ->
                when (e) {
                    is SerializationException -> RedisError.SerializationError("Failed to serialize dosage history: ${e.message}")
                    else -> RedisError.OperationError("Failed to create dosage history: ${e.message}")
                }
            }.bind()
        }
    }

    /**
     * Add stock to a medicine
     */
    fun addStock(medicineId: UUID, amount: Double): Either<RedisError, Medicine> {
        return either {
            // Get medicine and verify it exists
            val medicine = getMedicine(medicineId.toString()).bind()
            
            // Update medicine stock
            val updatedMedicine = medicine.copy(stock = medicine.stock + amount)
            updateMedicine(medicineId.toString(), updatedMedicine).bind()
        }
    }

    /**
     * Close the connection
     */
    fun close() {
        connection?.close()
        client?.shutdown()
    }
}

/**
 * Sealed interface for Redis errors
 */
sealed interface RedisError {
    val message: String
    
    data class ConnectionError(override val message: String) : RedisError
    data class OperationError(override val message: String) : RedisError
    data class NotFound(override val message: String) : RedisError
    data class SerializationError(override val message: String) : RedisError
}
