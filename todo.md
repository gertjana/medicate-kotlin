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
   - [ ] Don't give away information about whether username/email exists on login/register/reset password
   - [ ] **Activate User with email confirmation after registration**
   - [ ] **Add rate limiting to register/login to prevent brute-force attacks**
- [ ] **Allow users to delete their accounts**
- [ ] **Optional, implement Postgres StorageService for production instead of Redis**
- [ ] Investigate whether we can have a database of known medicines to let users select/search it from a list

---
