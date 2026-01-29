# Admin Page Separation and Serialization Fix

## Summary

Fixed serialization error in admin endpoints and separated admin functionality into its own dedicated page.

## Changes Made

### Backend Changes

1. **Created AdminUserResponse models** (`src/main/kotlin/dev/gertjanassies/model/response/AdminUserResponse.kt`)
   - Added `AdminUserResponse` data class with proper serialization annotations
   - Added `AdminUsersListResponse` wrapper to fix List serialization issue
   - This resolves the `kotlinx.serialization.SerializationException` error

2. **Updated AdminRoutes** (`src/main/kotlin/dev/gertjanassies/routes/AdminRoutes.kt`)
   - Changed `/admin/users` endpoint to return `AdminUsersListResponse` instead of raw List
   - Updated response mapping to use proper data classes instead of Map
   - Fixed imports and logger usage

### Frontend Changes

1. **Created dedicated Admin page** (`frontend/src/routes/admin/+page.svelte`)
   - Moved all admin functionality from profile page to separate admin page
   - Includes user management table with activate/deactivate/delete actions
   - Added admin-only guard to redirect non-admin users
   - Maintained all existing admin features (confirmation dialogs, toast notifications)

2. **Simplified Profile page** (`frontend/src/routes/profile/+page.svelte`)
   - Removed admin section from profile page
   - Now only handles user profile editing
   - Cleaner, more focused UI

3. **Updated Layout** (`frontend/src/routes/+layout.svelte`)
   - Added "Admin Panel" link in profile dropdown for admin users
   - Link is only visible to users with isAdmin flag
   - Styled with purple color to distinguish from regular profile links

4. **Updated API types** (`frontend/src/lib/api.ts`)
   - Added `AdminUsersListResponse` interface to match backend response
   - Updated `getAllUsers()` to return wrapped response

## Issue Resolved

The serialization error was caused by Ktor trying to serialize a raw `List<Map<String, Any>>` which isn't supported by kotlinx.serialization. The fix wraps the list in a proper serializable data class.

## User Experience Improvements

- Admin functionality is now clearly separated from user profile
- Profile dropdown shows "Admin Panel" link only for admin users
- Admin page is accessible at `/admin` route
- Cleaner, more focused profile page for all users
- Better organization and navigation
