# Content-Type Header Fix - POST/PUT Requests

**Date:** January 18, 2026
**Issue:** 415 Unsupported Media Type on POST requests (adding medicine)
**Status:** ✅ FIXED

---

## 🐛 Problem

After implementing refresh token authentication, POST/PUT requests to create/update medicines were failing with:
```
415 Unsupported Media Type
```

**Root Cause:**
During the refactoring for refresh tokens, the `getHeaders()` function signature was changed and no longer accepted the `includeContentType` parameter. The `authenticatedFetch()` helper was calling `getHeaders(true)` but the function was ignoring this parameter, so the `Content-Type: application/json` header was not being sent.

---

## 🔧 Solution

Updated `frontend/src/lib/api.ts`:

```typescript
// Before (broken):
function getHeaders(): HeadersInit {
    const headers: HeadersInit = {};
    if (browser) {
        const token = localStorage.getItem('medicate_token');
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
    }
    return headers;
}

// After (fixed):
function getHeaders(includeContentType: boolean = false): HeadersInit {
    const headers: HeadersInit = {};
    if (browser) {
        const token = localStorage.getItem('medicate_token');
        if (token) {
            headers['Authorization'] = `Bearer ${token}`;
        }
    }
    if (includeContentType) {
        headers['Content-Type'] = 'application/json';
    }
    return headers;
}
```

---

## ✅ What Was Fixed

- ✅ `getHeaders()` now accepts `includeContentType` parameter
- ✅ When `true`, adds `Content-Type: application/json` header
- ✅ `authenticatedFetch()` properly sets Content-Type for POST/PUT requests
- ✅ All create/update operations now work correctly

---

## 🧪 Affected Endpoints

Fixed endpoints that were returning 415:
- ✅ `POST /medicine` - Create medicine
- ✅ `PUT /medicine/{id}` - Update medicine
- ✅ `POST /schedule` - Create schedule
- ✅ `PUT /schedule/{id}` - Update schedule
- ✅ `POST /takedose` - Record dose
- ✅ `POST /addstock` - Add stock

All other POST/PUT endpoints that use `authenticatedFetch()` are also fixed.

---

## 🎯 Impact

**Before:** Creating/updating medicines failed with 415 error
**After:** All CRUD operations work correctly ✅

---

## 📝 Lessons Learned

1. When refactoring authentication code, verify all request types (GET, POST, PUT, DELETE)
2. Content-Type header is critical for POST/PUT requests with JSON body
3. Helper functions should maintain backward compatibility during refactoring
4. Test all CRUD operations after authentication changes

---

**Status:** ✅ RESOLVED
**Frontend Build:** ✅ Successful
**Ready for Production:** ✅ YES
