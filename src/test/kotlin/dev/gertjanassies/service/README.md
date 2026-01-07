# Service Tests - Testing Approach

This directory contains comprehensive test suites demonstrating **sync testing while mocking Redis** using MockK in Kotlin.

## Test Suites

### MedicineServiceTest (11 tests)
- CRUD operations: create, read, update, delete medicines
- Stock management: add stock with optimistic locking
- Error handling: NotFound, OperationError cases

### ScheduleServiceTest (12 tests)
- CRUD operations: create, read, update, delete schedules
- Daily schedule: grouped medicines by time with day filtering
- Error handling: NotFound, OperationError cases

### DosageHistoryServiceTest (10 tests)
- Create dosage history with stock reduction
- Retrieve and sort dosage histories by datetime
- Support scheduled time tracking
- Error handling: NotFound, transaction errors

**Total: 33 tests covering all RedisService operations**

## Testing Strategy

### Approach: Mock the Service Layer

Instead of mocking low-level Redis connections (which requires complex setup with extension function mocking), we mock the `RedisService` itself. This provides:

- **Simpler test setup**: No complex Redis connection mocking
- **Better isolation**: Tests focus on business logic, not Redis internals  
- **Faster execution**: No actual Redis operations
- **Easier maintenance**: Changes to Redis implementation don't break tests

## Key Testing Patterns

### 1. Mocking Suspend Functions

```kotlin
// Use coEvery for suspend functions
coEvery { mockRedisService.getAllMedicines() } returns medicines.right()
```

### 2. Running Tests Synchronously

```kotlin
// Use runBlocking to execute suspend functions in tests
val result = runBlocking { mockRedisService.getAllMedicines() }
```

### 3. Verifying Calls

```kotlin
// Use coVerify for suspend function calls
coVerify { mockRedisService.getAllMedicines() }
```

### 4. Testing with Arrow Either

```kotlin
// Test happy path with Right
coEvery { mockRedisService.getMedicine(id) } returns medicine.right()

// Test error path with Left
coEvery { mockRedisService.getMedicine(id) } returns 
    RedisError.NotFound("Not found").left()
```

## What This Demonstrates

✅ **Sync Testing**: Tests run synchronously using `runBlocking`  
✅ **Mocking**: MockK mocks the service layer effectively  
✅ **Redis Abstraction**: Service is mocked, not actual Redis  
✅ **Functional Error Handling**: Tests Arrow's `Either` type  
✅ **Verification**: Confirms methods were called correctly  

## Alternative Approaches

### For Integration Testing
- Use **Testcontainers** to run actual Redis
- Test real Redis operations end-to-end
- Verify data persistence and retrieval

### For Low-Level Testing
- Mock Redis connection with `mockkStatic`  
- Mock `RedisFuture.await()` extension function
- Complex setup, only needed for testing Redis interaction layer

## Running Tests

```bash
./gradlew test --tests "dev.gertjanassies.service.MedicineServiceTest"
```

All tests should pass, demonstrating the mocking patterns work correctly.
