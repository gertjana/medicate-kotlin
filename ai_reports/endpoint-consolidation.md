# Email Verification Endpoint Consolidation

## Problem

The codebase had two separate endpoints for email verification that performed the same functionality:

1. **GET /api/user/verify-email?token=xxx** - Accepts verification token as query parameter
2. **POST /api/auth/activateAccount** - Accepts verification token in request body

Both endpoints:
- Verified the activation token
- Activated the user account
- Generated JWT tokens
- Set refresh token as HttpOnly cookie
- Returned user information and access token

This duplication created API inconsistency and potential confusion for API consumers.

## Solution

Consolidated the functionality into a single endpoint by:

1. **Removed** the GET /api/user/verify-email endpoint from UserRoutes.kt
2. **Kept** the POST /api/auth/activateAccount endpoint in AuthRoutes.kt
3. **Updated** the email verification link in EmailService to point to a frontend URL (/activate-account?token=xxx) instead of directly to the API endpoint

## Rationale

The POST endpoint was chosen because:
- POST is more appropriate for state-changing operations (activating an account)
- The /auth route is semantically more appropriate for authentication/activation operations
- Sending sensitive tokens in request body (POST) is more secure than in URL query parameters (GET)
- The email should link to a frontend page that handles the user experience, not directly to an API endpoint

## Changes Made

### Files Modified

1. **src/main/kotlin/dev/gertjanassies/routes/UserRoutes.kt**
   - Removed the entire GET /verify-email endpoint (64 lines)
   - Removed unused cleanup code for deleteUser (which didn't exist)

2. **src/main/kotlin/dev/gertjanassies/service/EmailService.kt**
   - Changed verification link from `/verify-email?token=` to `/activate-account?token=`

3. **src/test/kotlin/dev/gertjanassies/service/EmailServiceTest.kt**
   - Updated test assertion to check for `/activate-account?token=` instead of `/verify-email?token=`

## Impact

- API surface reduced by one endpoint
- Clearer separation of concerns between /user and /auth routes
- Better security by not exposing tokens in URL query parameters
- Frontend application will need to implement an /activate-account page that:
  - Extracts the token from the URL
  - Makes a POST request to /api/auth/activateAccount with the token
  - Handles the response (success or error)

## Testing

All existing tests pass after the changes:
- EmailService tests verify the correct link format
- AuthRoutes tests verify the POST /auth/activateAccount endpoint works correctly
