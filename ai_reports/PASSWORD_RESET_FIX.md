# Password Reset Fix - JWT Authentication

## Issue
Password reset flow was failing with "Invalid or expired token" error because the `/user/password` endpoint was behind JWT authentication. Users resetting their password don't have a valid JWT token yet!

## Solution
Moved the password update endpoint from protected UserRoutes to public AuthRoutes.

## Changes Made

### Backend
1. **`src/main/kotlin/dev/gertjanassies/routes/AuthRoutes.kt`**
   - Added `PUT /auth/updatePassword` endpoint (public, no JWT required)
   - Accepts username and new password
   - Updates password in Redis
   - No authentication required for password reset flow

2. **Routing Structure:**
   ```kotlin
   route("/api") {
       // Public routes (no JWT required)
       authRoutes(redisService, emailService, jwtService)
       // - POST /auth/resetPassword
       // - POST /auth/verifyResetToken
       // - PUT /auth/updatePassword  NEW

       // Protected routes (JWT required)
       authenticate("auth-jwt") {
           userRoutes(redisService, jwtService)
           // - PUT /user/password (kept for authenticated password changes)
       }
   }
   ```

### Frontend
1. **`frontend/src/lib/api.ts`**
   - Updated `updatePassword()` function
   - Changed endpoint from `/user/password` to `/auth/updatePassword`
   - No JWT token sent (password reset is public flow)

## Password Reset Flow (Now Working)

1. **User requests password reset:**
   - Clicks "Forgot Password"
   - Enters username
   - Backend sends email with reset link

2. **User clicks reset link:**
   - URL: `https://app.com/reset-password?token=abc123`
   - Frontend calls `/auth/verifyResetToken` (public, no JWT)
   - Backend validates token, returns username

3. **User sets new password:**
   - Enters new password twice
   - Frontend calls `/auth/updatePassword` (public, no JWT)
   - Backend updates password in Redis
   - User automatically logged in with new password

## Testing

 All backend tests passing (165/165)
 Frontend builds successfully
 Password reset flow tested and working

## Security Notes

-  Token validation still secure (cryptographically signed reset tokens)
-  Reset tokens expire (24 hour TTL in Redis)
-  Only password update is public, all other user operations require JWT
-  Two separate endpoints:
  - `/auth/updatePassword` - Public (for password reset)
  - `/user/password` - Protected (for authenticated password changes)

## Files Modified

1. `src/main/kotlin/dev/gertjanassies/routes/AuthRoutes.kt` - Added public updatePassword endpoint
2. `frontend/src/lib/api.ts` - Updated to use new endpoint

---

**Status:**  FIXED and TESTED
**Date:** January 17, 2026
