# Storage Service Interface Implementation

## Date
January 20, 2026

## Summary
Created a `StorageService` interface to abstract data persistence operations from the Redis-specific implementation. This allows for potential future migration to different storage backends (e.g., PostgreSQL, MongoDB) without changing the route handlers.

## Changes Made

### 1. Created StorageService Interface
- **File**: `src/main/kotlin/dev/gertjanassies/service/StorageService.kt`
- Extracted all public methods from `RedisService` into an interface
- Organized methods into logical groups:
  - User operations (register, login, getUser, getUserById, updateProfile, updatePassword, verifyPasswordResetToken)
  - Medicine operations (CRUD operations, getAllMedicines, getLowStockMedicines)
  - Schedule operations (CRUD operations, getAllSchedules, getDailySchedule)
  - Dosage History operations (create, addStock, getAll, delete)
  - Analytics operations (getWeeklyAdherence, medicineExpiry)

### 2. Updated RedisService
- Made `RedisService` implement `StorageService` interface
- Added `override` modifiers to all public methods
- Removed duplicate default parameter values from override methods (they're defined in the interface)
  - `getLowStockMedicines(threshold: Double = 10.0)`
  - `createDosageHistory(scheduledTime: String? = null, datetime: LocalDateTime? = null)`
  - `medicineExpiry(now: LocalDateTime = LocalDateTime.now())`

### 3. Updated Route Handlers
Updated all route files to use `StorageService` instead of `RedisService`:
- `AdherenceRoutes.kt`
- `AuthRoutes.kt`
- `DailyRoutes.kt`
- `DosageHistoryRoutes.kt`
- `MedicineRoutes.kt`
- `ScheduleRoutes.kt`
- `UserRoutes.kt`

Route handlers now receive `storageService: StorageService` parameter instead of `redisService: RedisService`.

### 4. Updated Application Configuration
- **File**: `src/main/kotlin/dev/gertjanassies/Application.kt`
- Changed route registration to pass `redisService` as `StorageService` type
- No functional changes, just type adjustments

## Benefits

1. **Abstraction**: Route handlers are decoupled from Redis-specific implementation
2. **Testability**: Easier to create alternative implementations for testing
3. **Flexibility**: Can swap storage backends without changing route logic
4. **Maintainability**: Clear contract for what storage operations are available
5. **Future-proofing**: Easier to add PostgreSQL, MongoDB, or other storage backends

## Technical Notes

### Default Parameter Values
In Kotlin, when implementing an interface method that has default parameter values, the implementation must NOT re-specify those defaults. The defaults are inherited from the interface.

**Interface**:
```kotlin
suspend fun getLowStockMedicines(username: String, threshold: Double = 10.0): Either<RedisError, List<Medicine>>
```

**Implementation** (correct):
```kotlin
override suspend fun getLowStockMedicines(username: String, threshold: Double): Either<RedisError, List<Medicine>> {
    // implementation
}
```

### Redis-Specific Helper Methods
The following helper methods remain private to `RedisService` as they are implementation-specific:
- `getUserId(username: String)`
- `createRedisFuture<T>(value: T)`
- Transaction handling utilities
- Key generation helpers

## Testing
- All 199 tests pass successfully
- No changes required to test files (they already mock `RedisService` directly)
- Build completes successfully

## Files Modified
1. `src/main/kotlin/dev/gertjanassies/service/StorageService.kt` (new)
2. `src/main/kotlin/dev/gertjanassies/service/RedisService.kt`
3. `src/main/kotlin/dev/gertjanassies/Application.kt`
4. `src/main/kotlin/dev/gertjanassies/routes/AdherenceRoutes.kt`
5. `src/main/kotlin/dev/gertjanassies/routes/AuthRoutes.kt`
6. `src/main/kotlin/dev/gertjanassies/routes/DailyRoutes.kt`
7. `src/main/kotlin/dev/gertjanassies/routes/DosageHistoryRoutes.kt`
8. `src/main/kotlin/dev/gertjanassies/routes/MedicineRoutes.kt`
9. `src/main/kotlin/dev/gertjanassies/routes/ScheduleRoutes.kt`
10. `src/main/kotlin/dev/gertjanassies/routes/UserRoutes.kt`

## Next Steps (Optional)
- Consider creating a PostgreSQL implementation of `StorageService`
- Add factory pattern for creating storage service instances based on configuration
- Consider adding a caching layer between routes and storage
