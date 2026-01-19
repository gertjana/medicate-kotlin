pre# JWT Token Security Enhancement - HttpOnly Cookies

**Date:** January 19, 2026
**Status:** COMPLETE
**Tests:** All passing (178/178)

---

## Overview

Improved JWT token security by moving from localStorage to a more secure storage mechanism:
- **Access token:** Stored in memory (lost on page refresh)
- **Refresh token:** Stored in HttpOnly cookie (not accessible to JavaScript)

This significantly reduces the attack surface for XSS (Cross-Site Scripting) attacks.

---

## Security Improvement

### Before (Insecure)

**Storage:**
- Access token: localStorage (accessible to JavaScript)
- Refresh token: localStorage (accessible to JavaScript)

**Vulnerabilities:**
- Both tokens vulnerable to XSS attacks
- Any malicious script can steal tokens from localStorage
- Tokens persist across browser sessions

### After (Secure)

**Storage:**
- Access token: Memory variable (lost on page refresh/close)
- Refresh token: HttpOnly cookie (not accessible to JavaScript)

**Security Benefits:**
- Access token: Cannot be stolen via XSS (not in DOM/localStorage)
- Refresh token: Protected by HttpOnly flag (JavaScript cannot read it)
- Automatic token refresh on page load
- Reduced token lifetime exposure

---

## Implementation Details

### Backend Changes

#### 1. UserRoutes - Login & Register

Updated to set refresh token as HttpOnly cookie instead of returning it in response:

```kotlin
// Generate JWT tokens
val accessToken = jwtService.generateAccessToken(user.username)
val refreshToken = jwtService.generateRefreshToken(user.username)

// Set refresh token as HttpOnly cookie
call.response.cookies.append(
    io.ktor.http.Cookie(
        name = "refresh_token",
        value = refreshToken,
        maxAge = 30 * 24 * 60 * 60, // 30 days in seconds
        httpOnly = true,
        secure = false, // Set to true in production with HTTPS
        path = "/",
        extensions = mapOf("SameSite" to "Strict")
    )
)

// Return response WITHOUT refresh token
call.respond(
    HttpStatusCode.Created,
    AuthResponse(user = user.toResponse(), token = accessToken, refreshToken = "")
)
```

#### 2. AuthRoutes - Token Refresh

Updated to read refresh token from cookie instead of request body:

```kotlin
post("/refresh") {
    // Get refresh token from HttpOnly cookie
    val refreshToken = call.request.cookies["refresh_token"]

    if (refreshToken.isNullOrBlank()) {
        call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Refresh token is required"))
        return@post
    }

    // Validate and generate new access token
    val username = jwtService.validateRefreshToken(refreshToken)
    if (username == null) {
        call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired refresh token"))
        return@post
    }

    val newAccessToken = jwtService.generateAccessToken(username)

    // Return only access token (refresh token stays in cookie)
    call.respond(HttpStatusCode.OK, mapOf("token" to newAccessToken))
}
```

#### 3. AuthRoutes - Logout

Added new logout endpoint to clear the HttpOnly cookie:

```kotlin
post("/logout") {
    // Clear the refresh token cookie
    call.response.cookies.append(
        io.ktor.http.Cookie(
            name = "refresh_token",
            value = "",
            maxAge = 0, // Expire immediately
            httpOnly = true,
            secure = false,
            path = "/"
        )
    )

    logger.debug("User logged out, refresh token cookie cleared")
    call.respond(HttpStatusCode.OK, mapOf("message" to "Logged out successfully"))
}
```

### Frontend Changes

#### 1. In-Memory Access Token Storage

```typescript
// Store access token in memory (not localStorage for security)
let accessToken: string | null = null;

export function getAccessToken(): string | null {
    return accessToken;
}

export function setAccessToken(token: string | null): void {
    accessToken = token;
}
```

#### 2. Updated Headers Function

```typescript
function getHeaders(includeContentType: boolean = false): HeadersInit {
    const headers: HeadersInit = {};

    // Get JWT token from memory (not localStorage)
    if (browser && accessToken) {
        headers['Authorization'] = `Bearer ${accessToken}`;
    }

    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }

    return headers;
}
```

#### 3. Token Refresh Function

```typescript
async function refreshAccessToken(): Promise<boolean> {
    if (!browser) return false;

    try {
        const response = await fetch(`${API_BASE}/auth/refresh`, {
            method: 'POST',
            credentials: 'include' // Include cookies
        });

        if (!response.ok) {
            return false;
        }

        const data = await response.json();
        // Store new access token in memory
        setAccessToken(data.token);
        return true;
    } catch (e) {
        console.error('Failed to refresh token:', e);
        return false;
    }
}
```

#### 4. Login/Register Functions

```typescript
export async function loginUser(username: string, password: string): Promise<User> {
    const response = await fetch(`${API_BASE}/user/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password }),
        credentials: 'include' // Include cookies
    });

    if (!response.ok) throw new Error('Failed to login');

    const authResponse: AuthResponse = await response.json();

    // Store user in localStorage and access token in memory
    // Refresh token is in HttpOnly cookie (set by server)
    if (browser) {
        localStorage.setItem('medicate_user', JSON.stringify(authResponse.user));
        setAccessToken(authResponse.token);
    }

    return authResponse.user;
}
```

#### 5. Logout Function

```typescript
export async function logout(): Promise<void> {
    if (browser) {
        // Call backend to clear HttpOnly cookie
        try {
            await fetch(`${API_BASE}/auth/logout`, {
                method: 'POST',
                credentials: 'include'
            });
        } catch (e) {
            console.error('Failed to logout on server:', e);
        }

        // Clear user from localStorage and access token from memory
        localStorage.removeItem('medicate_user');
        setAccessToken(null);
    }
}
```

#### 6. User Store Initialization

Updated to automatically refresh access token on page load:

```typescript
init: async () => {
    if (browser) {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
            try {
                const user = JSON.parse(stored);
                set(user);

                // Access token is lost on page refresh (in memory)
                // Try to refresh it using the HttpOnly cookie
                if (!getAccessToken()) {
                    try {
                        const response = await fetch('/api/auth/refresh', {
                            method: 'POST',
                            credentials: 'include'
                        });
                        if (response.ok) {
                            const data = await response.json();
                            setAccessToken(data.token);
                        } else {
                            // Refresh token expired or invalid, logout
                            this.logout();
                        }
                    } catch (e) {
                        console.error('Failed to refresh token on init:', e);
                        this.logout();
                    }
                }
            } catch (e) {
                localStorage.removeItem(STORAGE_KEY);
            }
        }
    }
}
```

#### 7. Authenticated Fetch

All API requests now include credentials for cookies:

```typescript
async function authenticatedFetch(url: string, options: RequestInit = {}): Promise<any> {
    const makeRequest = () => {
        // ...headers setup...

        return fetch(url, {
            ...options,
            headers,
            credentials: 'include' // Include cookies for refresh token
        });
    };

    // ...rest of function...
}
```

---

## Test Updates

Updated all tests to verify HttpOnly cookie behavior:

### 1. Register/Login Tests

```kotlin
test("should register a new user successfully and return JWT token") {
    // ...test setup...

    response.status shouldBe HttpStatusCode.Created
    val body = response.body<AuthResponse>()
    body.token shouldBe "test-access-token-123"
    // Refresh token should not be in response body (empty string)
    body.refreshToken shouldBe ""

    // Verify refresh token cookie was set
    val cookies = response.setCookie()
    val refreshCookie = cookies.find { it.name == "refresh_token" }
    refreshCookie shouldNotBe null
    refreshCookie?.value shouldBe "test-refresh-token-456"
    refreshCookie?.httpOnly shouldBe true
}
```

### 2. Refresh Token Tests

```kotlin
test("should refresh access token with valid refresh token") {
    // ...mock setup...

    testApplication {
        // ...setup...

        val response = client.post("/auth/refresh") {
            cookie("refresh_token", refreshToken)  // Send as cookie
        }

        response.status shouldBe HttpStatusCode.OK
        val body = response.body<Map<String, String>>()
        body shouldContainKey "token"
        body["token"] shouldBe newAccessToken
        // Refresh token should not be in response
        (body.containsKey("refreshToken")) shouldBe false
    }
}
```

---

## Cookie Configuration

### Development Settings

```kotlin
Cookie(
    name = "refresh_token",
    value = refreshToken,
    maxAge = 30 * 24 * 60 * 60,  // 30 days
    httpOnly = true,               // Cannot be accessed by JavaScript
    secure = false,                // HTTP allowed (for local development)
    path = "/",                    // Available to all paths
    extensions = mapOf("SameSite" to "Strict")  // CSRF protection
)
```

### Production Settings

For production deployment with HTTPS:

```kotlin
Cookie(
    name = "refresh_token",
    value = refreshToken,
    maxAge = 30 * 24 * 60 * 60,  // 30 days
    httpOnly = true,               // Cannot be accessed by JavaScript
    secure = true,                 // HTTPS only (CHANGE THIS)
    path = "/",
    extensions = mapOf("SameSite" to "Strict")
)
```

**Important:** Set `secure = true` in production to ensure cookies are only sent over HTTPS.

---

## Security Benefits

### 1. XSS Attack Protection

**Scenario:** Malicious script injected into page

**Before:**
```javascript
// Attacker can steal tokens
const token = localStorage.getItem('medicate_token');
const refreshToken = localStorage.getItem('medicate_refresh_token');
sendToAttacker(token, refreshToken);
```

**After:**
```javascript
// Attacker CANNOT access tokens
const token = accessToken; // undefined (not in global scope)
const refreshToken = document.cookie; // Empty (HttpOnly prevents access)
```

### 2. Token Lifetime Reduction

**Access Token:**
- Lifetime: 1 hour
- Storage: Memory only
- Lost on: Page refresh, browser close, navigation
- Impact: Minimal exposure window

**Refresh Token:**
- Lifetime: 30 days
- Storage: HttpOnly cookie
- Protected: Cannot be read by JavaScript
- Impact: Long session without compromising security

### 3. Automatic Session Recovery

When user refreshes page:
1. Access token lost (was in memory)
2. App automatically calls `/api/auth/refresh`
3. Server reads HttpOnly cookie
4. New access token generated and stored in memory
5. User stays logged in seamlessly

---

## Migration Notes

### Breaking Changes

**localStorage is no longer used for tokens:**
- Old key `medicate_token` - REMOVED
- Old key `medicate_refresh_token` - REMOVED
- Only `medicate_user` remains in localStorage

**Existing users:**
- Will be logged out on first page load after update
- Need to log in again to get new HttpOnly cookie
- This is a one-time inconvenience for improved security

### Deployment Steps

1. Deploy backend with HttpOnly cookie support
2. Deploy frontend with memory-based token storage
3. Existing users will see logout on next page load
4. Users log in again (gets HttpOnly cookie)
5. Improved security from this point forward

---

## Testing

### Manual Testing

**Test Token Storage:**
1. Login to application
2. Open Browser DevTools > Application > Local Storage
3. Verify: Only `medicate_user` exists (no token keys)
4. Check: Application > Cookies
5. Verify: `refresh_token` cookie exists with HttpOnly flag

**Test Page Refresh:**
1. Login to application
2. Navigate to any protected page
3. Refresh the page
4. Verify: User stays logged in (automatic token refresh)

**Test Logout:**
1. Login to application
2. Click logout
3. Check cookies: `refresh_token` should be gone
4. Verify: Cannot access protected pages

### Automated Testing

All 178 tests passing:
- Login/Register tests verify cookie creation
- Refresh tests verify cookie-based authentication
- Profile tests verify protected endpoints work
- Logout clears cookies properly

---

## Files Modified

### Backend
1. `src/main/kotlin/dev/gertjanassies/routes/UserRoutes.kt` - HttpOnly cookies on login/register
2. `src/main/kotlin/dev/gertjanassies/routes/AuthRoutes.kt` - Cookie-based refresh + logout endpoint

### Frontend
3. `frontend/src/lib/api.ts` - Memory token storage, cookie-based refresh
4. `frontend/src/lib/stores/user.ts` - Auto-refresh on init
5. `frontend/src/routes/+layout.svelte` - Async logout

### Tests
6. `src/test/kotlin/dev/gertjanassies/routes/UserRoutesTest.kt` - Cookie assertions
7. `src/test/kotlin/dev/gertjanassies/routes/AuthRoutesTest.kt` - Cookie-based refresh tests

**Total: 7 files modified**

---

## Production Checklist

- [ ] Set `secure = true` in cookie configuration (HTTPS only)
- [ ] Verify CORS settings allow credentials
- [ ] Test token refresh works over HTTPS
- [ ] Verify cookies work across subdomains (if needed)
- [ ] Monitor failed refresh attempts
- [ ] Document one-time user re-login requirement

---

## Summary

Successfully migrated from insecure localStorage token storage to secure HttpOnly cookie + memory storage:

**Security Improvements:**
- Access tokens no longer accessible via XSS
- Refresh tokens protected by HttpOnly flag
- Reduced token exposure window
- Automatic session recovery on page refresh

**User Experience:**
- Seamless login experience
- Automatic token refresh
- Users stay logged in across page refreshes
- One-time re-login required after deployment

**Status:** COMPLETE - All tests passing (178/178)
