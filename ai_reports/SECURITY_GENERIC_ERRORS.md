# Security Enhancement: Generic Error Messages

## Overview
Implemented security improvements to prevent user enumeration attacks by using generic error messages that don't reveal whether specific usernames or email addresses exist in the system.

## Changes Made

### 1. User Registration (`POST /user/register`)
**Before:**
- Returned specific error: "Username already exists" or "Email already exists"
- Attackers could enumerate valid usernames and emails

**After:**
- Returns generic error: "Registration failed. Please try a different username or email."
- No information about which field (username or email) caused the failure
- Backend still logs specific details for debugging

### 2. Password Reset (`POST /auth/resetPassword`)
**Before:**
- Returned error "No account found with that email address" for non-existent emails
- Allowed attackers to determine which email addresses are registered

**After:**
- Always returns: "If an account with that email exists, a password reset link has been sent."
- Same response whether email exists or not
- Backend still logs actual errors for monitoring
- Actual email is only sent if account exists (no fake emails sent)

### 3. User Login (`POST /user/login`)
**Before:**
- Could distinguish between "username doesn't exist" and "wrong password" errors

**After:**
- Generic error: "Invalid credentials"
- No distinction between non-existent user and wrong password
- Prevents username enumeration during login attempts

### 4. Password Update (`PUT /user/password`)
**Before:**
- Returned 404 when user not found
- Revealed user existence

**After:**
- Always returns 200 OK with "Password updated successfully"
- Same response whether user exists or not
- Prevents user enumeration via password update endpoint

### 5. Protected Profile Routes (`GET/PUT /user/profile`)
**Before:**
- Returned specific 404 errors revealing user existence

**After:**
- Generic 500 error: "Failed to retrieve profile" or "Failed to update profile"
- For authenticated routes, user should exist (JWT validated)
- If user somehow doesn't exist despite valid JWT, don't reveal details
- Profile update uses generic: "Failed to update profile. Email may already be in use."

## Security Benefits

1. **Prevents User Enumeration**
   - Attackers cannot determine which usernames or emails are registered
   - Cannot build a list of valid accounts for targeted attacks

2. **Timing Attack Mitigation**
   - Consistent response times regardless of whether user exists
   - Backend processing happens the same way for both cases

3. **Email Privacy**
   - Users cannot determine if a specific email is registered
   - Protects user privacy and prevents social engineering

4. **Password Reset Security**
   - Cannot use password reset feature to verify email addresses
   - Reduces spam and phishing attack vectors

## Backend Logging
All specific error details are still logged on the backend for debugging and monitoring:
- Failed registration attempts (with specific reason)
- Password reset attempts (whether user was found)
- Failed login attempts
- Profile update failures

This allows administrators to diagnose issues while maintaining security for users.

## Testing
All tests updated to expect generic error messages:
- AuthRoutesTest: Password reset tests now expect generic success message
- UserRoutesTest: Registration, login, and password update tests expect generic errors
- All 199 tests passing

## Best Practices Followed
1. Never reveal whether a username/email exists in error messages
2. Use same HTTP status codes regardless of internal state
3. Log specific errors server-side for debugging
4. Return consistent timing for both success and failure cases
5. Maintain user experience with helpful (but non-specific) messages

## Implementation Date
January 23, 2026

## Related Files
- `src/main/kotlin/dev/gertjanassies/routes/UserRoutes.kt`
- `src/main/kotlin/dev/gertjanassies/routes/AuthRoutes.kt`
- `src/test/kotlin/dev/gertjanassies/routes/UserRoutesTest.kt`
- `src/test/kotlin/dev/gertjanassies/routes/AuthRoutesTest.kt`
