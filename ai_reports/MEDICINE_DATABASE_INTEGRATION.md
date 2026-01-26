
# Medicine Database Integration

## Date: January 25-26, 2026

## Overview
Integrated the Dutch Medicine Database (CBG-MEB) into the Medicate application to provide users with autocomplete suggestions when adding or editing medicines.

## What Was Implemented

### 1. Data Download and Processing
- Created Python script to download medicine metadata from CBG-MEB
- Converts CSV data to JSON format (medicines.json)
- Stores in /data directory (28MB, 89,000+ medicines)
- Script handles rate limiting and provides statistics

### 2. Backend Implementation

#### New Components
- **MedicineSearchService**: Service to search medicines from JSON database
  - Case-insensitive substring matching
  - Configurable result limit (default: 10)
  - Matches on product name
  - Returns structured search results

- **MedicineSearchResult**: Data model for search results
  - Includes: registratienummer, productnaam, farmaceutischevorm, bijsluiter_filenaam

#### API Endpoint
- `GET /api/medicines/search?q={query}`
  - Requires authentication
  - Minimum 2 characters for search
  - Returns up to 10 matching medicines
  - Used for autocomplete functionality

#### Medicine Model Update
- Added optional `bijsluiter` field to Medicine model
  - Stores link to package leaflet (bijsluiter) PDF
  - Populated from selected medicine database entry

#### Configuration
- `MEDICINES_DATA_PATH` environment variable
- Defaults to `data/medicines.json`
- Can be overridden for Docker deployments

### 3. Frontend Implementation

#### Medicine Form Enhancements
- Autocomplete dropdown when typing medicine name
- Triggers after 2+ characters
- Shows up to 10 matching medicines
- Displays: product name and pharmaceutical form
- Auto-fills name and bijsluiter link when selected
- Falls back to manual entry if no match

#### Medicine Display
- Added PDF icon link to package leaflet (if available)
- Icon appears in top-right of medicine card
- Opens bijsluiter PDF in new tab
- Styled in steelblue color
- Card layout adjusted to 2 columns for longer medicine names

#### Button Layout Improvements
- Edit, Delete, and +Stock buttons left-aligned
- Reduced button widths for better layout
- PDF icon positioned at top-right of card

### 4. Testing

#### Backend Tests
- **MedicineSearchService**: 19 comprehensive tests
  - Partial name matching
  - Case sensitivity
  - Result limiting
  - Special characters
  - Field validation
  - Edge cases (empty queries, whitespace, etc.)

- **MedicineRoutes**: 6 new tests for search endpoint
  - Valid queries
  - Authentication
  - Query parameter validation
  - Response format validation

#### Test Coverage
- All 248 tests passing
- 100% code coverage on new components

### 5. Data Management

#### Scripts Created
- `scripts/convert-metadata-to-json.py`: Convert CSV to JSON
- `scripts/download-ema-medicines.py`: EMA SPOR API client (unused - CBG-MEB chosen instead)
- `scripts/setup-monthly-update.sh`: Cron job setup for monthly updates

#### GitHub Actions
- `update-medicines.yml`: Monthly automated updates
  - Downloads latest metadata.csv
  - Converts to JSON
  - Creates artifact for Docker builds
- Integrated with Docker build workflow

### 6. Docker Integration
- Medicine data excluded from git (.gitignore)
- Downloaded during Docker image build
- Uses GitHub Actions artifact if available
- Falls back to direct download if needed

## Configuration

### Environment Variables
```bash
MEDICINES_DATA_PATH=/path/to/medicines.json  # Optional, defaults to data/medicines.json
```

### Local Development
```bash
# Download medicine data
cd scripts
python3 convert-metadata-to-json.py
```

### Docker Deployment
Medicine data is automatically included in Docker image via build process.

## Data Source
- **Provider**: College ter Beoordeling van Geneesmiddelen (CBG-MEB)
- **URL**: https://www.geneesmiddeleninformatiebank.nl/metadata.csv
- **Scope**: Netherlands registered medicines
- **Size**: ~89,000 medicines
- **Update Frequency**: Monthly (automated via GitHub Actions)

## Files Changed

### Backend
- `src/main/kotlin/dev/gertjanassies/model/Medicine.kt`
- `src/main/kotlin/dev/gertjanassies/model/MedicineSearchResult.kt` (new)
- `src/main/kotlin/dev/gertjanassies/service/MedicineSearchService.kt` (new)
- `src/main/kotlin/dev/gertjanassies/routes/MedicineRoutes.kt`
- `src/test/kotlin/dev/gertjanassies/service/MedicineSearchServiceTest.kt` (new)
- `src/test/kotlin/dev/gertjanassies/routes/MedicineRoutesTest.kt`

### Frontend
- `frontend/src/routes/medicines/+page.svelte`
- `frontend/src/lib/api.ts`

### Scripts & CI
- `scripts/convert-metadata-to-json.py` (new)
- `scripts/download-ema-medicines.py` (new)
- `scripts/setup-monthly-update.sh` (new)
- `.github/workflows/update-medicines.yml` (new)
- `.github/workflows/docker-build.yml`

### Configuration
- `.gitignore` (excluded /data/)

## Benefits
1. Faster medicine entry with autocomplete
2. Accurate medicine names and dosages
3. Direct access to official package leaflets
4. Automatic monthly updates of medicine database
5. Reduced user errors in medicine naming

## Future Enhancements
- Search by active ingredient (werkzamestoffen)
- Filter by pharmaceutical form
- Show ATC codes
- Display warnings/monitoring status
- Multi-language support (currently Dutch only)
