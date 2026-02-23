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
     * Get user by email address
     */
    suspend fun getUserByEmail(email: String): Either<RedisError, User>

    /**
     * Update user profile
     */
    suspend fun updateProfile(username: String, email: String, firstName: String, lastName: String): Either<RedisError, User>

    /**
     * Update user password by username (used internally)
     */
    suspend fun updatePassword(username: String, newPassword: String): Either<RedisError, Unit>

    /**
     * Verify password reset token and return associated userId
     */
    suspend fun verifyPasswordResetToken(token: String): Either<RedisError, String>

    /**
     * Verify a password reset token and update the password.
     * The token is consumed (deleted) only after a successful password update,
     * so the operation is not guaranteed to be atomic across all underlying Redis calls.
     */
    suspend fun resetPasswordWithToken(token: String, newPassword: String): Either<RedisError, Unit>

    /**
     * Activate user account (set isActive to true)
     */
    suspend fun activateUser(userId: String): Either<RedisError, User>

    /**
     * Verify email activation token and return user ID
     */
    suspend fun verifyActivationToken(token: String): Either<RedisError, String>

    /**
     * Store a refresh token in the backing store so it can be invalidated on logout or password change.
     * @param token the raw refresh token string
     * @param userId the user the token belongs to
     * @param ttlSeconds time-to-live in seconds (should match the JWT expiry)
     */
    suspend fun storeRefreshToken(token: String, userId: String, ttlSeconds: Long): Either<RedisError, Unit>

    /**
     * Delete a refresh token from the backing store (called on logout or password change).
     */
    suspend fun deleteRefreshToken(token: String): Either<RedisError, Unit>

    /**
     * Check whether a refresh token exists in the backing store (i.e. has not been logged out or invalidated).
     */
    suspend fun isRefreshTokenValid(token: String): Either<RedisError, Boolean>

    /**
     * Invalidate all active refresh tokens for a user (called on password change).
     */
    suspend fun invalidateAllRefreshTokensForUser(userId: String): Either<RedisError, Unit>

    // Admin operations

    /**
     * Check if a user has admin privileges
     */
    suspend fun isUserAdmin(userId: String): Either<RedisError, Boolean>

    /**
     * Add admin privileges to a user
     */
    suspend fun addAdmin(userId: String): Either<RedisError, Unit>

    /**
     * Remove admin privileges from a user
     */
    suspend fun removeAdmin(userId: String): Either<RedisError, Unit>

    /**
     * Get all admin user IDs
     */
    suspend fun getAllAdmins(): Either<RedisError, Set<String>>

    /**
     * Get all users in the system
     */
    suspend fun getAllUsers(): Either<RedisError, List<User>>

    /**
     * Deactivate a user account (set isActive to false)
     */
    suspend fun deactivateUser(userId: String): Either<RedisError, User>

    /**
     * Completely delete a user and all associated data (medicines, schedules, history)
     */
    suspend fun deleteUserCompletely(userId: String): Either<RedisError, Unit>

    // Medicine operations

    /**
     * Get medicine by ID for a specific user
     */
    suspend fun getMedicine(userId: String, id: String): Either<RedisError, Medicine>

    /**
     * Create a new medicine for a user
     */
    suspend fun createMedicine(userId: String, request: MedicineRequest): Either<RedisError, Medicine>

    /**
     * Update an existing medicine
     */
    suspend fun updateMedicine(userId: String, id: String, medicine: Medicine): Either<RedisError, Medicine>

    /**
     * Delete a medicine
     */
    suspend fun deleteMedicine(userId: String, id: String): Either<RedisError, Unit>

    /**
     * Get all medicines for a user
     */
    suspend fun getAllMedicines(userId: String): Either<RedisError, List<Medicine>>


    // Schedule operations

    /**
     * Get schedule by ID for a specific user
     */
    suspend fun getSchedule(userId: String, id: String): Either<RedisError, Schedule>

    /**
     * Create a new schedule for a user
     */
    suspend fun createSchedule(userId: String, request: ScheduleRequest): Either<RedisError, Schedule>

    /**
     * Update an existing schedule
     */
    suspend fun updateSchedule(userId: String, id: String, schedule: Schedule): Either<RedisError, Schedule>

    /**
     * Delete a schedule
     */
    suspend fun deleteSchedule(userId: String, id: String): Either<RedisError, Unit>

    /**
     * Get all schedules for a user
     */
    suspend fun getAllSchedules(userId: String): Either<RedisError, List<Schedule>>

    /**
     * Get daily schedule for a user (grouped by time)
     */
    suspend fun getDailySchedule(userId: String): Either<RedisError, DailySchedule>

    // Dosage History operations

    /**
     * Create a dosage history record and update medicine stock.
     * Timestamp is always set server-side to LocalDateTime.now().
     */
    suspend fun createDosageHistory(
        userId: String,
        medicineId: UUID,
        amount: Double,
        scheduledTime: String? = null
    ): Either<RedisError, DosageHistory>

    /**
     * Add stock to a medicine
     */
    suspend fun addStock(userId: String, medicineId: UUID, amount: Double): Either<RedisError, Medicine>

    /**
     * Get all dosage histories for a user
     */
    suspend fun getAllDosageHistories(userId: String): Either<RedisError, List<DosageHistory>>

    /**
     * Delete a dosage history record and restore medicine stock
     */
    suspend fun deleteDosageHistory(userId: String, dosageHistoryId: UUID): Either<RedisError, Unit>

    // Analytics operations

    /**
     * Get weekly adherence statistics for a user
     */
    suspend fun getWeeklyAdherence(userId: String): Either<RedisError, WeeklyAdherence>

    /**
     * Calculate medicine expiry dates based on current stock and schedules
     */
    suspend fun medicineExpiry(userId: String, now: LocalDateTime = LocalDateTime.now()): Either<RedisError, List<MedicineWithExpiry>>
}
