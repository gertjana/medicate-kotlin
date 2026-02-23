# TODO

## Dashboard UX Improvements

 - [x] **With no medicines in the database, Have an "add Medicine" button on the main screen instead
       of the "Add Schedule", if there are medicines but no schedules, show "Add Schedule" button.**
       - Implemented smart empty states on dashboard
       - Shows "Add Medicine" button when no medicines exist
       - Shows "Add Schedule" button when medicines exist but no schedules
       - Context-aware messaging guides users through natural workflow
       - See: `ai_reports/SMART_DASHBOARD_EMPTY_STATES.md`

## Profile & User Management

 - [x] **Clean up Profile popup**
       - Added "Edit Profile" link to dropdown
       - Shows user info in cleaner format

 - [x] **Add First Name and Last Name fields to Profile**
       - Implemented full profile feature with firstName and lastName
       - Created /profile page for editing
       - Personalized password reset emails
       - See: `ai_reports/USER_PROFILE_FEATURE.md`

 - [x] **Validate email format in edit Profile**
       - Client-side validation (requires @ symbol)
       - Backend validation (non-empty, format check)
       - Clear error messages

 - [x] **Change password via email link, same as reset password flow**
       - Added "Change Password" link to profile popup
       - Uses existing password reset flow (sends email with reset link)
       - Integrated with profile dropdown menu
       - One-click experience for logged-in users
       - Fixed to use user's email address instead of username
       - Uses toast notifications instead of alerts

## Production Rollout

 - [x] **Password reset now uses email address instead of username**
       - Updated to email-based identification (required for multiple users with same username)
       - Frontend "Forgot Password" modal now asks for email
       - Backend `/auth/resetPassword` endpoint uses email lookup
       - Token storage uses user ID for uniqueness
       - Fixed token key prefix issue (medicate:environment)
       - All tests passing (199/199)
       - See: `ai_reports/PASSWORD_RESET_EMAIL_BASED.md`

 - [x] **Low stock should be based on schedule (< 7 days = red, < 14 days = yellow), not stock count**
       - Removed getLowStockMedicines API endpoint and method
       - Dashboard now uses medicineExpiry calculation instead
       - Shows medicines expiring within 7 days with warning banner
       - Displays actual expiry dates based on consumption schedules
       - More intelligent than arbitrary stock thresholds
       - See: `ai_reports/LOW_STOCK_TO_EXPIRY_REFACTOR.md`

 - [x] **No more window.alert()'s - use toast notifications everywhere**
       - Verified no alert() calls exist in codebase
       - All pages use consistent toast notification system
       - Profile page now uses toast with auto-redirect to dashboard
       - See: `ai_reports/UX_IMPROVEMENTS.md`

 - [x] **Allow people to have the same username, but different emails**
       - Username index now stores comma-separated list of user IDs
       - Login checks passwords against all users with that username
       - Email remains unique (enforced)
       - Accepted risk: Users with same username must have different passwords
       - See: `ai_reports/MULTIPLE_USERS_SAME_USERNAME.md`

 - [x] **Security**
   - [x] Don't give away information about whether username/email exists on login/register/reset password
   - [x] **Add rate limiting to register/login to prevent brute-force attacks**
         - Implemented in nginx reverse proxy (most efficient)
         - Login/Register: 5 requests per minute per IP (+ 2 burst)
         - Password Reset: 3 requests per 5 minutes per IP (+ 1 burst)
         - General API: 60 requests per minute per IP (+ 10 burst)
         - Returns 429 status when limit exceeded
         - See: `ai_reports/RATE_LIMITING.md`
   - [x] **Activate User with email confirmation after registration**
- [ ] Create an admin page that only shows up for admin users, (an admin is a user with isAdmin = true in their user record).
      This page should allow the admin to see a list of all users, and delete any user.
- [x] **Multilingual support (i18n) for at least English and Dutch**
      - Implemented full i18n using svelte-i18n
      - Language switcher in header (EN/NL)
      - All frontend pages, forms, buttons, and messages translated
      - Email templates translated (password reset, activation)
      - Backend sends emails in user's preferred language
      - Persisted language preference in localStorage
      - All toast notifications localized
      - Complete coverage: dashboard, medicines, schedules, history, profile
- [-] WON'T DO **Optional, implement Postgres StorageService for production instead of Redis**
- [x] **Medicine database with autocomplete search**
      - Downloaded Dutch medicine database from Geneesmiddeleninformatiebank.nl
      - Converted metadata.csv (28MB, 48k+ medicines) to JSON format
      - Implemented SQLite-based search service for memory efficiency
      - Added autocomplete to medicine form with multi-word matching
      - Search supports partial name matches (2+ characters)
      - Keyboard navigation (up/down arrows) through results
      - Scrollable dropdown showing up to 5 results (30 retrieved)
      - Links to official product information (bijsluiter PDF)
      - GitHub Actions workflow for monthly database updates
      - Fallback to manual entry for unlisted medicines
      - See: `ai_reports/MEDICINE_DATABASE_RESEARCH.md`

---

## Security Audit (2026-02-23)

Full report: `ai_reports/SECURITY_AUDIT.md`

### Critical

- [x] **B-CRIT-1** Hardcoded fallback JWT secret — fail at startup if `JWT_SECRET` is unset (`Application.kt:126`)
- [x] **B-CRIT-2** Unauthenticated password change endpoint — removed `PUT /api/user/password` from `UserRoutes.kt`
- [x] **B-CRIT-3** Password reset flow doesn't require token — redesigned: `PUT /auth/updatePassword` now accepts `{ token, password }` and verifies+consumes the token atomically. Removed separate `verifyResetToken` endpoint.
- [-] **I-CRIT-1** Live Resend API key in `.env` — N/A: `.env` is gitignored (never committed), production uses Render environment variables

### High

- [x] **B-HIGH-1** Refresh token cookie `secure=false` — set `secure` via `secureCookies` flag (configurable via `SECURE_COOKIES` env var; default `false`, must be set to `true` in production)
- [x] **B-HIGH-2** Logout doesn't invalidate refresh token server-side — tokens stored in Redis (`$keyPrefix:refresh-token:<token>`), deleted on logout; per-user set for bulk invalidation
- [-] **B-HIGH-3** No application-layer rate limiting on login/register/reset — N/A: already handled in `deployment/nginx.conf` (auth: 5 req/min, reset: 1 req/min, general: 60 req/min)
- [x] **B-HIGH-4** Client controls dosage timestamps — use server-side `LocalDateTime.now()` (`DosageHistoryRequest.kt:16`)
- [x] **B-HIGH-5** `deleteUserCompletely` uses wrong Redis key patterns — fixed to `$keyPrefix:user:$userId:medicine:*` etc. (`RedisService.kt`)
- [x] **B-HIGH-6** No token invalidation on password change — `resetPasswordWithToken` now calls `invalidateAllRefreshTokensForUser` (`RedisService.kt`)
- [x] **F-HIGH-1** SvelteKit API proxy routes drop Authorization header — all protected proxy routes now forward `Authorization` from the incoming request
- [x] **F-HIGH-2** Login proxy drops `Set-Cookie` — refresh token never reaches the browser — login and activateAccount proxies now forward `Set-Cookie` from backend response
- [x] **F-HIGH-3** Account activation doesn't call `setAccessToken()` — broken post-activation state — fixed: `activate-account/+page.svelte` now calls `setAccessToken(data.token)` before `userStore.login()`
- [-] **I-HIGH-1** No TLS — N/A: Render platform terminates HTTPS at the edge; nginx on port 80 is internal only
- [x] **I-HIGH-2** CI action pinned to `@master` (mutable tag) — pinned to commit SHA `57116acb` (`update-medicines.yml`)

### Medium

- [x] **B-MED-1** No input length limits on any request field
- [x] **B-MED-2** Missing security headers (CSP, X-Frame-Options, HSTS, etc.) (`Application.kt`)
- [x] **B-MED-3** `Accept-Language` in file path without allowlisting — allowlist to `["en", "nl"]` (`UserRoutes.kt:62`)
- [x] **B-MED-4** Internal error messages returned to clients verbatim
- [x] **B-MED-5** Minimum password length is 6 characters — increase to 8+ (`UserRoutes.kt:44`)
- [-] **B-MED-6** Username disclosed after reset token verification — N/A: `verifyResetToken` endpoint was removed as part of B-CRIT-3
- [x] **F-MED-1** User PII and `isAdmin` stored in `localStorage` — `isAdmin` stripped from localStorage; derived from JWT claims at runtime via `parseIsAdminFromToken()`
- [x] **F-MED-2** `{@html}` used with i18n strings — replaced with plain text (no HTML in locale strings)
- [x] **F-MED-3** No `hooks.server.ts` — no security headers at SvelteKit layer
- [x] **F-MED-4** Reset/activation tokens in URL query params — moved to URL fragment (`#token=`) which is never sent in Referer headers
- [x] **F-MED-5** `isLoggedIn()` checks a key that is never set — fixed to check `medicate_user` key (`api.ts`)
- [x] **I-MED-1** Scheduled workflow commits unreviewed data directly to `main` — changed to open a PR via `peter-evans/create-pull-request`
- [x] **I-MED-2** Redis connection has no TLS — configurable via `REDIS_TLS=true` env var; uses `RedisURI.Builder.withSsl(true)` (`RedisService.kt`)
- [x] **I-MED-3** `APP_ENV=test` hard-coded in production `docker-compose.yml` — changed to `APP_ENV=${APP_ENV:-production}`

### Low

- [x] **B-LOW-1** BCrypt work factor 10 — use `gensalt(12)` (`RedisService.kt`)
- [x] **B-LOW-2** `User.isActive` defaults to `true` — change default to `false` (`User.kt`)
- [x] **B-LOW-3** Missing `SameSite` on activation-flow cookie (`AuthRoutes.kt`)
- [-] **B-LOW-4** Password reset token uses Redis SCAN with token in glob — N/A: already uses direct GET
- [x] **F-LOW-1** `window.location.reload()` on session expiry — use `window.location.href='/'` (`api.ts`)
- [-] **F-LOW-2** `userStore.init()` `this.logout()` silently fails — N/A: already fixed during F-MED-1 work
- [x] **F-LOW-3** Google Fonts loaded with no SRI — self-hosted Roboto Mono (`frontend/static/fonts/`, `app.html`)
- [x] **F-LOW-4** Frontend minimum password length is 6 characters — increased to 8 (`+layout.svelte`)
- [x] **I-LOW-1** Docker container runs as root — added `appuser` + `su-exec` (`Dockerfile`, `deployment/start.sh`)
- [x] **I-LOW-2** No Gradle dependency checksum verification — generated `gradle/verification-metadata.xml`
- [x] **I-LOW-3** nginx debug logging in production — changed to `main` log format (`nginx.conf`)
- [x] **I-LOW-4** `client_max_body_size 10M` too large for a JSON API — reduced to `1M` (`nginx.conf`)

---
