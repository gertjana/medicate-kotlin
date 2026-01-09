package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import arrow.core.raise.either
import dev.gertjanassies.model.*
import dev.gertjanassies.model.request.*
import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import kotlinx.coroutines.future.await
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import java.util.*

/**
 * Redis service using functional programming patterns with Arrow and async coroutines
 */
class RedisService private constructor(
    private val host: String?,
    private val port: Int?,
    private val environment: String,
    private var connection: StatefulRedisConnection<String, String>?,
    private val isConnectionOwner: Boolean
) {
    private var client: RedisClient? = null
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Primary constructor for production use
     */
    constructor(host: String, port: Int, environment: String = "test") : this(host, port, environment, null, true)

    /**
     * Constructor for testing that accepts a connection
     */
    constructor(environment: String, connection: StatefulRedisConnection<String, String>) : this(null, null, environment, connection, false)

    /**
     * Connect to Redis using Either for error handling
     * If connection is already set (for testing), returns it
     */
    fun connect(): Either<RedisError, RedisAsyncCommands<String, String>> = Either.catch {
        connection?.let { conn ->
            // Connection already provided (test mode)
            conn.async()
        } ?: run {
            // Connect to Redis (production mode)
            requireNotNull(host) { "Host must be provided for production mode" }
            requireNotNull(port) { "Port must be provided for production mode" }
            val redisClient = RedisClient.create("redis://$host:$port")
            val conn = redisClient.connect()
            client = redisClient
            connection = conn
            conn.async()
        }
    }.mapLeft { RedisError.ConnectionError(it.message ?: "Unknown error") }

    /**
     * Get a value from Redis asynchronously
     */
    suspend fun get(key: String): Either<RedisError, String?> = Either.catch {
        connection?.async()?.get(key)?.await()
    }.mapLeft { RedisError.OperationError(it.message ?: "Unknown error") }

    /**
     * Set a value in Redis asynchronously
     */
    suspend fun set(key: String, value: String): Either<RedisError, String> = Either.catch {
        connection?.async()?.set(key, value)?.await() ?: throw IllegalStateException("Not connected")
    }.mapLeft { RedisError.OperationError(it.message ?: "Unknown error") }

    /**
     * Get a Medicine object from Redis by ID asynchronously for a specific user
     */
    suspend fun getMedicine(username: String, id: String): Either<RedisError, Medicine> {
        val key = "$environment:user:$username:medicine:$id"
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
     * Create a new Medicine in Redis asynchronously
     */
    suspend fun createMedicine(username: String, request: MedicineRequest): Either<RedisError, Medicine> {
        val medicine = Medicine(
            id = UUID.randomUUID(),
            name = request.name,
            dose = request.dose,
            unit = request.unit,
            stock = request.stock,
            description = request.description
        )
        val key = "$environment:user:$username:medicine:${medicine.id}"

        return Either.catch {
            val jsonString = json.encodeToString(medicine)
            connection?.async()?.set(key, jsonString)?.await() ?: throw IllegalStateException("Not connected")
            medicine
        }.mapLeft { e ->
            when (e) {
                is SerializationException -> RedisError.SerializationError("Failed to serialize medicine: ${e.message}")
                else -> RedisError.OperationError("Failed to create medicine: ${e.message}")
            }
        }
    }

    /**
     * Update an existing Medicine in Redis asynchronously
     */
    suspend fun updateMedicine(username: String, id: String, medicine: Medicine): Either<RedisError, Medicine> {
        val key = "$environment:user:$username:medicine:$id"

        // Check if medicine exists
        val existing = get(key).getOrNull()

        return when (existing) {
            null -> RedisError.NotFound("Medicine with id $id not found").left()
            else -> {
                // Update the medicine
                Either.catch {
                    val jsonString = json.encodeToString(medicine)
                    connection?.async()?.set(key, jsonString)?.await() ?: throw IllegalStateException("Not connected")
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
     * Delete a Medicine from Redis asynchronously
     */
    suspend fun deleteMedicine(username: String, id: String): Either<RedisError, Unit> {
        val key = "$environment:user:$username:medicine:$id"

        return Either.catch {
            val deleted = connection?.async()?.del(key)?.await() ?: throw IllegalStateException("Not connected")
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
     * Get all Medicines from Redis asynchronously for a specific user
     */
    suspend fun getAllMedicines(username: String): Either<RedisError, List<Medicine>> {
        return Either.catch {
            val pattern = "$environment:user:$username:medicine:*"
            val keys = mutableListOf<String>()

            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
            var scanCursor = asyncCommands.scan(ScanArgs.Builder.matches(pattern)).await()

            // Iterate through all cursor pages
            while (true) {
                keys.addAll(scanCursor.keys)
                if (scanCursor.isFinished) break
                scanCursor = asyncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern)).await()
            }

            // Get all values for the keys
            keys.mapNotNull { key ->
                asyncCommands.get(key).await()?.let { jsonString ->
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
     * Get a Schedule object from Redis by ID asynchronously for a specific user
     */
    suspend fun getSchedule(username: String, id: String): Either<RedisError, Schedule> {
        val key = "$environment:user:$username:schedule:$id"
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
     * Create a new Schedule in Redis asynchronously
     */
    suspend fun createSchedule(username: String, request: ScheduleRequest): Either<RedisError, Schedule> {
        val schedule = Schedule(
            id = UUID.randomUUID(),
            medicineId = request.medicineId,
            time = request.time,
            amount = request.amount,
            daysOfWeek = request.daysOfWeek
        )
        val key = "$environment:user:$username:schedule:${schedule.id}"

        return Either.catch {
            val jsonString = json.encodeToString(schedule)
            connection?.async()?.set(key, jsonString)?.await() ?: throw IllegalStateException("Not connected")
            schedule
        }.mapLeft { e ->
            when (e) {
                is SerializationException -> RedisError.SerializationError("Failed to serialize schedule: ${e.message}")
                else -> RedisError.OperationError("Failed to create schedule: ${e.message}")
            }
        }
    }

    /**
     * Update an existing Schedule in Redis asynchronously
     */
    suspend fun updateSchedule(username: String, id: String, schedule: Schedule): Either<RedisError, Schedule> {
        val key = "$environment:user:$username:schedule:$id"

        // Check if schedule exists
        val existing = get(key).getOrNull()

        return when (existing) {
            null -> RedisError.NotFound("Schedule with id $id not found").left()
            else -> {
                // Update the schedule
                Either.catch {
                    val jsonString = json.encodeToString(schedule)
                    connection?.async()?.set(key, jsonString)?.await() ?: throw IllegalStateException("Not connected")
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
     * Delete a Schedule from Redis asynchronously
     */
    suspend fun deleteSchedule(username: String, id: String): Either<RedisError, Unit> {
        val key = "$environment:user:$username:schedule:$id"

        return Either.catch {
            val deleted = connection?.async()?.del(key)?.await() ?: throw IllegalStateException("Not connected")
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
     * Get all Schedules from Redis asynchronously for a specific user
     */
    suspend fun getAllSchedules(username: String): Either<RedisError, List<Schedule>> {
        return Either.catch {
            val pattern = "$environment:user:$username:schedule:*"
            val keys = mutableListOf<String>()

            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
            var scanCursor = asyncCommands.scan(ScanArgs.Builder.matches(pattern)).await()

            // Iterate through all cursor pages
            while (true) {
                keys.addAll(scanCursor.keys)
                if (scanCursor.isFinished) break
                scanCursor = asyncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern)).await()
            }

            // Get all values for the keys
            keys.mapNotNull { key ->
                asyncCommands.get(key).await()?.let { jsonString ->
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
     * Get daily schedule with medicines grouped by time asynchronously for a specific user
     */
    suspend fun getDailySchedule(username: String): Either<RedisError, DailySchedule> {
        return either {
            val allSchedules = getAllSchedules(username).bind()

            // Get current day of week as enum
            val today = java.time.LocalDate.now()
            val currentDay = DayOfWeek.fromJavaDay(today.dayOfWeek)

            // Filter schedules: include if daysOfWeek is empty or contains today
            val schedules = allSchedules.filter { schedule ->
                schedule.daysOfWeek.isEmpty() || schedule.daysOfWeek.contains(currentDay)
            }

            // Group schedules by time
            val groupedByTime = schedules.groupBy { it.time }

            // For each time slot, get the medicines
            val timeSlots = groupedByTime.map { (time, schedulesAtTime) ->
                val medicineItems = schedulesAtTime.mapNotNull { schedule ->
                    getMedicine(username, schedule.medicineId.toString()).getOrNull()?.let { medicine ->
                        MedicineScheduleItem(medicine, schedule.amount)
                    }
                }
                TimeSlot(time, medicineItems)
            }.sortedBy { it.time }

            DailySchedule(timeSlots)
        }
    }

    /**
     * Creates a [DosageHistory] entry for a given medicine and dosage amount and persists it in Redis asynchronously.
     *
     * This operation has the side effect of reducing the current stock of the specified medicine
     * by the given [amount] and updating the stored [Medicine] accordingly.
     *
     * Uses Redis WATCH for optimistic locking to prevent race conditions.
     *
     * @param medicineId The unique identifier of the [Medicine] for which the dosage is taken.
     * @param amount The amount of the medicine taken, which will be subtracted from the medicine's stock.
     * @return An [Either] containing:
     *   - [DosageHistory] on success, representing the created dosage history entry.
     *   - [RedisError] on failure, for example:
     *       - [RedisError.NotFound] if the medicine with the given [medicineId] does not exist.
     *       - [RedisError.SerializationError] if the [DosageHistory] cannot be serialized to JSON.
     *       - [RedisError.OperationError] if persisting the dosage history or updating the medicine fails
     *         (e.g. connection issues or an invalid Redis state).
     */
    suspend fun createDosageHistory(username: String, medicineId: UUID, amount: Double, scheduledTime: String? = null, datetime: java.time.LocalDateTime? = null): Either<RedisError, DosageHistory> {
        return either {
            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
            val medicineKey = "$environment:user:$username:medicine:$medicineId"

            // Retry loop for optimistic locking with WATCH
            var retryCount = 0
            val maxRetries = 10

            while (retryCount < maxRetries) {
                Either.catch {
                    // Watch the medicine key for changes
                    asyncCommands.watch(medicineKey).await()

                    // Get medicine and verify it exists
                    val medicineJson = asyncCommands.get(medicineKey).await()
                        ?: throw NoSuchElementException("Medicine with id $medicineId not found")

                    val medicine = json.decodeFromString<Medicine>(medicineJson)

                    // Create dosage history
                    val dosageHistory = DosageHistory(
                        id = UUID.randomUUID(),
                        datetime = datetime ?: java.time.LocalDateTime.now(),
                        medicineId = medicineId,
                        amount = amount,
                        scheduledTime = scheduledTime
                    )

                    val dosageKey = "$environment:user:$username:dosagehistory:${dosageHistory.id}"
                    val updatedMedicine = medicine.copy(stock = medicine.stock - amount)

                    // Start transaction
                    asyncCommands.multi().await()

                    val updatedMedicineJson = json.encodeToString(updatedMedicine)
                    val dosageHistoryJson = json.encodeToString(dosageHistory)

                    // Queue commands in the transaction.
                    // Note: In Lettuce, commands between MULTI and EXEC are queued on the Redis server
                    // and the returned RedisFuture objects only complete when EXEC is called.
                    // We intentionally do NOT await these futures here - they will complete after EXEC.
                    // See: https://redis.github.io/lettuce/user-guide/transactions-multi/
                    asyncCommands.set(medicineKey, updatedMedicineJson)
                    asyncCommands.set(dosageKey, dosageHistoryJson)

                    // Execute transaction - this atomically executes all queued commands
                    // and completes all the RedisFuture objects from the queued commands
                    val result = asyncCommands.exec().await()

                    if (result.wasDiscarded()) {
                        // Transaction failed due to concurrent modification, retry
                        retryCount++
                        null
                    } else {
                        // Success
                        return@either dosageHistory
                    }
                }.mapLeft { e ->
                    runCatching { asyncCommands.unwatch().await() }
                    when (e) {
                        is NoSuchElementException -> raise(RedisError.NotFound(e.message ?: "Medicine not found"))
                        is SerializationException -> raise(RedisError.SerializationError("Failed to serialize: ${e.message}"))
                        else -> raise(RedisError.OperationError("Failed to create dosage history: ${e.message}"))
                    }
                }.bind()
            }

            // Max retries exceeded
            raise(RedisError.OperationError("Failed to create dosage history after $maxRetries retries due to concurrent modifications"))
        }
    }

    /**
     * Add stock to a medicine using Redis WATCH for optimistic locking asynchronously
     */
    suspend fun addStock(username: String, medicineId: UUID, amount: Double): Either<RedisError, Medicine> {
        return either {
            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
            val medicineKey = "$environment:user:$username:medicine:$medicineId"

            // Retry loop for optimistic locking with WATCH
            var retryCount = 0
            val maxRetries = 10

            while (retryCount < maxRetries) {
                Either.catch {
                    // Watch the medicine key for changes
                    asyncCommands.watch(medicineKey).await()

                    // Get medicine and verify it exists
                    val medicineJson = asyncCommands.get(medicineKey).await()
                        ?: throw NoSuchElementException("Medicine with id $medicineId not found")

                    val medicine = json.decodeFromString<Medicine>(medicineJson)
                    val updatedMedicine = medicine.copy(stock = medicine.stock + amount)

                    // Start transaction
                    asyncCommands.multi().await()

                    val updatedMedicineJson = json.encodeToString(updatedMedicine)

                    // Queue command in the transaction.
                    // Note: In Lettuce, commands between MULTI and EXEC are queued on the Redis server
                    // and the returned RedisFuture only completes when EXEC is called.
                    // We intentionally do NOT await this future here - it will complete after EXEC.
                    asyncCommands.set(medicineKey, updatedMedicineJson)

                    // Execute transaction - this atomically executes all queued commands
                    val result = asyncCommands.exec().await()

                    if (result.wasDiscarded()) {
                        // Transaction failed due to concurrent modification, retry
                        retryCount++
                        null
                    } else {
                        // Success
                        return@either updatedMedicine
                    }
                }.mapLeft { e ->
                    runCatching { asyncCommands.unwatch().await() }
                    when (e) {
                        is NoSuchElementException -> raise(RedisError.NotFound(e.message ?: "Medicine not found"))
                        is SerializationException -> raise(RedisError.SerializationError("Failed to serialize medicine: ${e.message}"))
                        else -> raise(RedisError.OperationError("Failed to add stock: ${e.message}"))
                    }
                }.bind()
            }

            // Max retries exceeded
            raise(RedisError.OperationError("Failed to add stock after $maxRetries retries due to concurrent modifications"))
        }
    }

    /**
     * Get all DosageHistory entries from Redis asynchronously for a specific user
     */
    suspend fun getAllDosageHistories(username: String): Either<RedisError, List<DosageHistory>> {
        return Either.catch {
            val pattern = "$environment:user:$username:dosagehistory:*"
            val keys = mutableListOf<String>()

            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
            var scanCursor = asyncCommands.scan(ScanArgs.Builder.matches(pattern)).await()

            // Iterate through all cursor pages
            while (true) {
                keys.addAll(scanCursor.keys)
                if (scanCursor.isFinished) break
                scanCursor = asyncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern)).await()
            }

            // Get all values for the keys and sort by datetime descending
            keys.mapNotNull { key ->
                asyncCommands.get(key).await()?.let { jsonString ->
                    try {
                        json.decodeFromString<DosageHistory>(jsonString)
                    } catch (e: Exception) {
                        null // Skip invalid entries
                    }
                }
            }.sortedByDescending { it.datetime }
        }.mapLeft { e ->
            RedisError.OperationError("Failed to retrieve dosage histories: ${e.message}")
        }
    }

    /**
     * Get dosage histories within a date range (inclusive)
     *
     * Note: Due to Redis key-value structure, this method still scans all dosage history keys
     * and retrieves all values, but filters them immediately after deserialization to only
     * return entries within the specified date range. This reduces memory usage for the caller
     * compared to getAllDosageHistories, though it doesn't reduce Redis I/O.
     *
     * For true query-time filtering, consider migrating to Redis Sorted Sets with timestamps
     * as scores or a time-series data structure.
     */
    suspend fun getDosageHistoriesInDateRange(
        username: String,
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate
    ): Either<RedisError, List<DosageHistory>> {
        return Either.catch {
            val pattern = "$environment:user:$username:dosagehistory:*"
            val keys = mutableListOf<String>()

            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
            var scanCursor = asyncCommands.scan(ScanArgs.Builder.matches(pattern)).await()

            // Iterate through all cursor pages
            while (true) {
                keys.addAll(scanCursor.keys)
                if (scanCursor.isFinished) break
                scanCursor = asyncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern)).await()
            }

            // Get values for the keys, filter by date range, and sort by datetime descending
            keys.mapNotNull { key ->
                asyncCommands.get(key).await()?.let { jsonString ->
                    try {
                        json.decodeFromString<DosageHistory>(jsonString)
                    } catch (e: Exception) {
                        null // Skip invalid entries
                    }
                }
            }.filter { history ->
                val historyDate = history.datetime.toLocalDate()
                !historyDate.isBefore(startDate) && !historyDate.isAfter(endDate)
            }.sortedByDescending { it.datetime }
        }.mapLeft { e ->
            RedisError.OperationError("Failed to retrieve dosage histories in date range: ${e.message}")
        }
    }

    /**
     * Get weekly adherence (last 7 days) for a specific user
     */
    suspend fun getWeeklyAdherence(username: String): Either<RedisError, WeeklyAdherence> {
        return either {
            val allSchedules = getAllSchedules(username).bind()

            // Only load dosage histories from the last 7 days for efficiency
            val endDate = java.time.LocalDate.now()
            val startDate = endDate.minusDays(6)
            val dosageHistories = getDosageHistoriesInDateRange(username, startDate, endDate).bind()

            val days = (6 downTo 0).map { daysAgo ->
                val date = java.time.LocalDate.now().minusDays(daysAgo.toLong())
                val dayOfWeek = DayOfWeek.fromJavaDay(date.dayOfWeek)

                // Calculate expected medications for this day
                val expectedSchedules = allSchedules.filter { schedule ->
                    schedule.daysOfWeek.isEmpty() || schedule.daysOfWeek.contains(dayOfWeek)
                }
                val expectedCount = expectedSchedules.size

                // Precompute medicine IDs that are expected for this day
                val expectedMedicineIds = expectedSchedules.map { it.medicineId }.toSet()

                // Count how many expected medicines were actually taken
                val takenCount = dosageHistories.count { history ->
                    val historyDate = history.datetime.toLocalDate()
                    historyDate.isEqual(date) && expectedMedicineIds.contains(history.medicineId)
                }

                // Determine status
                val status = when {
                    expectedCount == 0 -> AdherenceStatus.NONE
                    takenCount == 0 -> AdherenceStatus.NONE
                    takenCount >= expectedCount -> AdherenceStatus.COMPLETE
                    else -> AdherenceStatus.PARTIAL
                }

                DayAdherence(
                    date = date.toString(), // Convert to ISO string format
                    dayOfWeek = dayOfWeek.name,
                    dayNumber = date.dayOfMonth,
                    month = date.monthValue,
                    status = status,
                    expectedCount = expectedCount,
                    takenCount = takenCount
                )
            }

            WeeklyAdherence(days)
        }
    }

    /**
     * Get medicines with low stock (< threshold) for a specific user
     */
    suspend fun getLowStockMedicines(username: String, threshold: Double = 10.0): Either<RedisError, List<Medicine>> {
        return either {
            val allMedicines = getAllMedicines(username).bind()
            allMedicines.filter { it.stock < threshold }
        }
    }

    /**
     * Register a new user
     * For now, we just store the username (no password yet)
     */
    suspend fun registerUser(username: String): Either<RedisError, User> {
        val key = "$environment:user:$username"

        // Check if user already exists
        val existing = get(key).getOrNull()

        return when (existing) {
            null -> {
                // Create new user
                val user = User(username = username)
                Either.catch {
                    val jsonString = json.encodeToString(user)
                    connection?.async()?.set(key, jsonString)?.await() ?: throw IllegalStateException("Not connected")
                    user
                }.mapLeft { e ->
                    when (e) {
                        is SerializationException -> RedisError.SerializationError("Failed to serialize user: ${e.message}")
                        else -> RedisError.OperationError("Failed to register user: ${e.message}")
                    }
                }
            }
            else -> RedisError.OperationError("Username already exists").left()
        }
    }

    /**
     * Login user (for now just checks if user exists)
     * Default password for all users - no actual password check yet
     */
    suspend fun loginUser(username: String): Either<RedisError, User> {
        val key = "$environment:user:$username"

        return get(key).fold(
            { error -> error.left() },
            { jsonString ->
                when (jsonString) {
                    null -> RedisError.NotFound("User not found").left()
                    else -> Either.catch {
                        json.decodeFromString<User>(jsonString)
                    }.mapLeft { e ->
                        when (e) {
                            is SerializationException -> RedisError.SerializationError("Failed to deserialize user: ${e.message}")
                            else -> RedisError.OperationError("Failed to login: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * Close the connection
     * Only closes the connection if it was created by this service (not injected for testing)
     */
    fun close() {
        if (isConnectionOwner) {
            connection?.close()
            client?.shutdown()
        }
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
