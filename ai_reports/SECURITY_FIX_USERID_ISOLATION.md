# Critical Security Fix: User Data Isolation Issue

## Problem Discovered
**CRITICAL SECURITY BUG**: Users with the same username but different email addresses
can see each other's medicines, schedules, and dosage history!

### Root Cause
When multiple users have the same username:
1. Both users get JWT tokens with their unique `userId`
2. Route handlers extract `userId` from JWT
3. Route handlers call `storageService.getUserById(userId)` to get user object
4. Route handlers extract `username` from user object
5. Route handlers call data methods like `getAllMedicines(username)`
6. RedisService methods call `getUser(username)` which uses username index
7. **BUG**: `getUser(username)` returns the FIRST userId for that username
8. RedisService uses the wrong userId to construct Redis keys
9. Second user sees first user's data!

The bug is in RedisService.kt line 853:
```kotlin
val firstUserId = userIdsString.split(",").firstOrNull()?.trim()
```

## Solution
Use `userId` directly from JWT throughout the entire data flow:
1. Extract `userId` from JWT ✓
2. Pass `userId` directly to storage methods (not username)
3. Storage methods use `userId` directly to construct Redis keys
4. No more username->userId lookups in data operations

## Changes Required

### 1. StorageService Interface ✓ COMPLETED
Changed all data operation method signatures from:
```kotlin
suspend fun getAllMedicines(username: String): Either<RedisError, List<Medicine>>
```
To:
```kotlin
suspend fun getAllMedicines(userId: String): Either<RedisError, List<Medicine>>
```

User authentication methods (register, login, getUser, updatePassword) still use `username`.

### 2. RedisService Implementation - IN PROGRESS
Need to update 17 methods to:
- Change parameter name from `username: String` to `userId: String`
- Remove the `getUser(username).fold()` wrapper
- Use `userId` directly in Redis key construction

Example transformation:
```kotlin
// OLD
override suspend fun getAllMedicines(username: String): Either<RedisError, List<Medicine>> {
    return getUser(username).fold(
        { error -> error.left() },
        { user ->
            Either.catch {
                val pattern = "$keyPrefix:user:${user.id}:medicine:*"
                ...
            }.mapLeft { ... }
        }
    )
}

// NEW
override suspend fun getAllMedicines(userId: String): Either<RedisError, List<Medicine>> {
    return Either.catch {
        val pattern = "$keyPrefix:user:$userId:medicine:*"
        ...
    }.mapLeft { ... }
}
```

Methods to update:
- ✓ getAllMedicines
- getMedicine
- createMedicine
- updateMedicine
- deleteMedicine
- getSchedule
- createSchedule
- updateSchedule
- deleteSchedule
- getAllSchedules
- getDailySchedule
- createDosageHistory
- addStock
- getAllDosageHistories
- deleteDosageHistory
- getWeeklyAdherence
- medicineExpiry

### 3. Route Handlers - TODO
Update all route handlers in:
- MedicineRoutes.kt
- ScheduleRoutes.kt
- DosageHistoryRoutes.kt
- AdherenceRoutes.kt
- DailyRoutes.kt

Change from:
```kotlin
val userId = call.getUserId() ?: return@get
val userResult = storageService.getUserById(userId)
val username = userResult.getOrNull()!!.username
val data = storageService.someMethod(username).bind()
```

To:
```kotlin
val userId = call.getUserId() ?: return@get
val data = storageService.someMethod(userId).bind()
```

### 4. Tests - TODO
Update all test files:
- Update mock calls to use `userId` instead of `testUsername`
- Update verify statements to check `userId` parameter
- Files: *RoutesTest.kt, *ServiceTest.kt

## Testing Plan
1. Update all code
2. Run `./gradlew test` to ensure all tests pass
3. Manual test:
   - Create user1: username="test", email="test1@example.com"
   - Create user2: username="test", email="test2@example.com"
   - Add medicine for user1
   - Login as user2
   - Verify user2 does NOT see user1's medicine

## Commit Message
```
SECURITY FIX: Prevent data leakage between users with same username

CRITICAL: Users with identical usernames could see each other's data
because route handlers were converting userId->username->userId, and
getUser(username) always returned the first matching user.

Changes:
- Updated StorageService interface to use userId for all data operations
- Updated RedisService to use userId directly without username lookup
- Updated all route handlers to pass userId from JWT directly to storage
- Updated all tests to mock and verify userId parameters

BREAKING CHANGE: Storage method signatures changed from username to userId

Fixes: Data isolation vulnerability
Impact: Users with same username now properly isolated
```

## Status
- ✓ StorageService interface updated
- ⚠️  RedisService partially updated (1/17 methods done)
- ⏳ Route handlers not started
- ⏳ Tests not started

## Next Steps
Due to the large scope (100+ changes across 20+ files), recommend:
1. Complete RedisService method updates systematically
2. Update route handlers file by file
3. Update tests file by file
4. Run tests frequently to catch issues early
5. Test manually with two users having same username

## Files Modified
- src/main/kotlin/dev/gertjanassies/service/StorageService.kt ✓
- src/main/kotlin/dev/gertjanassies/service/RedisService.kt (partial)
- src/main/kotlin/dev/gertjanassies/routes/*.kt (TODO)
- src/test/kotlin/dev/gertjanassies/**/*Test.kt (TODO)
