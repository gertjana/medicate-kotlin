# Admin Page Final Fixes

## Issues Fixed

1. **Empty users table** - Fixed Redis SCAN implementation to iterate through all cursor pages
2. **Page refresh redirects to home** - Added user store initialization wait logic

## Root Cause

### Empty Users Table
The Redis `SCAN` command returns results in batches with a cursor. The original implementation only took the first batch and stopped, which often returned 0 keys even though keys existed in Redis.

**Solution:** Implemented proper cursor iteration that continues scanning until `cursor.isFinished` is true, collecting all keys across multiple cursor pages.

### Page Refresh Issue
When directly accessing `/admin` or refreshing the page, the `userStore` wasn't initialized yet (still loading from localStorage/session), causing the `onMount` check to fail and redirect to home.

**Solution:** Added retry logic that waits up to 1 second (10 attempts × 100ms) for the user store to initialize before checking authentication.

## Changes Made

### Backend

**RedisService.kt** - Fixed `getAllUsers()` implementation:
```kotlin
val allKeys = mutableListOf<String>()
var cursor: io.lettuce.core.ScanCursor = io.lettuce.core.ScanCursor.INITIAL
val scanArgs = ScanArgs.Builder.matches(pattern)

do {
    val scanCursor = if (cursor.cursor == "0") {
        asyncCommands.scan(scanArgs).await()
    } else {
        asyncCommands.scan(cursor, scanArgs).await()
    }
    allKeys.addAll(scanCursor.keys)
    cursor = scanCursor
} while (!cursor.isFinished)
```

This ensures ALL users are retrieved from Redis, not just the first batch.

**Application.kt** - Added environment logging:
```kotlin
this@module.log.info("Initializing Redis connection: host=$redisHost, port=$redisPort, environment=$appEnvironment")
```

Helps debug which environment (test/production) is being used.

### Frontend

**admin/+page.svelte** - Added user store wait logic:
```typescript
onMount(async () => {
    // Wait a bit for userStore to initialize if needed
    let attempts = 0;
    while (!$userStore && attempts < 10) {
        await new Promise(resolve => setTimeout(resolve, 100));
        attempts++;
    }

    if (!$userStore) {
        goto('/');
        return;
    }

    if (!$userStore.isAdmin) {
        goto('/');
        return;
    }

    await loadUsers();
});
```

This prevents premature redirects when the page loads directly.

## Testing

Please test:
1. **Visit /admin directly in browser** - Should stay on admin page if logged in as admin
2. **Refresh the admin page** - Should stay on admin page, not redirect to home
3. **Check users table** - Should show all 3 users from the test environment
4. **Admin panel link in dropdown** - Should still work
5. **Non-admin users** - Should be redirected to home when trying to access /admin

## Environment Note

The application is running in **test** environment, so it will show users from `medicate:test:user:id:*` keys in Redis.
