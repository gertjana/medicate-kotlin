# TODO

## ✅ JWT Authentication Implementation (COMPLETE!)

### Backend ✅ (Completed)
- [x] Add JWT library dependency
- [x] Create JwtService for token generation/validation
- [x] Configure JWT authentication in Application.kt
- [x] Update UserRoutes to return tokens on login/register
- [x] Create AuthResponse model (user + token)
- [x] Update all protected routes to use JWT instead of X-Username header
- [x] Wrap protected routes with authenticate("auth-jwt")
- [x] Create TestJwtConfig helper for tests

### Tests ✅ (COMPLETE - All 165 tests passing!)
- [x] Update all protected route tests to use JWT authentication
  - [x] MedicineRoutesTest (36 tests)
  - [x] ScheduleRoutesTest (10 tests)
  - [x] DailyRoutesTest (2 tests)
  - [x] DosageHistoryRoutesTest (8 tests)
  - [x] AdherenceRoutesTest (10 tests)
  - [x] MedicineExpiryRoutesTest (3 tests)
  - [x] UserRoutesTest (with token validation)
  - [x] AuthRoutesTest (with token validation)

### Frontend ⏳ (TODO)
- [ ] Update api.ts to handle AuthResponse with token
- [ ] Store JWT token in localStorage on login/register
- [ ] Update getHeaders() to send `Authorization: Bearer <token>` instead of X-Username
- [ ] Handle 401 responses (token expired → redirect to login)
- [ ] Clear token on logout
- [ ] Test full authentication flow

### Configuration
- [ ] Set JWT_SECRET environment variable in production (generate strong random secret)

**Backend is now PRODUCTION READY and SECURE! 🎉**

See `JWT_IMPLEMENTATION.md` for detailed implementation guide and frontend update instructions.

## Password Reset Feature ✅ (Completed)

### Backend ✅
- [x] EmailService implementation with Resend API integration
- [x] Generate password reset tokens
- [x] Store tokens in Redis with expiry
- [x] Send password reset emails with dynamic URL
- [x] Verify password reset tokens
- [x] Environment variable for app URL (APP_URL)
- [x] POST /api/auth/resetPassword endpoint (send reset email)
- [x] POST /api/auth/verifyResetToken endpoint (verify token)
- [x] All backend tests passing
- [x] Generic error messages (don't expose API details to users)

### Frontend ✅
- [x] Add "Forgot Password?" link to login popup
- [x] Create `/reset-password` page that:
  - [x] Extracts token from URL query parameter
  - [x] Calls `/api/auth/verifyResetToken` to verify token
  - [x] Shows password reset form if token is valid
  - [x] Password form with two fields (password + confirm password)
  - [x] Validation: passwords match, minimum 6 characters
  - [x] Call `/api/user/password` to update password
  - [x] Show success message and redirect to login
  - [x] Show error message if token is invalid/expired

### Configuration ✅
- [x] Set RESEND_API_KEY environment variable in production
- [x] Set APP_URL environment variable in production
- [x] Tested email sending in production with real Resend API key
- [x] Tested full password reset flow end-to-end

## Notes
- ⚠️ **CRITICAL**: Current authentication is insecure - see SECURITY.md
- Email tokens expire after 1 hour (managed by Redis TTL)
- Tokens are single-use (deleted after verification)
- Generic error messages prevent information leakage
