# TODO - Password Reset Feature

## Backend ✅ (Completed)
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

## Frontend 🔄 (In Progress)
- [ ] Add "Forgot Password?" link to login popup
- [ ] Create `/reset-password` page that:
  - [ ] Extracts token from URL query parameter
  - [ ] Calls `/api/auth/verifyResetToken` to verify token
  - [ ] Shows password reset form if token is valid
  - [ ] Password form with two fields (password + confirm password)
  - [ ] Validation: passwords match, minimum 6 characters
  - [ ] Call `/api/user/password` to update password
  - [ ] Show success message and redirect to login
  - [ ] Show error message if token is invalid/expired

## Configuration
- [ ] Set RESEND_API_KEY environment variable in production
- [ ] Set APP_URL environment variable in production (e.g., https://yourdomain.com)

## Testing
- [ ] Test email sending in production with real Resend API key
- [ ] Test full password reset flow end-to-end
- [ ] Verify token expiry works (1 hour timeout)
- [ ] Test error cases (invalid token, expired token, etc.)

## Documentation
- [ ] Update README with password reset feature
- [ ] Document required environment variables
- [ ] Add API documentation for new endpoints

## Notes
- Email tokens expire after 1 hour
- Tokens are single-use (deleted after verification)
- Default app URL is http://localhost:5173 for development
- Generic error messages prevent information leakage to potential attackers
