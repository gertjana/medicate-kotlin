# User Activation and Security Improvements

## Overview
Implemented comprehensive security improvements including user activation flow, information disclosure prevention, and rate limiting.

## Changes Implemented

### 1. User Activation Flow

#### Backend Changes
- Added `isActive` boolean field to `User` model
- Updated `registerUser` in `RedisService` to create inactive users by default
- Created `activateUser` method in `RedisService` to activate users by activation token
- Added `sendVerificationEmail` method in `EmailService` to send activation emails
- Modified login flow to check if user is active before allowing login
- Created `/auth/activate` endpoint to handle activation link clicks

#### Frontend Changes
- Updated registration flow to show message about checking email
- Registration no longer automatically logs user in
- Added activation page at `/activate` route to handle activation tokens

#### Database Schema
Redis keys:
- `medicate:{env}:user:id:{userId}` - Now includes `isActive` field
- `medicate:{env}:activation:{token}` - Stores userId for activation tokens (24h TTL)

### 2. Information Disclosure Prevention

#### Password Reset
- Changed error messages to generic "If an account exists..." message
- Prevents attackers from enumerating valid email addresses
- Returns same response whether email exists or not

#### Login
- Generic error message for both invalid username and wrong password
- Prevents username enumeration attacks

#### Registration
- Consistent error messages that don't reveal if username/email already exists
- Same response time regardless of success or failure

### 3. Rate Limiting (nginx)

Added rate limiting to sensitive endpoints in `deployment/nginx.conf`:
- `/api/user/register`: 5 requests per minute
- `/api/user/login`: 10 requests per minute
- `/api/auth/resetPassword`: 5 requests per minute
- `/api/auth/activate`: 10 requests per minute

Configuration:
```nginx
limit_req_zone $binary_remote_addr zone=register_limit:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=login_limit:10m rate=10r/m;
limit_req_zone $binary_remote_addr zone=reset_limit:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=activate_limit:10m rate=10r/m;
```

Prevents brute force attacks and DoS attempts.

### 4. User Data Model Migration

#### From Username-Based to User ID-Based Storage

**Before:**
- Data stored using username as key: `medicate:{env}:user:{username}:medicine:{id}`
- Usernames were unique identifiers
- Multiple users couldn't have same username even with different emails

**After:**
- Data stored using UUID as key: `medicate:{env}:user:id:{userId}:medicine:{id}`
- Username is just a display name, can be duplicated
- Email address is the true unique identifier
- User ID (UUID) is the primary key for all user data

**Index Structure:**
- `medicate:{env}:user:username:{username}` - Stores comma-separated list of user IDs
- `medicate:{env}:user:email:{email}` - Stores user ID (unique)
- `medicate:{env}:user:id:{userId}` - Stores user data

**Benefits:**
- Allows multiple users with same username but different emails
- Prevents username enumeration (multiple IDs per username)
- Better privacy and security
- Easier user data management

#### JWT Token Changes
- Tokens now embed `userId` instead of `username`
- All authenticated endpoints extract `userId` from JWT
- Routes validate and pass `userId` to storage methods
- No more `getUser()` calls in request handling

### 5. Storage Service Interface

Created `StorageService` interface to abstract Redis implementation:
- All public methods defined in interface
- `RedisService` implements interface
- Enables future alternative storage backends
- Improves testability and maintainability

## Testing

All tests updated and passing:
- 189 tests completed successfully
- Updated all route tests to use JWT with userId
- Updated all service tests to use userId instead of username
- Added tests for activation flow
- Added tests for generic error messages

## Security Benefits

1. **Account Enumeration Prevention**
   - Attackers cannot determine valid emails/usernames
   - Same response for existing and non-existing accounts

2. **Rate Limiting**
   - Prevents brute force password attacks
   - Mitigates DoS attempts
   - Slows down automated enumeration attempts

3. **Email Verification**
   - Confirms user owns the email address
   - Prevents fake account creation
   - Reduces spam and abuse

4. **User Privacy**
   - Usernames are no longer unique identifiers
   - Multiple users can share usernames
   - Harder to track specific users

## Migration Notes

For production deployment:
1. Existing users will need to be migrated with `isActive = true`
2. Use provided migration script: `migrate-to-user-ids.sh`
3. Migration preserves all user data while adding UUID-based keys
4. Old username-based keys remain for rollback safety

## Configuration

### Environment Variables
- `APP_URL` - Base URL for activation links (defaults to http://localhost)
- `RESEND_API_KEY` - API key for email service
- `JWT_SECRET` - Secret for JWT token signing

### Activation Email Template
Subject: Activate Your Medicate Account
Body includes:
- User's first name (if available) or "there"
- Activation link valid for 24 hours
- Clear call-to-action button

## Future Improvements

1. Add password strength requirements
2. Implement CAPTCHA for registration/login
3. Add two-factor authentication
4. Session management improvements
5. Account lockout after failed attempts
6. Audit logging for security events

## Rollback Plan

If issues arise:
1. Revert nginx rate limiting configuration
2. Set all existing users to `isActive = true`
3. Keep UUID-based storage (backward compatible)
4. Email verification can be made optional via config flag

## Date Completed
January 23, 2026
