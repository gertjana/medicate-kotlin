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
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt
import org.slf4j.LoggerFactory
import java.util.*
import java.security.SecureRandom
import java.time.LocalDateTime


/**
 * Redis service using functional programming patterns with Arrow and async coroutines
 * Implements StorageService interface for data persistence
 */
class RedisService private constructor(
    private val host: String?,
    private val port: Int?,
    private val environment: String,
    private var connection: StatefulRedisConnection<String, String>?,
    private val isConnectionOwner: Boolean
) : StorageService {
    private var client: RedisClient? = null
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(RedisService::class.java)

    // Use medicate prefix for all keys
    private val keyPrefix = "medicate:$environment"

    /**
     * Primary constructor for production use
     */
    constructor(host: String, port: Int, environment: String = "test") : this(host, port, environment, null, true)

    /**
     * Constructor for testing that accepts a connection
     */
    constructor(environment: String, connection: StatefulRedisConnection<String, String>) : this(null, null, environment, connection, false)

    /**
     * Get the environment name (for key prefixing)
     */
    fun getEnvironment(): String = environment

    /**
     * Connect to Redis using Either for error handling
     * If connection is already set (for testing), returns it
     */
    fun connect(): Either<RedisError, RedisAsyncCommands<String, String>> = Either.catch {
        // If connection is already provided (test mode), return its async commands
        if (connection != null) {
            connection!!.async()
        } else {
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
     * Helper function to get userId from username
     *
     * WARNING: If multiple users have the same username, this returns the ID of the first one.
     * For authenticated operations, extract userId directly from the JWT token instead.
     *
     * @deprecated For authenticated operations, extract userId from JWT token
     */
    private suspend fun getUserId(username: String): Either<RedisError, UUID> {
        return getUser(username).map { it.id }
    }

    /**
     * Helper to validate a userId string and convert to UUID
     */
    private fun validateUserId(userIdString: String): Either<RedisError, UUID> {
        return Either.catch {
            UUID.fromString(userIdString)
        }.mapLeft {
            RedisError.OperationError("Invalid user ID format")
        }
    }

    /**
     * Set a value in Redis with TTL (Time To Live) in seconds
     * The key will automatically be deleted after the specified time
     */
    suspend fun setex(key: String, ttlSeconds: Long, value: String): Either<RedisError, String> = Either.catch {
        connection?.async()?.setex(key, ttlSeconds, value)?.await() ?: throw IllegalStateException("Not connected")
    }.mapLeft { RedisError.OperationError(it.message ?: "Unknown error") }

    /**
     * Get a Medicine object from Redis by ID asynchronously for a specific user
     */
    override suspend fun getMedicine(username: String, id: String): Either<RedisError, Medicine> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val key = "$keyPrefix:user:$userId:medicine:$id"
                val jsonString = get(key).getOrNull()

                when (jsonString) {
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
        )
    }

    /**
     * Create a new Medicine in Redis asynchronously
     */
    override suspend fun createMedicine(username: String, request: MedicineRequest): Either<RedisError, Medicine> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val medicine = Medicine(
                    id = UUID.randomUUID(),
                    name = request.name,
                    dose = request.dose,
                    unit = request.unit,
                    stock = request.stock,
                    description = request.description
                )
                val key = "$keyPrefix:user:$userId:medicine:${medicine.id}"

                Either.catch {
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
        )
    }

    /**
     * Update an existing Medicine in Redis asynchronously
     */
    override suspend fun updateMedicine(username: String, id: String, medicine: Medicine): Either<RedisError, Medicine> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val key = "$keyPrefix:user:$userId:medicine:$id"

                // Check if medicine exists
                val existing = get(key).getOrNull()

                when (existing) {
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
        )
    }

    /**
     * Delete a Medicine from Redis asynchronously
     */
    override suspend fun deleteMedicine(username: String, id: String): Either<RedisError, Unit> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val key = "$keyPrefix:user:$userId:medicine:$id"

                Either.catch {
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
        )
    }

    /**
     * Get all Medicines from Redis asynchronously for a specific user
     */
    override suspend fun getAllMedicines(username: String): Either<RedisError, List<Medicine>> {
        // First get the user ID from username
        return getUser(username).fold(
            { error -> error.left() },
            { user ->
                Either.catch {
                    val pattern = "$keyPrefix:user:${user.id}:medicine:*"
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
                            } catch (_: Exception) {
                                null // Skip invalid entries
                            }
                        }
                    }
                }.mapLeft { e ->
                    RedisError.OperationError("Failed to retrieve medicines: ${e.message}")
                }
            }
        )
    }


    // Schedule operations

    /**
     * Get a Schedule object from Redis by ID asynchronously for a specific user
     */
    override suspend fun getSchedule(username: String, id: String): Either<RedisError, Schedule> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val key = "$keyPrefix:user:$userId:schedule:$id"
                val jsonString = get(key).getOrNull()

                when (jsonString) {
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
        )
    }

    /**
     * Create a new Schedule in Redis asynchronously
     */
    override suspend fun createSchedule(username: String, request: ScheduleRequest): Either<RedisError, Schedule> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val schedule = Schedule(
                    id = UUID.randomUUID(),
                    medicineId = request.medicineId,
                    time = request.time,
                    amount = request.amount,
                    daysOfWeek = request.daysOfWeek
                )
                val key = "$keyPrefix:user:$userId:schedule:${schedule.id}"

                Either.catch {
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
        )
    }

    /**
     * Update an existing Schedule in Redis asynchronously
     */
    override suspend fun updateSchedule(username: String, id: String, schedule: Schedule): Either<RedisError, Schedule> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val key = "$keyPrefix:user:$userId:schedule:$id"

                // Check if schedule exists
                val existing = get(key).getOrNull()

                when (existing) {
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
        )
    }

    /**
     * Delete a Schedule from Redis asynchronously
     */
    override suspend fun deleteSchedule(username: String, id: String): Either<RedisError, Unit> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                val key = "$keyPrefix:user:$userId:schedule:$id"

                Either.catch {
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
        )
    }

    /**
     * Get all Schedules from Redis asynchronously for a specific user
     */
    override suspend fun getAllSchedules(username: String): Either<RedisError, List<Schedule>> {
        return getUser(username).fold(
            { error -> error.left() },
            { user ->
                Either.catch {
                    val pattern = "$keyPrefix:user:${user.id}:schedule:*"
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
                            } catch (_: Exception) {
                                null // Skip invalid entries
                            }
                        }
                    }
                }.mapLeft { e ->
                    RedisError.OperationError("Failed to retrieve schedules: ${e.message}")
                }
            }
        )
    }

    /**
     * Get daily schedule with medicines grouped by time asynchronously for a specific user
     */
    override suspend fun getDailySchedule(username: String): Either<RedisError, DailySchedule> {
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
    override suspend fun createDosageHistory(username: String, medicineId: UUID, amount: Double, scheduledTime: String?, datetime: java.time.LocalDateTime?): Either<RedisError, DosageHistory> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                either {
                    val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
                    val medicineKey = "$keyPrefix:user:$userId:medicine:$medicineId"

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

                            val dosageKey = "$keyPrefix:user:$userId:dosagehistory:${dosageHistory.id}"
                            val updatedMedicine = medicine.copy(stock = medicine.stock - amount)

                            // Start transaction
                            asyncCommands.multi().await()

                            val updatedMedicineJson = json.encodeToString(updatedMedicine)
                            val dosageHistoryJson = json.encodeToString(dosageHistory)

                            // Queue commands in the transaction.
                            asyncCommands.set(medicineKey, updatedMedicineJson)
                            asyncCommands.set(dosageKey, dosageHistoryJson)

                            // Execute transaction
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
        )
    }

    /**
     * Add stock to a medicine using Redis WATCH for optimistic locking asynchronously
     */
    override suspend fun addStock(username: String, medicineId: UUID, amount: Double): Either<RedisError, Medicine> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                either {
                    val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
                    val medicineKey = "$keyPrefix:user:$userId:medicine:$medicineId"

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

                            asyncCommands.set(medicineKey, updatedMedicineJson)

                            // Execute transaction
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
        )
    }

    /**
     * Get all DosageHistory entries from Redis asynchronously for a specific user
     */
    override suspend fun getAllDosageHistories(username: String): Either<RedisError, List<DosageHistory>> {
        return getUser(username).fold(
            { error -> error.left() },
            { user ->
                Either.catch {
                    val pattern = "$keyPrefix:user:${user.id}:dosagehistory:*"
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
                    val list = keys.mapNotNull { key ->
                        asyncCommands.get(key).await()?.let { jsonString ->
                            try {
                                json.decodeFromString<DosageHistory>(jsonString)
                            } catch (_: Exception) {
                                null // Skip invalid entries
                            }
                        }
                    }

                    list.sortedByDescending { it.datetime }
                }.mapLeft { e ->
                    RedisError.OperationError("Failed to retrieve dosage histories: ${e.message}")
                }
            }
        )
    }

    /**
     * Delete a DosageHistory entry and restore the stock to the medicine
     */
    override suspend fun deleteDosageHistory(username: String, dosageHistoryId: UUID): Either<RedisError, Unit> {
        return getUserId(username).fold(
            { error -> error.left() },
            { userId ->
                either {
                    val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
                    val dosageKey = "$keyPrefix:user:$userId:dosagehistory:$dosageHistoryId"

                    // Retry loop for optimistic locking with WATCH
                    var retryCount = 0
                    val maxRetries = 10

                    while (retryCount < maxRetries) {
                        Either.catch {
                            // Get the dosage history first
                            val dosageJson = asyncCommands.get(dosageKey).await()
                                ?: throw NoSuchElementException("Dosage history with id $dosageHistoryId not found")

                            val dosageHistory = json.decodeFromString<DosageHistory>(dosageJson)
                            val medicineKey = "$keyPrefix:user:$userId:medicine:${dosageHistory.medicineId}"

                            // Watch both keys for changes
                            asyncCommands.watch(medicineKey, dosageKey).await()

                            // Get medicine
                            val medicineJson = asyncCommands.get(medicineKey).await()
                                ?: throw NoSuchElementException("Medicine with id ${dosageHistory.medicineId} not found")

                            val medicine = json.decodeFromString<Medicine>(medicineJson)

                            // Restore stock to medicine
                            val updatedMedicine = medicine.copy(stock = medicine.stock + dosageHistory.amount)

                            // Start transaction
                            asyncCommands.multi().await()

                            val updatedMedicineJson = json.encodeToString(updatedMedicine)

                            // Queue commands in the transaction
                            asyncCommands.set(medicineKey, updatedMedicineJson)
                            asyncCommands.del(dosageKey)

                            // Execute transaction
                            val result = asyncCommands.exec().await()

                            if (result.wasDiscarded()) {
                                // Transaction failed due to concurrent modification, retry
                                retryCount++
                                null
                            } else {
                                // Success
                                return@either Unit
                            }
                        }.mapLeft { e ->
                            runCatching { asyncCommands.unwatch().await() }
                            when (e) {
                                is NoSuchElementException -> raise(RedisError.NotFound(e.message ?: "Dosage history or medicine not found"))
                                is SerializationException -> raise(RedisError.SerializationError("Failed to serialize: ${e.message}"))
                                else -> raise(RedisError.OperationError("Failed to delete dosage history: ${e.message}"))
                            }
                        }.bind()
                    }

                    // Max retries exceeded
                    raise(RedisError.OperationError("Failed to delete dosage history after $maxRetries retries due to concurrent modifications"))
                }
            }
        )
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
        return getUser(username).fold(
            { error -> error.left() },
            { user ->
                Either.catch {
                    val pattern = "$keyPrefix:user:${user.id}:dosagehistory:*"
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
                    val list = keys.mapNotNull { key ->
                        asyncCommands.get(key).await()?.let { jsonString ->
                            try {
                                json.decodeFromString<DosageHistory>(jsonString)
                            } catch (_: Exception) {
                                null // Skip invalid entries
                            }
                        }
                    }

                    list.filter { history ->
                        val historyDate = history.datetime.toLocalDate()
                        !historyDate.isBefore(startDate) && !historyDate.isAfter(endDate)
                    }.sortedByDescending { it.datetime }
                }.mapLeft { e ->
                    RedisError.OperationError("Failed to retrieve dosage histories in date range: ${e.message}")
                }
            }
        )
    }

    /**
     * Get weekly adherence (last 7 days, excluding today) for a specific user
     */
    override suspend fun getWeeklyAdherence(username: String): Either<RedisError, WeeklyAdherence> {
        return either {
            val allSchedules = getAllSchedules(username).bind()

            // Only load dosage histories from the last 7 days (excluding today) for efficiency
            val endDate = java.time.LocalDate.now().minusDays(1)
            val startDate = endDate.minusDays(6)
            val dosageHistories = getDosageHistoriesInDateRange(username, startDate, endDate).bind()

            val days = (6 downTo 0).map { daysAgo ->
                val date = endDate.minusDays(daysAgo.toLong())
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
     * Get a user by username (uses username index to find user ID)
     *
     * WARNING: If multiple users have the same username, this returns the first one.
     * For authenticated operations, use getUserById with the userId from the JWT token instead.
     *
     * @deprecated For authenticated operations, use getUserById with userId from JWT token
     */
    override suspend fun getUser(username: String): Either<RedisError, User> {
        // First, get user ID(s) from username index
        val usernameIndexKey = "$keyPrefix:user:username:$username"

        return get(usernameIndexKey).fold(
            { error -> error.left() },
            { userIdsString ->
                when (userIdsString) {
                    null -> RedisError.NotFound("User not found").left()
                    else -> {
                        // Handle comma-separated user IDs (for multiple users with same username)
                        val firstUserId = userIdsString.split(",").firstOrNull()?.trim()
                        if (firstUserId == null) {
                            RedisError.NotFound("User not found").left()
                        } else {
                            getUserById(firstUserId)
                        }
                    }
                }
            }
        )
    }

    /**
     * Get user by ID
     */
    override suspend fun getUserById(userId: String): Either<RedisError, User> {
        val key = "$keyPrefix:user:id:$userId"

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
                            else -> RedisError.OperationError("Failed to get user: ${e.message}")
                        }
                    }
                }
            }
        )
    }

    /**
     * Get user by email address
     */
    override suspend fun getUserByEmail(email: String): Either<RedisError, User> {
        val emailIndexKey = "$keyPrefix:user:email:${email.lowercase()}"

        // Get user ID from email index
        return get(emailIndexKey).fold(
            { error -> error.left() },
            { userId ->
                when (userId) {
                    null -> RedisError.NotFound("No user found with email: $email").left()
                    else -> getUserById(userId)
                }
            }
        )
    }

    /**
     * Register a new user with username, email and password
     * Allows multiple users with same username but different emails
     */
    override suspend fun registerUser(username: String, email: String, password: String): Either<RedisError, User> {
        val usernameIndexKey = "$keyPrefix:user:username:$username"
        val emailIndexKey = "$keyPrefix:user:email:${email.lowercase()}"

        // Check if email already exists
        if (email.isNotBlank()) {
            val existingEmail = get(emailIndexKey).getOrNull()
            if (existingEmail != null) {
                return RedisError.OperationError("Email already in use").left()
            }
        }

        return Either.catch {
            val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")

            // Get existing username index (may be null or contain comma-separated user IDs)
            val existingUserIds = asyncCommands.get(usernameIndexKey).await()

            // Generate new user ID
            val userId = UUID.randomUUID()
            val userKey = "$keyPrefix:user:id:$userId"

            // Hash the password using BCrypt
            val passwordHash = BCrypt.hashpw(password, BCrypt.gensalt())

            // Create new user with UUID
            val user = User(id = userId, username = username, email = email, passwordHash = passwordHash)
            val jsonString = json.encodeToString(user)

            // Calculate updated user IDs list
            val updatedUserIds = if (existingUserIds != null) {
                "$existingUserIds,$userId"
            } else {
                userId.toString()
            }

            // Use transaction to ensure atomicity
            asyncCommands.multi().await()

            // Store user data
            asyncCommands.set(userKey, jsonString)
            // Update username index with comma-separated list of user IDs
            asyncCommands.set(usernameIndexKey, updatedUserIds)
            // Create email index if email provided
            if (email.isNotBlank()) {
                asyncCommands.set(emailIndexKey, userId.toString())
            }

            asyncCommands.exec().await()

            user
        }.mapLeft { e ->
            when (e) {
                is SerializationException -> RedisError.SerializationError("Failed to serialize user: ${e.message}")
                else -> RedisError.OperationError("Failed to register user: ${e.message}")
            }
        }
    }

    /**
     * Login user by verifying password hash against all users with that username
     * Returns the user whose password matches
     */
    override suspend fun loginUser(username: String, password: String): Either<RedisError, User> = either {
        val usernameIndexKey = "$keyPrefix:user:username:$username"

        // Get username index (may contain single ID or comma-separated list)
        val nullableUserIdsString = get(usernameIndexKey).bind()
        if (nullableUserIdsString == null) {
            raise(RedisError.NotFound("User not found"))
        }
        val userIdsString: String = nullableUserIdsString

        // Split into list of user IDs
        val userIds = userIdsString.split(",").map { it.trim() }

        // Try to authenticate with each user ID
        for (userIdStr in userIds) {
            val userId = try {
                UUID.fromString(userIdStr)
            } catch (e: IllegalArgumentException) {
                continue // Skip invalid UUIDs
            }

            // Get user data
            val userResult = getUserById(userId.toString())
            if (userResult.isLeft()) continue

            val user = userResult.getOrNull()!!

            // Check if password matches
            if (BCrypt.checkpw(password, user.passwordHash)) {
                return@either user
            }
        }

        // No matching password found
        raise(RedisError.OperationError("Invalid credentials"))
    }

    /**
     * Update user password
     */
    override suspend fun updatePassword(username: String, newPassword: String): Either<RedisError, Unit> {
        return getUser(username).fold(
            { error -> error.left() },
            { user ->
                Either.catch {
                    val userKey = "$keyPrefix:user:id:${user.id}"
                    val newPasswordHash = BCrypt.hashpw(newPassword, BCrypt.gensalt())
                    val updatedUser = user.copy(passwordHash = newPasswordHash)
                    val updatedJsonString = json.encodeToString(updatedUser)
                    connection?.async()?.set(userKey, updatedJsonString)?.await() ?: throw IllegalStateException("Not connected")
                    Unit
                }.mapLeft { e ->
                    when (e) {
                        is SerializationException -> RedisError.SerializationError("Failed to serialize user: ${e.message}")
                        else -> RedisError.OperationError("Failed to update password: ${e.message}")
                    }
                }
            }
        )
    }

    /**
     * Check if an email is already in use by a different user
     */
    private suspend fun isEmailInUseByOtherUser(email: String, currentUserId: UUID): Either<RedisError, Boolean> {
        if (email.isBlank()) {
            return false.right()
        }

        val emailIndexKey = "$keyPrefix:user:email:${email.lowercase()}"

        return get(emailIndexKey).fold(
            { error -> error.left() },
            { userId ->
                when (userId) {
                    null -> false.right() // Email not in use
                    currentUserId.toString() -> false.right() // Same user
                    else -> true.right() // Different user has this email
                }
            }
        )
    }

    /**
     * Update user profile (email, firstName, lastName)
     */
    override suspend fun updateProfile(username: String, email: String, firstName: String, lastName: String): Either<RedisError, User> {
        return getUser(username).fold(
            { error -> error.left() },
            { user ->
                // Check if email is already in use by another user
                isEmailInUseByOtherUser(email, user.id).fold(
                    { error -> error.left() },
                    { emailInUse ->
                        if (emailInUse) {
                            RedisError.OperationError("Email is already in use by another user").left()
                        } else {
                            Either.catch {
                                val userKey = "$keyPrefix:user:id:${user.id}"
                                val oldEmailIndexKey = "$keyPrefix:user:email:${user.email.lowercase()}"
                                val newEmailIndexKey = "$keyPrefix:user:email:${email.lowercase()}"

                                val updatedUser = user.copy(email = email, firstName = firstName, lastName = lastName)
                                val updatedJsonString = json.encodeToString(updatedUser)

                                val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")

                                // Use transaction to update user and email index atomically
                                asyncCommands.multi().await()

                                // Update user data
                                asyncCommands.set(userKey, updatedJsonString)

                                // Update email index if email changed
                                if (!user.email.equals(email, ignoreCase = true)) {
                                    // Delete old email index
                                    if (user.email.isNotBlank()) {
                                        asyncCommands.del(oldEmailIndexKey)
                                    }
                                    // Create new email index
                                    if (email.isNotBlank()) {
                                        asyncCommands.set(newEmailIndexKey, user.id.toString())
                                    }
                                }

                                asyncCommands.exec().await()

                                updatedUser
                            }.mapLeft { e ->
                                when (e) {
                                    is SerializationException -> RedisError.SerializationError("Failed to serialize user: ${e.message}")
                                    else -> RedisError.OperationError("Failed to update profile: ${e.message}")
                                }
                            }
                        }
                    }
                )
            }
        )
    }

    suspend fun validateToken(username: String, token: String): Either<RedisError, Boolean> {
        val key = "$keyPrefix:user:$username:token:$token"

        return get(key).fold(
            { error -> error.left() },
            { value ->
                when (value) {
                    null -> RedisError.NotFound("Token not found or expired").left()
                    "valid" -> true.right()
                    else -> false.right()
                }
            }
        )
    }


    suspend fun generateTokenAndStore(username: String, tokenExpirySeconds: Long = 3600): Either<RedisError, String> {
        val secureRandom = SecureRandom()
        val code = secureRandom.nextInt(900000) + 100000 // Range: 100000-999999
        val token = code.toString()

        val key = "$keyPrefix:user:$username:token:$token"

        return Either.catch {
            connection?.async()?.setex(key, tokenExpirySeconds, "valid")?.await() ?: throw IllegalStateException("Not connected")
            token
        }.mapLeft { e ->
            RedisError.OperationError("Failed to generate and store token: ${e.message}")
        }
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

    /**
     * Calculate expiry date for each medicine based on schedules and stock
     */
    override suspend fun medicineExpiry(username: String, now: java.time.LocalDateTime): Either<RedisError, List<MedicineWithExpiry>> = either {
        val medicines = getAllMedicines(username).bind()
        val schedules = getAllSchedules(username).bind()
        medicines
            .mapNotNull { medicine ->
                val medSchedules = schedules.filter { it.medicineId == medicine.id }
                if (medSchedules.isEmpty()) return@mapNotNull null // Exclude medicines not in a schedule
                val dailyAmount = medSchedules.sumOf { schedule ->
                    val daysPerWeek = if (schedule.daysOfWeek.isEmpty()) 7 else schedule.daysOfWeek.size
                    schedule.amount * (daysPerWeek / 7.0)
                }
                if (dailyAmount > 0.0) {
                    val daysLeft = (medicine.stock / dailyAmount).toInt()
                    val expiryDate = now.plusDays(daysLeft.toLong())
                    MedicineWithExpiry(
                        id = medicine.id,
                        name = medicine.name,
                        dose = medicine.dose,
                        unit = medicine.unit,
                        stock = medicine.stock,
                        description = medicine.description,
                        expiryDate = expiryDate
                    )
                } else {
                    MedicineWithExpiry(
                        id = medicine.id,
                        name = medicine.name,
                        dose = medicine.dose,
                        unit = medicine.unit,
                        stock = medicine.stock,
                        description = medicine.description,
                        expiryDate = null
                    )
                }
            }
            .sortedBy { it.name }
    }

    /**
     * Verify password reset token and return associated username
     * Token is single-use and will be deleted after successful verification
     * Note: Token is now stored with user ID, so we need to look up the username
     */
    override suspend fun verifyPasswordResetToken(token: String): Either<RedisError, String> = either {
        // Scan for keys matching: password_reset:*:token
        // This will find the key password_reset:{userId}:token
        val pattern = "$keyPrefix:password_reset:*:$token"
        logger.debug("Verifying password reset token, scanning for password_reset keys")
        val keys = mutableListOf<String>()

        val asyncCommands = connection?.async() ?: throw IllegalStateException("Not connected")
        var scanCursor = Either.catch {
            asyncCommands.scan(ScanArgs.Builder.matches(pattern)).await()
        }.mapLeft { e ->
            RedisError.OperationError("Failed to scan for reset tokens: ${e.message}")
        }.bind()

        // Iterate through all cursor pages
        while (true) {
            keys.addAll(scanCursor.keys)
            if (scanCursor.isFinished) break
            scanCursor = Either.catch {
                asyncCommands.scan(io.lettuce.core.ScanCursor.of(scanCursor.cursor), ScanArgs.Builder.matches(pattern)).await()
            }.mapLeft { e ->
                RedisError.OperationError("Failed to scan for reset tokens: ${e.message}")
            }.bind()
        }

        logger.debug("Found ${keys.size} matching keys: $keys")

        // Should find exactly one matching key (if token exists and hasn't expired via TTL)
        val matchingKey = when (keys.size) {
            0 -> raise(
                RedisError.NotFound("Invalid or expired password reset token")
            )
            1 -> keys[0]
            else -> {
                // Multiple matching keys indicate a potential attack or cleanup bug
                logger.warn(
                    "Multiple matching password reset keys found for token pattern {}: count={}, keys={}",
                    pattern,
                    keys.size,
                    keys
                )
                raise(
                    RedisError.OperationError(
                        "Multiple password reset tokens found; possible attack or cleanup issue"
                    )
                )
            }
        }

        logger.debug("Using matching key")

        // Get the user ID from the value (token was stored with userId as value)
        val userId = get(matchingKey).bind() ?: raise(
            RedisError.NotFound("Invalid or expired password reset token")
        )

        logger.debug("Found user ID: $userId for token")

        // Get the user by ID to retrieve username
        val user = getUserById(userId).bind()
        val username = user.username

        // Delete the token after successful verification (one-time use)
        Either.catch {
            asyncCommands.del(matchingKey).await()
        }.mapLeft { e ->
            RedisError.OperationError("Failed to delete reset token: ${e.message}")
        }.bind()

        logger.debug("Successfully verified and deleted token for user: $username")

        username
    }

    // end of RedisService class
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
