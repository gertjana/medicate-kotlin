# RedisService Update Guide - Username to UserID Migration

## Problem

After migrating data to use UUID-based keys (`medicate:test:user:{userId}:medicine:*`), the RedisService functions are still using username-based keys (`medicate:test:user:{username}:medicine:*`).

This causes all API calls to return empty results even though the data exists in Redis.

## Solution

Update all functions in RedisService.kt that access user-specific data to:
1. First call `getUserId(username)` to get the UUID
2. Use the UUID in the Redis key instead of username

## Helper Function (Already Added)

```kotlin
private suspend fun getUserId(username: String): Either<RedisError, UUID> {
    return getUser(username).map { it.id }
}
```

## Functions That Need Updating (14 total)

### 1. getMedicine (line ~109)

**Before:**
```kotlin
suspend fun getMedicine(username: String, id: String): Either<RedisError, Medicine> {
    val key = "$keyPrefix:user:$username:medicine:$id"
    val jsonString = get(key).getOrNull()
    // ... rest
}
```

**After:**
```kotlin
suspend fun getMedicine(username: String, id: String): Either<RedisError, Medicine> {
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
```

### 2. createMedicine (line ~137)

**Pattern:** Wrap entire function body with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:medicine:` → `$keyPrefix:user:$userId:medicine:`

### 3. updateMedicine (line ~155)

**Pattern:** Wrap entire function body with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:medicine:` → `$keyPrefix:user:$userId:medicine:`

### 4. deleteMedicine (line ~182)

**Pattern:** Wrap entire function body with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:medicine:` → `$keyPrefix:user:$userId:medicine:`

### 5. getAllSchedules (line ~338)

**Pattern:** Similar to getAllMedicines (already updated)
- Wrap with `getUser(username).fold()`
- Change: `$keyPrefix:user:$username:schedule:` → `$keyPrefix:user:$userId:schedule:`

### 6. getSchedule (line ~246)

**Pattern:** Wrap with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:schedule:` → `$keyPrefix:user:$userId:schedule:`

### 7. createSchedule (line ~273)

**Pattern:** Wrap with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:schedule:` → `$keyPrefix:user:$userId:schedule:`

### 8. updateSchedule (line ~291)

**Pattern:** Wrap with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:schedule:` → `$keyPrefix:user:$userId:schedule:`

### 9. deleteSchedule (line ~318)

**Pattern:** Wrap with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:schedule:` → `$keyPrefix:user:$userId:schedule:`

### 10. createDosageHistory (lines ~420, ~446)

**Pattern:** Wrap with `getUserId(username).fold()`
- TWO occurrences:
  - `$keyPrefix:user:$username:medicine:` → `$keyPrefix:user:$userId:medicine:`
  - `$keyPrefix:user:$username:dosagehistory:` → `$keyPrefix:user:$userId:dosagehistory:`

### 11. addStock (lines ~496)

**Pattern:** Wrap with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:medicine:` → `$keyPrefix:user:$userId:medicine:`

### 12. getAllDosageHistory (line ~556)

**Pattern:** Similar to getAllMedicines (wrap with getUser)
- Change: `$keyPrefix:user:$username:dosagehistory:` → `$keyPrefix:user:$userId:dosagehistory:`

### 13. deleteDosageHistory (lines ~592, ~605)

**Pattern:** Wrap with `getUserId(username).fold()`
- TWO occurrences:
  - `$keyPrefix:user:$username:dosagehistory:` → `$keyPrefix:user:$userId:dosagehistory:`
  - `$keyPrefix:user:$username:medicine:` → `$keyPrefix:user:$userId:medicine:`

### 14. getDosageHistoryByDate (line ~671)

**Pattern:** Wrap with `getUserId(username).fold()`
- Change: `$keyPrefix:user:$username:dosagehistory:` → `$keyPrefix:user:$userId:dosagehistory:`

## Testing After Update

After updating all functions, run:

```bash
./gradlew clean test
```

All 199 tests should pass.

Then test the API:

```bash
# Login
curl -X POST http://localhost:8080/api/user/login \
  -H "Content-Type: application/json" \
  -d '{"username":"gertjan","password":"test123"}'

# Get medicines (should now return data)
curl -X GET http://localhost:8080/api/medicine \
  -H "Authorization: Bearer {token}"

# Get schedules (should now return data)
curl -X GET http://localhost:8080/api/schedule \
  -H "Authorization: Bearer {token}"
```

## Quick Find & Replace (NOT AUTOMATIC - CHECK EACH!)

Search for these patterns and update manually:

1. `$keyPrefix:user:$username:medicine:` → Wrap with `getUserId`, change to `$userId`
2. `$keyPrefix:user:$username:schedule:` → Wrap with `getUserId`, change to `$userId`
3. `$keyPrefix:user:$username:dosagehistory:` → Wrap with `getUserId`, change to `$userId`

**IMPORTANT:** Don't do blind find/replace! Each function needs proper error handling with the `.fold()` pattern.

## Notes

- The token-related functions (lines ~981, ~1001) should keep using `$username` as they're not user data but session tokens
- User-related functions (getUser, registerUser, etc.) already use the correct index-based lookup
- Only CRUD operations on medicines, schedules, and dosagehistory need updating
