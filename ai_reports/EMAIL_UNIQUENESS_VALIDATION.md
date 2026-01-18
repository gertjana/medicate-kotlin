# Email Uniqueness Validation Implementation

## Overview
This document describes the implementation of email uniqueness validation for the `updateProfile` function in response to feedback on PR #43.

## Problem Statement
The `updateProfile` function allowed users to change their email without checking if the new email was already in use by another user. This could lead to duplicate emails in the system if multiple users set the same email address.

## Solution Implemented

### 1. Email Uniqueness Check Function
Added a private helper function `isEmailInUseByOtherUser` that:
- Scans all user keys in Redis using the pattern `{environment}:user:*`
- Checks each user (excluding the current user) to see if they have the same email
- Performs case-insensitive email comparison using `equals(email, ignoreCase = true)`
- Handles deserialization errors gracefully by logging warnings and skipping malformed data
- Returns `Either<RedisError, Boolean>` for functional error handling

### 2. Updated updateProfile Function
Modified the `updateProfile` function to:
- Check email uniqueness before performing the update
- Return an error if the email is already in use by another user
- Allow users to update their profile with their current email (no change scenario)
- Maintain the existing error handling for other cases (user not found, serialization errors, etc.)

### 3. Comprehensive Test Coverage
Added tests in `UserServiceTest.kt` covering:
- ✅ Successfully updating profile when email is not in use
- ✅ Returning error when email is already in use by another user
- ✅ Allowing users to keep their current email
- ✅ Handling non-existent users
- ✅ Case-insensitive email checking

## Code Changes

### Files Modified
1. `src/main/kotlin/dev/gertjanassies/service/RedisService.kt`
   - Added `isEmailInUseByOtherUser()` function
   - Updated `updateProfile()` to validate email uniqueness

2. `src/test/kotlin/dev/gertjanassies/service/UserServiceTest.kt`
   - Added 5 new test cases for `updateProfile` function

### Key Implementation Details

```kotlin
private suspend fun isEmailInUseByOtherUser(email: String, currentUsername: String): Either<RedisError, Boolean> {
    // Scans all users and checks if email is in use
    // Case-insensitive comparison
    // Excludes current user from check
}

suspend fun updateProfile(...): Either<RedisError, User> {
    // 1. Check email uniqueness
    // 2. If unique, proceed with update
    // 3. Otherwise, return error
}
```

## Testing Results
✅ All 5 new tests pass
✅ All existing tests continue to pass
✅ No regressions introduced

## Performance Considerations

### Current Implementation
- The email uniqueness check performs an O(n) scan of all users
- Sequential GET operations for each user key
- Acceptable for small to medium-sized user bases

### Future Optimizations (if needed)
If the user base grows significantly, consider:
1. Implementing an email-to-username index in Redis (e.g., using a hash or set)
2. Using Redis secondary indexes
3. Caching email lookups for frequently accessed data

The current implementation prioritizes correctness and simplicity over performance optimization, which is appropriate for the initial implementation.

## Security Considerations
✅ No security vulnerabilities introduced
✅ Proper error handling with specific exception types
✅ Case-insensitive email comparison prevents bypassing via different casing

## Commits
- `cf6b69b` - Add email uniqueness validation to updateProfile
- `25d44fe` - Improve exception handling in email uniqueness check

## Conclusion
The implementation successfully addresses the feedback by preventing duplicate emails during profile updates while maintaining backwards compatibility and comprehensive error handling.
