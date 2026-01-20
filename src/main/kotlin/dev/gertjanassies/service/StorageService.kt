package dev.gertjanassies.service

import arrow.core.Either
import dev.gertjanassies.model.*
import dev.gertjanassies.model.request.*
import java.time.LocalDateTime
import java.util.*

/**
 * Storage service interface for data persistence operations
 * This abstraction allows swapping storage backends (e.g., Redis, PostgreSQL, MongoDB)
 */
interface StorageService {

    // User operations

    /**
     * Register a new user
     */
    suspend fun registerUser(username: String, email: String, password: String): Either<RedisError, User>

    /**
     * Login a user
     */
    suspend fun loginUser(username: String, password: String): Either<RedisError, User>

    /**
     * Get user by username
     */
    suspend fun getUser(username: String): Either<RedisError, User>

    /**
     * Get user by ID
     */
    suspend fun getUserById(userId: String): Either<RedisError, User>

    /**
     * Update user profile
     */
    suspend fun updateProfile(username: String, email: String, firstName: String, lastName: String): Either<RedisError, User>

    /**
     * Update user password
     */
    suspend fun updatePassword(username: String, newPassword: String): Either<RedisError, Unit>

    /**
     * Verify password reset token
     */
    suspend fun verifyPasswordResetToken(token: String): Either<RedisError, String>

    // Medicine operations

    /**
     * Get medicine by ID for a specific user
     */
    suspend fun getMedicine(username: String, id: String): Either<RedisError, Medicine>

    /**
     * Create a new medicine for a user
     */
    suspend fun createMedicine(username: String, request: MedicineRequest): Either<RedisError, Medicine>

    /**
     * Update an existing medicine
     */
    suspend fun updateMedicine(username: String, id: String, medicine: Medicine): Either<RedisError, Medicine>

    /**
     * Delete a medicine
     */
    suspend fun deleteMedicine(username: String, id: String): Either<RedisError, Unit>

    /**
     * Get all medicines for a user
     */
    suspend fun getAllMedicines(username: String): Either<RedisError, List<Medicine>>

    /**
     * Get medicines with low stock (below threshold) for a user
     */
    suspend fun getLowStockMedicines(username: String, threshold: Double = 10.0): Either<RedisError, List<Medicine>>

    // Schedule operations

    /**
     * Get schedule by ID for a specific user
     */
    suspend fun getSchedule(username: String, id: String): Either<RedisError, Schedule>

    /**
     * Create a new schedule for a user
     */
    suspend fun createSchedule(username: String, request: ScheduleRequest): Either<RedisError, Schedule>

    /**
     * Update an existing schedule
     */
    suspend fun updateSchedule(username: String, id: String, schedule: Schedule): Either<RedisError, Schedule>

    /**
     * Delete a schedule
     */
    suspend fun deleteSchedule(username: String, id: String): Either<RedisError, Unit>

    /**
     * Get all schedules for a user
     */
    suspend fun getAllSchedules(username: String): Either<RedisError, List<Schedule>>

    /**
     * Get daily schedule for a user (grouped by time)
     */
    suspend fun getDailySchedule(username: String): Either<RedisError, DailySchedule>

    // Dosage History operations

    /**
     * Create a dosage history record and update medicine stock
     */
    suspend fun createDosageHistory(
        username: String,
        medicineId: UUID,
        amount: Double,
        scheduledTime: String? = null,
        datetime: LocalDateTime? = null
    ): Either<RedisError, DosageHistory>

    /**
     * Add stock to a medicine
     */
    suspend fun addStock(username: String, medicineId: UUID, amount: Double): Either<RedisError, Medicine>

    /**
     * Get all dosage histories for a user
     */
    suspend fun getAllDosageHistories(username: String): Either<RedisError, List<DosageHistory>>

    /**
     * Delete a dosage history record and restore medicine stock
     */
    suspend fun deleteDosageHistory(username: String, dosageHistoryId: UUID): Either<RedisError, Unit>

    // Analytics operations

    /**
     * Get weekly adherence statistics for a user
     */
    suspend fun getWeeklyAdherence(username: String): Either<RedisError, WeeklyAdherence>

    /**
     * Calculate medicine expiry dates based on current stock and schedules
     */
    suspend fun medicineExpiry(username: String, now: LocalDateTime = LocalDateTime.now()): Either<RedisError, List<MedicineWithExpiry>>
}
