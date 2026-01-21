# Test Files Update Guide - Username to UserID Migration

## Problem

After updating RedisService to use UUID-based keys, all service tests are failing because they:
1. Mock keys with the old format: `medicate:test:user:{username}:medicine:*`
2. Need to mock the new format: `medicate:test:user:{userId}:medicine:*`
3. Need to mock the `getUser()` call that returns the User with UUID

## Solution Pattern

For each test in the service test files, we need to:

### 1. Create a test User with UUID
```kotlin
val testUserId = UUID.randomUUID()
val testUser = User(
    id = testUserId,
    username = testUsername,
    email = "test@example.com",
    passwordHash = "hash"
)
```

### 2. Mock the getUser() call
```kotlin
// Mock username index lookup
val usernameIndexKey = "medicate:$environment:user:username:$testUsername"
every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(testUserId.toString())

// Mock user data lookup
val userKey = "medicate:$environment:user:id:$testUserId"
val userJson = """{"id":"$testUserId","username":"$testUsername","email":"test@example.com","passwordHash":"hash"}"""
every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
```

### 3. Update the data key to use userId
```kotlin
// OLD:
val medicineKey = "medicate:$environment:user:$testUsername:medicine:$medicineId"

// NEW:
val medicineKey = "medicate:$environment:user:$testUserId:medicine:$medicineId"
```

## Files That Need Updating

1. `src/test/kotlin/dev/gertjanassies/service/MedicineServiceTest.kt`
2. `src/test/kotlin/dev/gertjanassies/service/ScheduleServiceTest.kt`
3. `src/test/kotlin/dev/gertjanassies/service/DosageHistoryServiceTest.kt`
4. `src/test/kotlin/dev/gertjanassies/service/AdherenceServiceTest.kt`

## Example: Complete Test Update

### Before:
```kotlin
test("should get medicine successfully") {
    val medicineId = UUID.randomUUID()
    val testUsername = "testuser"
    val medicineKey = "medicate:$environment:user:$testUsername:medicine:$medicineId"

    val medicine = Medicine(medicineId, "Aspirin", 100.0, "mg", 50.0)
    val medicineJson = json.encodeToString(medicine)

    every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)

    val result = redisService.getMedicine(testUsername, medicineId.toString()).getOrNull()

    result shouldBe medicine
}
```

### After:
```kotlin
test("should get medicine successfully") {
    val medicineId = UUID.randomUUID()
    val testUsername = "testuser"
    val testUserId = UUID.randomUUID()

    // Mock getUser() call
    val usernameIndexKey = "medicate:$environment:user:username:$testUsername"
    val userKey = "medicate:$environment:user:id:$testUserId"
    val userJson = """{"id":"$testUserId","username":"$testUsername","email":"test@example.com","passwordHash":"hash"}"""

    every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(testUserId.toString())
    every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

    // Now mock the actual medicine data with userId
    val medicineKey = "medicate:$environment:user:$testUserId:medicine:$medicineId"
    val medicine = Medicine(medicineId, "Aspirin", 100.0, "mg", 50.0)
    val medicineJson = json.encodeToString(medicine)

    every { mockAsyncCommands.get(medicineKey) } returns createRedisFutureMock(medicineJson)

    val result = redisService.getMedicine(testUsername, medicineId.toString()).getOrNull()

    result shouldBe medicine
}
```

## Systematic Update Steps

For each test file:

1. Add a helper function at the top:
```kotlin
private fun mockGetUser(
    mockAsyncCommands: RedisAsyncCommands<String, String>,
    environment: String,
    username: String,
    userId: UUID
) {
    val usernameIndexKey = "medicate:$environment:user:username:$username"
    val userKey = "medicate:$environment:user:id:$userId"
    val userJson = """{"id":"$userId","username":"$username","email":"$username@example.com","passwordHash":"hash"}"""

    every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
    every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
}
```

2. In each test:
   - Define `testUserId = UUID.randomUUID()`
   - Call `mockGetUser(mockAsyncCommands, environment, testUsername, testUserId)`
   - Update all keys to use `$testUserId` instead of `$testUsername`

## Notes

- Every test that calls a RedisService method with a username parameter needs this update
- SCAN operations also need to use userId in the pattern
- Transaction tests need userId in both medicine and dosagehistory keys
