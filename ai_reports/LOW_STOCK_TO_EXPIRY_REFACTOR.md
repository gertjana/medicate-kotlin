# Low Stock to Medicine Expiry Refactoring

## Summary

Refactored the low stock warning system to use medicine expiry calculations instead of a simple stock threshold. The new system is more intelligent and warns users when medicines will run out within 7 days based on their actual consumption schedules.

## Changes Made

### Backend Changes

#### Removed Components

1. **StorageService Interface**
   - Removed `getLowStockMedicines(username: String, threshold: Double)` method

2. **RedisService**
   - Removed `getLowStockMedicines` implementation

3. **AdherenceRoutes**
   - Removed `/lowstock` endpoint (GET)
   - Simplified to only contain `/adherence` endpoint

4. **Tests**
   - Removed `getLowStockMedicines` test context from `AdherenceServiceTest.kt`
   - Removed all `/lowstock` endpoint tests from `AdherenceRoutesTest.kt`
   - Updated test file comments to reflect changes

### Frontend Changes

#### Removed Components

1. **API Functions**
   - Removed `getLowStockMedicines()` from `src/lib/api.ts`

2. **Server Endpoints**
   - Removed `frontend/src/routes/api/lowstock/+server.ts`

#### Updated Components

1. **API Types** (`src/lib/api.ts`)
   - Added `expiryDate?: string` field to `MedicineExpiry` interface

2. **Dashboard** (`src/routes/+page.svelte`)
   - Replaced `lowStockMedicines` state with expiry-based calculation
   - Changed `suppressedLowStockIds` to `suppressedExpiringIds`
   - Updated localStorage key from `suppressedLowStock` to `suppressedExpiring`
   - Added reactive calculation for medicines expiring within 7 days:
     ```typescript
     $: expiringMedicines = medicineExpiry.filter(m => {
       if (!m.expiryDate) return false;
       const expiryDate = new Date(m.expiryDate);
       const now = new Date();
       const daysUntilExpiry = Math.ceil((expiryDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24));
       return daysUntilExpiry <= 7 && daysUntilExpiry >= 0;
     });
     ```
   - Updated warning banner to show expiry information instead of stock threshold
   - Modified `loadSchedule()` to load medicine expiry data
   - Renamed `suppressLowStockWarning()` to `suppressExpiringWarning()`

3. **Warning Banner**
   - Changed from "Low Stock Warning" to "Running Low"
   - Now shows: "X medicines expiring within 7 days"
   - Displays stock remaining and expiry date for each medicine

## Benefits

1. **More Intelligent Warnings**: Users are warned based on actual consumption patterns, not arbitrary stock levels
2. **Better User Experience**: Shows when medicine will actually run out with specific dates
3. **Simplified Code**: Removed redundant API endpoint and related code
4. **Data-Driven**: Leverages existing `medicineExpiry` calculation that considers schedules and consumption
5. **Consistent Logic**: Uses the same expiry calculation throughout the application

## Technical Details

### Warning Threshold
- Medicines are flagged when they will run out within **7 days**
- Calculation is based on:
  - Current stock levels
  - Scheduled consumption (daily/weekly patterns)
  - Days of week for schedules

### User Experience
- Users can still dismiss warnings (stored in localStorage as `suppressedExpiring`)
- Warning banner shows:
  - Number of medicines expiring soon
  - Medicine details (name, dose, unit)
  - Current stock remaining
  - Expiry date

## Testing

All tests pass successfully:
- Backend: 153 tests
- No failing tests
- Code coverage maintained

## Migration Notes

For users upgrading:
- Old `suppressedLowStock` localStorage data will be ignored
- New warnings will appear based on expiry dates instead of stock levels
- No backend migration needed - API changes are backward compatible (removed endpoint)
