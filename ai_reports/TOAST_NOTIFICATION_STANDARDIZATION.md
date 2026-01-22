# Toast Notification Standardization

## Date
January 22, 2026

## Overview

Standardized all toast notifications across the application to use consistent positioning and styling.

## Problem

Toast notifications were inconsistent across different pages:
- **Layout:** Bottom-right, new styling (light bg with dark border)
- **Other pages:** Top-right, old styling (solid steelblue background)

This created a jarring user experience where notifications appeared in different locations and looked different.

## Solution

Standardized all toast notifications to:
- **Position:** Just below header (`fixed top-[4.5rem] right-4` - 72px from top)
- **Styling:** Light background with darker border
- **Stacking:** Multiple toasts stack vertically below each other
- **Duration:** 6 seconds auto-dismiss

### Standard Toast Component (with Stacking)

```svelte
<!-- Toast notification state -->
interface Toast {
    id: number;
    message: string;
    type?: 'success' | 'error' | 'info'; // Layout only
}
let toasts: Toast[] = [];
let toastIdCounter = 0;

function showToastNotification(message: string, type: 'success' | 'error' | 'info' = 'info') {
    const id = toastIdCounter++;
    toasts = [...toasts, { id, message, type }];
    setTimeout(() => {
        toasts = toasts.filter(t => t.id !== id);
    }, 6000); // 6 seconds
}

<!-- HTML rendering -->
<div class="fixed top-[4.5rem] right-4 z-50 flex flex-col gap-2">
    {#each toasts as toast (toast.id)}
        <div class="animate-slide-up">
            <div class="p-4 rounded-lg shadow-lg border-2 bg-blue-50 border-blue-500 text-blue-800">
                {toast.message}
            </div>
        </div>
    {/each}
</div>
```

### Color Variants

**Info/Default (Blue):**
```svelte
bg-blue-50 border-blue-500 text-blue-800
```

**Success (Green):**
```svelte
bg-green-50 border-green-500 text-green-800
```

**Error (Red):**
```svelte
bg-red-50 border-red-500 text-red-800
```

## Files Updated

All files updated to support **toast stacking**:

1. **frontend/src/routes/+layout.svelte**
   - Changed from `bottom-4` to `top-4`
   - Implemented toast array with unique IDs
   - Added stacking support with `flex flex-col gap-2`

2. **frontend/src/routes/+page.svelte** (Dashboard)
   - Changed from old styling to new styling
   - Implemented toast array for stacking
   - Position already top-right (kept as-is)

3. **frontend/src/routes/history/+page.svelte**
   - Changed from old styling to new styling
   - Implemented toast array for stacking
   - Position already top-right (kept as-is)

4. **frontend/src/routes/medicines/+page.svelte**
   - Changed from old styling to new styling
   - Implemented toast array for stacking
   - Position already top-right (kept as-is)

5. **frontend/src/routes/schedules/+page.svelte**
   - Changed from old styling to new styling
   - Implemented toast array for stacking
   - Position already top-right (kept as-is)

6. **frontend/src/routes/profile/+page.svelte**
   - Changed from old styling to new styling (green variant)
   - Implemented toast array for stacking
   - Position already top-right (kept as-is)

## Old vs New Implementation

### Before (Single Toast)
```svelte
<!-- Old state management -->
let toastMessage = '';
let showToast = false;

function showToastNotification(message: string) {
    toastMessage = message;
    showToast = true;
    setTimeout(() => {
        showToast = false;
    }, 3000); // 3 seconds
}

<!-- Old steelblue style - single toast only -->
{#if showToast}
    <div class="fixed top-4 right-4 bg-[steelblue] text-white px-6 py-3 rounded-tr-lg rounded-bl-lg shadow-lg transition-opacity z-50">
        {toastMessage}
    </div>
{/if}
```

**Issues:**
- Solid dark background (steelblue)
- White text (lower contrast)
- Different border radius style
- Less visual hierarchy
- **Only one toast at a time** (new toasts replace existing ones)

### After (Stacked Toasts)
```svelte
<!-- New state management with array -->
interface Toast {
    id: number;
    message: string;
}
let toasts: Toast[] = [];
let toastIdCounter = 0;

function showToastNotification(message: string) {
    const id = toastIdCounter++;
    toasts = [...toasts, { id, message }];
    setTimeout(() => {
        toasts = toasts.filter(t => t.id !== id);
    }, 6000); // 6 seconds (doubled from 3)
}

<!-- New light bg with dark border style - supports stacking -->
<div class="fixed top-[4.5rem] right-4 z-50 flex flex-col gap-2">
    {#each toasts as toast (toast.id)}
        <div class="animate-slide-up">
            <div class="p-4 rounded-lg shadow-lg border-2 bg-blue-50 border-blue-500 text-blue-800">
                {toast.message}
            </div>
        </div>
    {/each}
</div>
```

**Benefits:**
- Light background (less intrusive)
- Dark text (better readability)
- Prominent colored border (clear visual indicator)
- Consistent rounded corners
- Slide-up animation
- **Multiple toasts stack vertically** (each toast is tracked independently with unique ID)

## User Experience Impact

### Before
- Notifications appeared in different locations (confusing)
- Different visual styles (inconsistent)
- Some harder to read (white text on dark background)
- **New toasts replaced existing ones** (couldn't see multiple notifications)

### After
- All notifications appear in same location (predictable)
- Consistent visual style (professional)
- Better readability (dark text on light background)
- Clear color coding:
  - Blue = info/action
  - Green = success
  - Red = error
- **Multiple toasts stack vertically** (each with 6-second auto-dismiss)
- Toasts appear below each other with `gap-2` spacing
- Positioned just below header (`top-[4.5rem]` = 72px from top)

## Build Status

```
Frontend: ✅ BUILD SUCCESSFUL (built in 3.03s)
All 6 pages updated with stacking support
```

## Testing Checklist

Manual testing to verify:
- [ ] Dashboard toasts (take dose, add stock, undo) - appear top-right, blue style, stack properly
- [ ] History toasts (undo dose) - appear top-right, blue style, stack properly
- [ ] Medicines toasts (add/edit/delete medicine) - appear top-right, blue style, stack properly
- [ ] Schedules toasts (add/edit schedule) - appear top-right, blue style, stack properly
- [ ] Profile toasts (update profile) - appear top-right, green style, stack properly
- [ ] Layout toasts (change password) - appear top-right, blue/green/red styles, stack properly

**Stacking Tests:**
- [ ] Trigger multiple toasts rapidly (e.g., "Take All" on dashboard) - should stack below each other
- [ ] Verify gap spacing between stacked toasts (should be `gap-2` = 0.5rem)
- [ ] Verify each toast dismisses after 6 seconds independently
- [ ] Verify slide-up animation for each toast

## Technical Details

### CSS Classes Used

**Container (Stacking Support):**
- `fixed` - Fixed positioning
- `top-[4.5rem]` - 72px from top (8px below 64px header for comfortable spacing)
- `right-4` - 1rem from right
- `z-50` - High z-index (above other content)
- `flex` - Flexbox layout
- `flex-col` - Vertical stacking
- `gap-2` - 0.5rem spacing between toasts

**Individual Toast:**
- `animate-slide-up` - Slide up animation
- `p-4` - Padding
- `rounded-lg` - Rounded corners
- `shadow-lg` - Large shadow
- `border-2` - 2px border
- Color classes (bg/border/text)

### Stacking Implementation

Each toast is assigned a unique ID using an incrementing counter:
```typescript
let toastIdCounter = 0;
const id = toastIdCounter++;
```

Toasts are stored in an array and rendered using Svelte's `#each` with the unique ID as the key:
```svelte
{#each toasts as toast (toast.id)}
```

Each toast auto-dismisses after 6 seconds by filtering itself out of the array:
```typescript
setTimeout(() => {
    toasts = toasts.filter(t => t.id !== id);
}, 6000);
```

### Animation

The `animate-slide-up` class provides a smooth entrance animation for each toast notification as it appears.

## Future Enhancements

Potential improvements:

1. ~~**Stacking:** Support multiple toasts simultaneously~~ ✅ **IMPLEMENTED**
2. **Auto-dismiss control:** Different durations for different types (e.g., errors stay longer)
3. **Close button:** Allow manual dismissal before auto-dismiss
4. **Icons:** Add icons for different types (ℹ️ ✅ ⚠️)
5. **Progress bar:** Show countdown until auto-dismiss
6. **Sound:** Optional audio feedback for important notifications
7. **Accessibility:** ARIA live regions for screen readers
8. **Max stack limit:** Limit number of visible toasts (e.g., max 5, oldest dismisses first)
9. **Animation on dismiss:** Fade out or slide out when dismissing

## Conclusion

All toast notifications are now:
- ✅ Consistent position (top-right)
- ✅ Consistent styling (light bg with dark border)
- ✅ Color-coded appropriately (blue/green/red)
- ✅ Better readability
- ✅ Professional appearance
- ✅ **Support stacking** (multiple toasts appear below each other)

The user experience is now uniform across the entire application, providing clear and predictable feedback for all user actions. Multiple toasts can now be displayed simultaneously, stacked vertically with proper spacing, ensuring users never miss important notifications.
