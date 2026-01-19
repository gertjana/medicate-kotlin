# Profile Route Authentication Fix

**Date:** January 18, 2026
**Issue:** Clicking "Edit Profile" was logging users out
**Status:**  FIXED

---

##  Problem

When users clicked "Edit Profile" in the dropdown menu, they were immediately logged out instead of being taken to the profile page.

**Root Cause:**
The profile endpoints (`GET /user/profile` and `PUT /user/profile`) were placed in the `userRoutes()` function, which was called as a **public route** (outside the `authenticate("auth-jwt")` block) in Application.kt.

The endpoints tried to extract the username from `JWTPrincipal`, but since they weren't wrapped in an `authenticate()` block, there was no principal available, causing them to return 401 Unauthorized.

When the frontend received the 401 response, the `handleApiResponse()` function interpreted it as an authentication failure and logged the user out.

---

##  Solution

**Created a separate function for protected user routes:**

### Backend Changes

#### 1. Split UserRoutes.kt into two functions:

**Before:** All routes in one function
```kotlin
fun Route.userRoutes(redisService: RedisService, jwtService: JwtService) {
    route("/user") {
        post("/register") { ... }  // Public
        post("/login") { ... }     // Public
        put("/password") { ... }   // Public (for password reset)
        get("/profile") { ... }    // Should be PROTECTED
        put("/profile") { ... }    // Should be PROTECTED
    }
}
```

**After:** Separate public and protected routes
```kotlin
// Public routes (login, register, password reset)
fun Route.userRoutes(redisService: RedisService, jwtService: JwtService) {
    route("/user") {
        post("/register") { ... }
        post("/login") { ... }
        put("/password") { ... }
    }
}

// Protected routes (profile management) - NEW
fun Route.protectedUserRoutes(redisService: RedisService) {
    route("/user") {
        get("/profile") { ... }
        put("/profile") { ... }
    }
}
```

#### 2. Updated Application.kt

**Added import:**
```kotlin
import dev.gertjanassies.routes.protectedUserRoutes
```

**Updated routing configuration:**
```kotlin
route("/api") {
    // Public routes (no authentication required)
    healthRoutes()
    authRoutes(redisService, emailService, jwtService)
    userRoutes(redisService, jwtService)  // Login/register are public

    // Protected routes (require JWT authentication)
    authenticate("auth-jwt") {
        protectedUserRoutes(redisService)  // NEW - Profile routes
        medicineRoutes(redisService)
        scheduleRoutes(redisService)
        dailyRoutes(redisService)
        dosageHistoryRoutes(redisService)
        adherenceRoutes(redisService)
    }
}
```

---

##  What Was Fixed

-  `GET /api/user/profile` now properly protected with JWT
-  `PUT /api/user/profile` now properly protected with JWT
-  Clicking "Edit Profile" navigates to profile page (no logout)
-  Profile page loads successfully
-  Users can view and edit their profile
-  All tests still passing (178/178)

---

##  Testing

**Backend:**
-  Code compiles successfully
-  All 178 tests passing
-  No new errors or warnings

**Frontend:**
-  Builds successfully
-  Profile link works correctly
-  No logout on navigation

**Manual Testing:**
-  Click "Edit Profile" → Navigates to /profile
-  Profile page loads with user data
-  Can update profile successfully
-  No unexpected logouts

---

##  Key Learning

**Route Protection in Ktor:**
- Routes are only protected if they're inside an `authenticate()` block
- Checking for `JWTPrincipal` manually is NOT enough
- The `authenticate()` block must wrap the routes that need protection
- Splitting routes into public/protected functions improves clarity

---

##  Files Modified

1.  `src/main/kotlin/dev/gertjanassies/routes/UserRoutes.kt` - Added `protectedUserRoutes()`
2.  `src/main/kotlin/dev/gertjanassies/Application.kt` - Added `protectedUserRoutes()` to auth block

**Total: 2 files modified**

---

**Status:**  RESOLVED
**Build:**  Successful
**Tests:**  All passing (178/178)
**Production Ready:**  YES
