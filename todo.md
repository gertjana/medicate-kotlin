# TODO

## Dashboard UX Improvements

 - [x] **With no medicines in the database, Have an "add Medicine" button on the main screen instead
       of the "Add Schedule", if there are medicines but no schedules, show "Add Schedule" button.** ✅
       - Implemented smart empty states on dashboard
       - Shows "Add Medicine" button when no medicines exist
       - Shows "Add Schedule" button when medicines exist but no schedules
       - Context-aware messaging guides users through natural workflow
       - See: `ai_reports/SMART_DASHBOARD_EMPTY_STATES.md`

## Profile & User Management

 - [x] **Clean up Profile popup** ✅
       - Added "Edit Profile" link to dropdown
       - Shows user info in cleaner format

 - [x] **Add First Name and Last Name fields to Profile** ✅
       - Implemented full profile feature with firstName and lastName
       - Created /profile page for editing
       - Personalized password reset emails
       - See: `ai_reports/USER_PROFILE_FEATURE.md`

 - [x] **Validate email format in edit Profile** ✅
       - Client-side validation (requires @ symbol)
       - Backend validation (non-empty, format check)
       - Clear error messages

 - [ ] Change password via email link, same as reset password flow

## Production Rollout

 - [ ] Allow people to have the same username, but different emails
 - [ ] Activate User with email confirmation after registration
 - [ ] Allow users to delete their accounts
 - [ ] Add rate limiting to prevent brute-force attacks


---
