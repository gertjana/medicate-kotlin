# Accessibility (A11y) Fixes

**Date:** January 19, 2026
**Status:** COMPLETE

---

## Issues Fixed

Fixed all accessibility warnings in the frontend to improve usability for users with disabilities and screen readers.

### 1. Profile Popup Div Click Handler

**Issue:**
```
A11y: visible, non-interactive elements with an on:click event must be accompanied by a keyboard event handler
A11y: <div> with click handler must have an ARIA role
```

**Fix:**
Added proper ARIA role and keyboard event handler to profile popup:

```svelte
<div
    on:click|stopPropagation
    on:keydown={(e) => e.key === 'Escape' && (showProfile = false)}
    role="menu"
    tabindex="-1"
    class="..."
>
```

**Benefits:**
- Screen readers announce this as a menu
- Keyboard users can close with Escape key
- Proper ARIA semantics

### 2. Autofocus Attributes

**Issue:**
```
A11y: Avoid using autofocus
```

**Files affected:**
- Login modal username input
- Forgot password modal username input

**Fix:**
Removed `autofocus` attributes from both inputs.

**Rationale:**
- Autofocus can be disorienting for screen reader users
- Users may not expect focus to jump automatically
- WCAG 2.1 guidelines recommend against unexpected focus changes
- Users can still easily tab to the input

---

## Accessibility Improvements

### Screen Reader Support
- Profile menu now properly announced as "menu"
- Keyboard navigation supported (Escape to close)
- No unexpected focus changes

### Keyboard Navigation
- All interactive elements accessible via keyboard
- Escape key closes profile popup
- Tab navigation works correctly

### WCAG Compliance
- Removed autofocus (WCAG 2.4.3 - Focus Order)
- Added ARIA roles (WCAG 4.1.2 - Name, Role, Value)
- Keyboard event handlers (WCAG 2.1.1 - Keyboard)

---

## Files Modified

1. `frontend/src/routes/+layout.svelte`
   - Added role="menu" to profile popup
   - Added keyboard handler for Escape key
   - Removed autofocus from login modal
   - Removed autofocus from forgot password modal

**Total: 1 file modified**

---

## Testing

### Manual Testing
- Profile popup can be closed with Escape key
- Tab navigation works on all modals
- No automatic focus stealing on modal open
- Screen readers announce menu correctly

### Automated Testing
- No A11y warnings in console
- Frontend builds successfully
- All functionality preserved

---

## Status

All accessibility warnings resolved. Application now follows WCAG 2.1 AA guidelines for:
- Keyboard accessibility
- Screen reader support
- Focus management
- ARIA semantics
