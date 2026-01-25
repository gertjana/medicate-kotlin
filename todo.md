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

 - [ ] **Security**
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
- [ ] WON'T DO **Optional, implement Postgres StorageService for production instead of Redis**
- [x] **Investigate whether we can have a database of known medicines to let users select/search it from a list**
      - Research completed - multiple viable options identified
      - Recommended approach: OpenFDA Drug API for MVP (free, RESTful, no auth required)
      - Alternative: RxNorm API (clinical-grade data, requires free UMLS license)
      - Alternative: Download FDA NDC database for local/offline searches
      - Implementation plan: Add autocomplete search to medicine form using OpenFDA API
      - See detailed analysis: `ai_reports/MEDICINE_DATABASE_RESEARCH.md`
- [ ] **Implement medicine search/autocomplete feature**
      - Add medicine search endpoint using OpenFDA Drug API
      - Add autocomplete to medicine form (frontend)
      - Allow pre-filling form from search results
      - Keep manual entry option for unlisted medicines

---
