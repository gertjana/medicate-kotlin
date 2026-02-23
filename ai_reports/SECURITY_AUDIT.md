# Security Audit Report — Medicate

**Date:** 2026-02-23
**Auditor:** OpenCode (claude-sonnet-4.6)
**Scope:** Full stack — Backend (Kotlin/Ktor), Frontend (SvelteKit), Config/Infrastructure/CI

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 4 |
| HIGH | 12 |
| MEDIUM | 13 |
| LOW | 13 |
| **Total** | **42** |

---

## CRITICAL

### B-CRIT-1: Hardcoded Fallback JWT Secret
**File:** `src/main/kotlin/dev/gertjanassies/Application.kt:126`

If `JWT_SECRET` env var is unset, the app silently falls back to `"default-secret-change-in-production"` — a string that is public in source code. Any attacker can forge valid JWT tokens for any user, including admins.

**Fix:** Throw a fatal exception at startup if `JWT_SECRET` is missing:
```kotlin
val jwtSecret = System.getenv("JWT_SECRET")
    ?: throw IllegalStateException("JWT_SECRET environment variable must be set")
```
Also enforce a minimum key length (≥32 characters).

---

### B-CRIT-2: Unauthenticated Password Change Endpoint
**File:** `src/main/kotlin/dev/gertjanassies/routes/UserRoutes.kt:148`

`PUT /api/user/password` is in the public (unauthenticated) route block. It accepts only `username` + `newPassword` with no JWT, no current-password check, and no reset token. Anyone who knows a username can take over the account.

**Fix:** Remove this endpoint entirely (the `/auth/updatePassword` reset flow already exists), or move it inside `authenticate("auth-jwt")` and require the current password.

---

### B-CRIT-3: Password Reset Flow Does Not Require Token
**File:** `src/main/kotlin/dev/gertjanassies/routes/AuthRoutes.kt:250`, `frontend/src/lib/api.ts:427`

After `/verifyResetToken` the token is deleted and the username is returned to the frontend. The final `PUT /api/auth/updatePassword` step accepts only `username` + `password` — the token is never sent again. Any caller who knows a username can set a new password without ever owning a reset token.

**Fix:** After verifying the reset token, issue a short-lived signed credential (e.g., a one-time JWT with a `password_reset` claim and 5-minute expiry). Require this credential in `updatePassword` and verify it server-side.

---

### I-CRIT-1: Live API Key Stored in `.env`
**File:** `.env:5`

`RESEND_API_KEY=re_ALM7ix9G_...` is a real live Resend API key stored on disk (the file is gitignored so it is not in git history, but the key should still be rotated).

**Fix:**
1. Rotate the key immediately at https://resend.com/api-keys
2. Replace the value with a placeholder: `RESEND_API_KEY=your-resend-api-key-here`
3. Rename the file to `.env.example` as a documented template

---

## HIGH

### B-HIGH-1: Refresh Token Cookie `secure=false`
**Files:** `UserRoutes.kt:131`, `AuthRoutes.kt:229`

The 30-day HttpOnly refresh token cookie is explicitly set with `secure=false`, transmitting it over plain HTTP.

**Fix:** Set `secure = System.getenv("APP_ENV") != "test"` (or unconditionally `true` if always behind HTTPS).

---

### B-HIGH-2: Logout Does Not Invalidate Refresh Token Server-Side
**File:** `AuthRoutes.kt:71`

Logout only clears the client-side cookie. A captured refresh token remains valid for 30 days.

**Fix:** Store refresh tokens in Redis on issue (`refresh:token:{hash}` → userId, TTL 30 days). On logout, delete the entry. On `/auth/refresh`, verify the token exists in Redis before issuing a new access token.

---

### B-HIGH-3: No Rate Limiting on Login, Registration, or Password Reset
**Files:** `UserRoutes.kt`, `AuthRoutes.kt`

No rate limiting at the application layer. Brute-force of passwords is possible at full network speed. The `/resetPassword` endpoint can be used to rack up Resend API costs. Note: nginx does apply IP-based rate limiting, but there is no application-layer lockout.

**Fix:** Add account-level lockout in Redis after N failed login attempts (e.g., 5 failures → 15-minute lock). Complement the existing nginx IP-based limits.

---

### B-HIGH-4: Client Controls Dosage Timestamps
**File:** `model/request/DosageHistoryRequest.kt:16`

Clients can supply arbitrary past/future `datetime` values for dose records, directly inflating adherence statistics. In a medical tracking app this is an integrity concern.

**Fix:** Reject client-supplied `datetime` and always use `LocalDateTime.now()` server-side, or restrict to a narrow window (e.g., ±2 hours).

---

### B-HIGH-5: `deleteUserCompletely` Uses Wrong Redis Key Patterns — Data Never Deleted
**File:** `RedisService.kt:1422`

The deletion patterns use `medicine:$userId:*` but actual keys follow `user:$userId:medicine:*`. All user medicine, schedule, and dosage history data is permanently orphaned in Redis when an admin deletes a user. This is a GDPR compliance failure.

**Fix:**
```kotlin
val medicinePattern = "$keyPrefix:user:$userId:medicine:*"
val schedulePattern = "$keyPrefix:user:$userId:schedule:*"
val historyPattern  = "$keyPrefix:user:$userId:dosagehistory:*"
```

---

### B-HIGH-6: No Token Invalidation on Password Change
**File:** `RedisService.kt:1006`

When a password is changed, existing access tokens (1 hr) and refresh tokens (30 days) remain valid. An attacker with a stolen session retains access after the victim changes their password.

**Fix:** On password change, delete the user's stored refresh token (see B-HIGH-2) and embed a `passwordVersion` counter in JWT tokens, rejecting tokens whose version doesn't match.

---

### F-HIGH-1: SvelteKit API Proxy Routes Drop the Authorization Header
**Files:** `frontend/src/routes/api/*/+server.ts`

All server-side proxy handlers forward requests to the Kotlin backend without forwarding the `Authorization` header from the incoming request. Any requests through these proxy routes are unauthenticated.

**Fix:** Either remove these unused proxy handlers (the client talks directly to the backend via the Vite/nginx proxy), or forward the header:
```typescript
headers: { 'Authorization': request.headers.get('Authorization') ?? '' }
```

---

### F-HIGH-2: Login Proxy Drops `Set-Cookie` — Refresh Token Never Reaches the Browser
**File:** `frontend/src/routes/api/user/login/+server.ts`

The login proxy returns only `Content-Type`, silently dropping the backend's `Set-Cookie` response header. The refresh token HttpOnly cookie is never delivered to the browser, breaking the entire token-refresh mechanism.

**Fix:**
```typescript
const setCookie = res.headers.get('set-cookie');
if (setCookie) responseHeaders['Set-Cookie'] = setCookie;
```

---

### F-HIGH-3: Account Activation Logs User In Without an Access Token
**File:** `frontend/src/routes/activate-account/+page.svelte:36`

After activation `userStore.login()` is called, but `setAccessToken()` is never called. The user appears logged in but every API call fails with 401. The activation server proxy also doesn't forward `Set-Cookie`.

**Fix:** Call `setAccessToken(data.token)` after activation, and forward `Set-Cookie` in the activation proxy response.

---

### I-HIGH-1: No TLS — HTTP Only on Port 80
**Files:** `deployment/nginx.conf:32`, `docker-compose.yml:7`

All traffic (JWT tokens, refresh cookies, health data) is transmitted in plaintext.

**Fix:** Obtain a TLS certificate (Let's Encrypt) and add HTTPS listener to nginx with HTTP→HTTPS redirect. Expose port 443 in docker-compose.

---

### I-HIGH-2: Unpinned CI Action (`@master`)
**File:** `.github/workflows/update-medicines.yml:73`

`ad-m/github-push-action@master` is a mutable tag. A supply-chain compromise could inject code into a workflow with `contents: write` permissions.

**Fix:** Pin to a specific commit SHA:
```yaml
uses: ad-m/github-push-action@31938da2c831af3f8b8d5b2f487cbfc96ee18ac8
```

---

## MEDIUM

### B-MED-1: No Input Length Limits on Any Field
**Files:** All request models

Username, password, medicine name, description, etc. are all unbounded strings stored directly in Redis.

**Fix:** Add server-side length validation. Suggested limits: username ≤ 64, email ≤ 254, password ≤ 128, names ≤ 100, description ≤ 1000 characters.

---

### B-MED-2: Missing Security Headers
**File:** `Application.kt`

No CSP, X-Frame-Options, X-Content-Type-Options, HSTS, or Referrer-Policy.

**Fix:**
```kotlin
install(DefaultHeaders) {
    header("X-Content-Type-Options", "nosniff")
    header("X-Frame-Options", "DENY")
    header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
    header("Referrer-Policy", "strict-origin-when-cross-origin")
}
```

---

### B-MED-3: `Accept-Language` Header Used in File Path Without Allowlisting
**File:** `UserRoutes.kt:62`

The 2-char locale is used to build a resource path. `take(2)` limits the string but doesn't sanitize it.

**Fix:** Allowlist explicitly: `val locale = if (rawLocale in setOf("en", "nl")) rawLocale else "en"`

---

### B-MED-4: Internal Error Messages Returned to Clients
**Files:** Multiple routes (e.g., `MedicineRoutes.kt:42`, `ScheduleRoutes.kt:35`)

Raw Redis exception messages, key names, and internal details are passed into HTTP responses.

**Fix:** Log the full error server-side and return a generic message: `"An internal error occurred. Please try again."`

---

### B-MED-5: Minimum Password Length Is 6 Characters
**Files:** `UserRoutes.kt:44`, `AuthRoutes.kt:263`

Well below NIST SP 800-63B minimum of 8.

**Fix:** Increase to minimum 8 characters, ideally 12.

---

### B-MED-6: Username Disclosed After Reset Token Verification
**File:** `AuthRoutes.kt:169`

`/verifyResetToken` returns the username to the frontend, which then sends it as the sole credential in the `updatePassword` step. This amplifies B-CRIT-3.

**Fix:** Eliminate the username roundtrip as part of the B-CRIT-3 fix. The short-lived credential issued after token verification should carry the username server-side.

---

### F-MED-1: User PII and `isAdmin` Flag Stored in `localStorage`
**Files:** `api.ts:389`, `user.ts:16`

`localStorage` is accessible to any same-origin JavaScript. If XSS is ever introduced, the user's name, email, and admin flag are immediately exfiltrable.

**Fix:** Store only a minimal non-sensitive session indicator (username only) in `localStorage`. Never store `isAdmin` client-side.

---

### F-MED-2: `{@html}` Used with i18n Strings
**Files:** `src/routes/+page.svelte:260`, `src/routes/history/+page.svelte:221`

Safe today (compile-time strings), but sets a dangerous precedent and becomes XSS if translations are ever loaded dynamically.

**Fix:** Use Svelte component composition instead of `{@html}` for any string that could eventually contain user data.

---

### F-MED-3: No `hooks.server.ts` — No Security Headers or CSRF Protection
**File:** (missing)

No server-side security headers, CSP, or CSRF protection at the SvelteKit layer.

**Fix:** Create `src/hooks.server.ts` with security headers set on every response.

---

### F-MED-4: Reset/Activation Tokens in URL Query Params
**File:** `src/routes/reset-password/+page.svelte:19`

Tokens appear in server access logs, browser history, and `Referer` headers sent to Google Fonts.

**Fix:** Use URL fragments (`#token=...`) which are never sent to servers, or POST-to-form flows. At minimum add `Referrer-Policy: no-referrer` and consider self-hosting fonts (see F-LOW-3).

---

### F-MED-5: `isLoggedIn()` Checks a Key That Is Never Set — Always Returns `false`
**File:** `api.ts:507`

```typescript
localStorage.getItem('medicate_token')  // This key is never set anywhere
```
The actual key is `medicate_user`. Every logged-in user is reported as logged out.

**Fix:**
```typescript
return accessToken !== null || localStorage.getItem('medicate_user') !== null;
```

---

### I-MED-1: Scheduled Workflow Commits Unreviewed Data Directly to `main`
**File:** `.github/workflows/update-medicines.yml:65`

A compromised upstream CSV source could inject a malicious SQLite database into production images.

**Fix:** Have the workflow open a Pull Request instead of committing directly to `main`.

---

### I-MED-2: Redis Connection Has No TLS
**File:** `RedisService.kt:68`

`RedisURI.Builder.redis()` creates a plain TCP connection. If Redis is not co-located, all data transits in plaintext.

**Fix:** Use `RedisURI.Builder.rediss()` (double `s`) for TLS, or make it configurable via `REDIS_TLS=true`.

---

### I-MED-3: `APP_ENV=test` Hard-Coded in Production `docker-compose.yml`
**File:** `docker-compose.yml:11`

Production data lives under `medicate:test:` keys, colliding with actual test data.

**Fix:** Change to `APP_ENV=production` or `APP_ENV=${APP_ENV:-production}`.

---

## LOW

### B-LOW-1: BCrypt Work Factor 10 (OWASP Recommends ≥12)
**File:** `RedisService.kt:924`

`BCrypt.gensalt()` defaults to work factor 10. OWASP recommends a minimum of 12 for new systems.

**Fix:** `BCrypt.gensalt(12)`

---

### B-LOW-2: `User.isActive` Defaults to `true`
**File:** `model/User.kt:16`

Registration correctly passes `isActive = false`, but any future `User()` call without specifying it creates an active account.

**Fix:** Change default to `val isActive: Boolean = false`.

---

### B-LOW-3: Missing `SameSite` on Activation-Flow Cookie
**File:** `AuthRoutes.kt:224`

Login cookie correctly sets `SameSite=Strict` but the activation-issued cookie doesn't.

**Fix:** Add `extensions = mapOf("SameSite" to "Strict")` to the activation cookie.

---

### B-LOW-4: Password Reset Token Uses Redis `SCAN` with Token in Glob Pattern
**File:** `RedisService.kt:1167`

O(N) scan with the token embedded in the pattern. A token value containing `*` could match multiple keys. The activation token flow already uses the correct O(1) direct `GET` approach.

**Fix:** Store as `$keyPrefix:password_reset:token:$token` and retrieve with a direct `GET`, consistent with activation tokens.

---

### F-LOW-1: `window.location.reload()` on Session Expiry
**File:** `api.ts:174`

Multiple simultaneous 401 responses can trigger rapid successive reloads. User loses all unsaved data silently.

**Fix:** Use `goto('/?sessionExpired=true', { replaceState: true })` instead.

---

### F-LOW-2: `userStore.init()` `this.logout()` Call Silently Fails
**File:** `src/lib/stores/user.ts:56`

`this` in this context is not the store object. The logout call throws a TypeError silently, leaving the user store in a broken logged-in-but-no-token state.

**Fix:** Replace `this.logout()` with explicit store reset and direct API call.

---

### F-LOW-3: External Google Fonts Loaded With No SRI
**File:** `src/app.html:9`

CSS from Google Fonts is loaded with no `integrity` attribute. Also a GDPR concern (user IPs sent to Google).

**Fix:** Self-host the Roboto Mono font under `/static/fonts/`.

---

### F-LOW-4: Minimum Password Length 6 Characters on Frontend
**Files:** `+layout.svelte:101`, `reset-password/+page.svelte:43`

Matches the weak backend minimum (see B-MED-5). Should be updated in sync.

---

### I-LOW-1: Docker Container Runs as Root
**File:** `Dockerfile`

No `USER` directive. A compromised process has full root access within the container.

**Fix:** Add a non-root user: `RUN addgroup -S app && adduser -S app -G app` and `USER app`.

---

### I-LOW-2: No Gradle Dependency Checksum Verification
**File:** `build.gradle.kts`

No `gradle/verification-metadata.xml`. A compromised Maven Central mirror could substitute malicious JARs silently.

**Fix:** Run `./gradlew --write-verification-metadata sha256 help` and commit the result.

---

### I-LOW-3: nginx Debug Logging in Production
**File:** `deployment/nginx.conf:48`

`access_log /dev/stdout debug` and `error_log /dev/stderr debug` in the `/api/` block is high-volume and could log sensitive path data.

**Fix:** Change to `access_log /dev/stdout main` and remove the debug `error_log` from the location block.

---

### I-LOW-4: `client_max_body_size 10M` Too Permissive for a JSON API
**File:** `deployment/nginx.conf:36`

The API only handles small JSON payloads. 10MB unnecessarily increases the DoS surface.

**Fix:** Reduce to `4k` for the API. Apply larger limits only to specific upload endpoints if ever added.

---

## Positive Findings

- Backend correctly binds to `127.0.0.1` only — the reverse proxy is the only public surface
- JWT correctly uses separate `type` claims for access vs. refresh tokens (no token confusion)
- `.env` is correctly gitignored and not in git history
- GitGuardian scanning and a `detect-private-key` pre-commit hook are configured
- Parameterized queries in `MedicineSearchService` — no SQL injection risk
- Redis keys are correctly namespaced per user

---

## Recommended Fix Order

1. **Rotate the Resend API key immediately** (I-CRIT-1)
2. **Fix B-CRIT-1** — fail at startup if `JWT_SECRET` is unset
3. **Fix B-CRIT-2** — remove or protect the unauthenticated password change endpoint
4. **Fix B-CRIT-3 + B-MED-6** — redesign password reset so the verified token is required in the final step
5. **Fix B-HIGH-5** — correct the key patterns in `deleteUserCompletely` (functional bug with GDPR implications)
6. **Fix F-HIGH-2** — forward `Set-Cookie` in the login proxy (refresh mechanism is currently broken)
7. **Enable TLS** (I-HIGH-1) and set `secure=true` on cookies (B-HIGH-1)
8. **Fix F-HIGH-3** — call `setAccessToken()` after account activation
9. **Fix F-MED-5** — correct the `isLoggedIn()` localStorage key
10. Work through remaining HIGH, MEDIUM, and LOW items
