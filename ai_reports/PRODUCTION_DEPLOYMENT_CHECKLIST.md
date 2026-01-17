# Production Deployment Checklist - JWT Authentication

## Pre-Deployment Verification ✅

- [x] All backend tests passing (165/165)
- [x] Frontend builds successfully
- [x] JWT authentication fully implemented
- [x] All code committed to git
- [x] Documentation complete

## Production Environment Setup

### 1. Set Environment Variables in Render.com

**Required:**
```bash
JWT_SECRET=<generate-strong-secret>  # Use command below to generate
```

**Generate JWT_SECRET:**
```bash
# On Mac/Linux:
openssl rand -base64 64

# Example output (use this as JWT_SECRET):
# kL9mN2pQ3rS4tU5vW6xY7zA8bC9dE0fG1hI2jK3lM4nO5pQ6rS7tU8vW9xY0zA1bC2dE3fG4hI5j==
```

**Other Environment Variables:**
```bash
SERVE_STATIC=true
APP_URL=https://medicate-kotlin.onrender.com
RESEND_API_KEY=<your-resend-api-key>
REDIS_URL=<your-redis-connection-string>
```

### 2. Deploy to Render.com

```bash
# Commit all changes
git add .
git commit -m "Complete JWT authentication implementation - backend and frontend"
git push origin main

# Render.com will automatically:
# 1. Build the backend (./gradlew build)
# 2. Build the frontend (cd frontend && npm run build)
# 3. Start the application
```

### 3. Post-Deployment Testing

**Test Authentication Flow:**

1. **Register New User**
   - Go to: https://medicate-kotlin.onrender.com
   - Click "Register"
   - Create test account
   - Verify:
     - User is logged in
     - Dashboard loads
     - Browser DevTools → Application → Local Storage shows:
       - `medicate_user` (user data)
       - `medicate_token` (JWT token)

2. **Test Protected Pages**
   - Navigate to: Dashboard, Medicines, Schedules, History
   - Verify: All pages load without errors
   - Check Network tab: All API requests have `Authorization: Bearer <token>` header

3. **Test Logout**
   - Click "Logout"
   - Verify:
     - Redirected to dashboard
     - Local Storage cleared (no medicate_user or medicate_token)
   - Try to navigate to Medicines page
   - Verify: Error message appears (no token)

4. **Test Login**
   - Click "Login"
   - Enter credentials
   - Verify:
     - Successfully logged in
     - Token appears in Local Storage
     - Protected pages work again

5. **Test Password Reset**
   - Click "Forgot Password"
   - Enter username
   - Check email for reset link
   - Click link and set new password
   - Login with new password
   - Verify: Works correctly

### 4. Security Verification

**Check JWT Token:**
```bash
# In browser DevTools → Console:
localStorage.getItem('medicate_token')

# Copy the token and decode it at: https://jwt.io
# Verify:
# - Header contains: {"alg":"HS256","typ":"JWT"}
# - Payload contains: {"aud":"medicate-users","iss":"medicate-app","username":"...","exp":...}
# - Token is properly formatted
```

**Test Token Validation:**
```bash
# Try to modify token in browser:
# DevTools → Console:
localStorage.setItem('medicate_token', 'fake-token')

# Refresh page or navigate
# Expected: Error "No authentication token found" or 401 Unauthorized
```

**Test Token Expiry:**
- Note: Tokens expire after 24 hours
- After 24 hours, verify user gets logged out automatically
- User should be prompted to login again

### 5. Monitor Logs

```bash
# In Render.com dashboard:
# 1. Go to your service
# 2. Click "Logs" tab
# 3. Watch for:
#    - Successful authentication logs
#    - No JWT validation errors
#    - No 401 errors for legitimate requests
```

### 6. Performance Check

- [ ] Login response time < 1 second
- [ ] Protected API endpoints respond < 500ms
- [ ] No token validation overhead noticeable
- [ ] Application feels responsive

## Rollback Plan (If Needed)

If issues occur in production:

```bash
# 1. Revert to previous deployment
git revert HEAD
git push origin main

# 2. Or manually rollback in Render.com dashboard
# Go to: Service → Deployments → Select previous deployment → Redeploy

# 3. Communicate to users
# - Clear their browser cache and localStorage
# - Ask them to login again
```

## Common Issues & Solutions

### Issue: "No authentication token found"
**Solution:** User needs to logout and login again to get new JWT token

### Issue: 401 Unauthorized on all requests
**Solutions:**
- Check JWT_SECRET is set correctly in production
- Verify frontend is sending Authorization header
- Check backend logs for JWT validation errors

### Issue: Token not being stored
**Solutions:**
- Check browser localStorage is enabled
- Verify frontend build includes latest api.ts changes
- Check browser console for JavaScript errors

### Issue: Login works but protected pages don't
**Solutions:**
- Verify getHeaders() is called correctly
- Check Authorization header format: `Bearer <token>`
- Verify all API endpoints are wrapped with authenticate("auth-jwt")

## Success Criteria

- [ ] New users can register and receive JWT token
- [ ] Existing users can login and receive JWT token
- [ ] All protected pages work with valid token
- [ ] Logout clears token completely
- [ ] Login required after logout
- [ ] No security errors in logs
- [ ] Application performs well
- [ ] Password reset flow works

## Post-Deployment

- [ ] Test with multiple user accounts
- [ ] Verify data isolation (users can't see each other's data)
- [ ] Monitor for 24 hours
- [ ] Document any issues
- [ ] Update team on deployment status

---

## Quick Reference

**JWT Token Location:**
```
Browser → DevTools → Application → Local Storage → https://medicate-kotlin.onrender.com
Key: medicate_token
```

**Check Token in API Request:**
```
Browser → DevTools → Network → Select any API request → Headers → Request Headers
Should see: Authorization: Bearer eyJhbGc...
```

**Backend JWT Config:**
```kotlin
// src/main/kotlin/dev/gertjanassies/Application.kt
install(Authentication) {
    jwt("auth-jwt") {
        // Uses JWT_SECRET from environment
        // Validates: signature, expiration, issuer, audience
    }
}
```

**All Protected Routes:**
```kotlin
authenticate("auth-jwt") {
    medicineRoutes(redisService)
    scheduleRoutes(redisService)
    dailyRoutes(redisService)
    dosageHistoryRoutes(redisService)
    adherenceRoutes(redisService)
}
```

---

**Deployment Date:** _________________
**Deployed By:** _________________
**All Tests Passing:** ✅
**Status:** Ready for Production 🚀
