# Localized Action Names in Confirm Dialog

## Problem

The confirm dialog in the admin page was displaying untranslated English action names when interpolating the `confirmDialog.action` value ('activate'/'deactivate'/'delete') directly into translated strings. This was especially noticeable in the Dutch (nl) translation.

### Before

When clicking "Deactivate" on a user, the dialog would show:
- English: "Confirm deactivate" (lowercase, not capitalized properly)
- Dutch: "Bevestig deactivate" (English word in Dutch sentence)

## Solution

Added a reactive statement that maps the raw action string to its localized translation:

```typescript
// Map action to localized label
$: localizedAction = confirmDialog.action
    ? $_(`admin.${confirmDialog.action}`)
    : '';
```

Then updated the dialog template to use `localizedAction` instead of `confirmDialog.action`:

```svelte
<h3 class="text-xl font-bold mb-4">
    {$_('admin.confirmTitle', { values: { action: localizedAction } })}
</h3>
<p class="mb-6">
    {$_('admin.confirmMessage', { values: { action: localizedAction } })} 
    <strong>{confirmDialog.username}</strong>?
```

### After

Now when clicking "Deactivate" on a user, the dialog shows:
- English: "Confirm Deactivate" (properly capitalized)
- Dutch: "Bevestig Deactiveren" (fully translated)

## How It Works

The reactive statement dynamically looks up the translation key based on the action:
- `confirmDialog.action = 'activate'` → `$_('admin.activate')` → "Activate" / "Activeren"
- `confirmDialog.action = 'deactivate'` → `$_('admin.deactivate')` → "Deactivate" / "Deactiveren"
- `confirmDialog.action = 'delete'` → `$_('admin.delete')` → "Delete" / "Verwijderen"

The translations already exist in the language files:

**en.json:**
```json
"activate": "Activate",
"deactivate": "Deactivate",
"delete": "Delete"
```

**nl.json:**
```json
"activate": "Activeren",
"deactivate": "Deactiveren",
"delete": "Verwijderen"
```

## Files Changed

- `frontend/src/routes/admin/+page.svelte`
  - Added reactive statement to map action to localized label
  - Updated confirm dialog to use localized action

## Testing

Ran `npm run check` to verify no TypeScript or Svelte errors were introduced. The change is minimal and surgical, affecting only the display of action names in the confirm dialog.
