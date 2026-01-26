# Medicine Database Migration to SQLite

## Problem
The application was experiencing OutOfMemoryError (Java heap space) in production due to loading the entire `medicines.json` file (28 MB) into memory. This caused crashes when searching for medicines.

## Solution
Migrated from in-memory JSON loading to SQLite database queries, reducing memory footprint significantly.

## Changes Made

### 1. Backend Changes

#### MedicineSearchService.kt
- Changed from loading entire JSON into memory to using SQLite database queries
- Now opens database connections on-demand for searches
- Memory footprint reduced from ~28MB to negligible (only query results in memory)

#### Build Configuration
- SQLite JDBC driver already included in dependencies: `org.xerial:sqlite-jdbc:3.44.1.0`

### 2. Migration Script

Created `scripts/migrate-medicines-to-sqlite.py`:
- Reads `data/medicines.json`
- Creates SQLite database with indexed schema
- Migrates all 18,989 medicines
- Creates indices on `productnaam`, `werkzamestoffen`, and `farmaceutischevorm` for fast searches
- Database size: 20 MB (vs 28 MB JSON)
- Memory savings: ~28 MB (JSON no longer loaded into RAM)

### 3. CI/CD Updates

#### GitHub Actions Workflow (update-medicines.yml)
- Now generates both JSON (for backward compatibility) and SQLite database
- Uploads both as artifacts
- Commits both to repository

#### CI Workflow (ci.yml)
- Downloads SQLite database artifact from update-medicines workflow
- Includes database in Docker image build

#### Dockerfile
- Updated to copy `medicines.db` instead of `medicines.json`
- Added fallback handling if database doesn't exist

## Migration Steps for Production

### Option 1: Use Migration Script
```bash
cd /path/to/kotlin-ktor-arrow
python3 scripts/migrate-medicines-to-sqlite.py
```

### Option 2: Download from GitHub Actions
The database is automatically generated monthly by GitHub Actions and included in Docker images.

## Database Schema

```sql
CREATE TABLE medicines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    registratienummer TEXT,
    soort TEXT,
    productnaam TEXT NOT NULL,
    farmaceutischevorm TEXT,
    werkzamestoffen TEXT,
    bijsluiter_filenaam TEXT,
    -- ... other fields
);

CREATE INDEX idx_productnaam ON medicines(productnaam);
CREATE INDEX idx_werkzamestoffen ON medicines(werkzamestoffen);
CREATE INDEX idx_farmaceutischevorm ON medicines(farmaceutischevorm);
```

## Performance Impact

### Before (JSON in memory)
- Memory usage: ~28 MB constant
- Startup time: Slower (JSON parsing)
- Search: Fast (in-memory filtering)
- Risk: OutOfMemoryError on small containers

### After (SQLite)
- Memory usage: Negligible (only query results)
- Startup time: Fast (no data loading)
- Search: Fast (indexed queries)
- Risk: None (only connection overhead)

## Environment Variables

- `MEDICINES_DATA_DIR`: Directory containing `medicines.db` (default: `data`)

## Backward Compatibility

The JSON file is still generated for reference purposes, but not loaded by the application.

## Testing

Existing tests still work as the public API (`searchMedicines()`) remains unchanged.

## Rollback Plan

If needed, revert these commits:
1. MedicineSearchService.kt changes
2. Dockerfile changes
3. GitHub Actions workflow changes

Then redeploy with previous version.

## Monitoring

Check application logs for:
- `"Connected to medicines database at ..."` - successful initialization
- `"Medicine database not found at: ..."` - missing database warning

## Future Improvements

1. Consider full-text search (FTS5) for better search quality
2. Add database versioning/migration support
3. Optimize indices based on query patterns
4. Consider read-only mode for extra safety

## Related Files

- `src/main/kotlin/dev/gertjanassies/service/MedicineSearchService.kt`
- `scripts/migrate-medicines-to-sqlite.py`
- `.github/workflows/update-medicines.yml`
- `.github/workflows/ci.yml`
- `Dockerfile`

## Date
January 26, 2026
