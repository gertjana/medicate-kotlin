# Medicine Search Feature Implementation

## Overview
Implemented a medicine search feature that allows users to search through a database of EU-approved medicines when creating or editing medicines.

## Components Added

### 1. Data Model
- **MedicineSearchResult** (`src/main/kotlin/dev/gertjanassies/model/MedicineSearchResult.kt`)
  - Data class containing: productnaam, farmaceutischevorm, werkzamestoffen
  - Serializable for API responses

### 2. Backend Service
- **MedicineSearchService** (`src/main/kotlin/dev/gertjanassies/service/MedicineSearchService.kt`)
  - Singleton service that loads and caches medicine data from JSON file
  - Configurable data directory via `MEDICINES_DATA_DIR` environment variable
  - Defaults to `data/medicines.json` but checks multiple locations:
    - `{MEDICINES_DATA_DIR}/medicines.json` (configured/default)
    - `/app/data/medicines.json` (Docker fallback)
    - `scripts/medicines.json` (legacy location)
  - `searchMedicines(query, limit)` function for autocomplete
  - Returns up to 10 matches by default

### 3. API Endpoint
- **GET /medicine/search?q={query}**
  - Query parameter: `q` (search query string)
  - Returns: List of MedicineSearchResult objects
  - Requires minimum 2 characters to search
  - Added to MedicineRoutes.kt

### 4. Data Source
- **Dutch Medicines Database (CBG-MEB)**
  - Source: https://www.geneesmiddeleninformatiebank.nl/metadata.csv
  - Contains EU-approved medicines in the Netherlands
  - Converted from pipe-delimited CSV to JSON format
  - Stored in `data/medicines.json`

### 5. GitHub Actions Workflow
- **update-medicines.yml** (`.github/workflows/update-medicines.yml`)
  - Manual trigger or monthly schedule (1st day of each month at 2 AM UTC)
  - Downloads latest metadata.csv from CBG-MEB
  - Converts to JSON using Python script
  - Uploads medicines.json as workflow artifact
  - Artifact used by Docker build workflow

- **docker-build-ci.yml** (updated)
  - Downloads medicines.json artifact if available
  - Copies to Docker image during build
  - Places in `/app/data/medicines.json` in container

### 6. Data Conversion Script
- **convert-metadata-to-json.py** (`scripts/convert-metadata-to-json.py`)
  - Converts pipe-delimited CSV to JSON
  - Handles Dutch special characters
  - Preserves all fields from metadata.csv

## Configuration

### Environment Variables
- `MEDICINES_DATA_DIR`: Directory containing medicines.json (default: "data")

### Docker
- Medicine database bundled in Docker image at `/app/data/medicines.json`
- Automatically loaded on application startup

## Usage

### API Example
```bash
curl http://localhost:8080/medicine/search?q=aspirin
```

Response:
```json
[
  {
    "productnaam": "Aspirine 100mg tabletten",
    "farmaceutischevorm": "Tablet",
    "werkzamestoffen": "ACETYLSALICYLZUUR"
  }
]
```

### Frontend Integration
The search endpoint can be integrated into the medicine creation/edit forms to provide autocomplete functionality:
1. User types at least 2 characters
2. Frontend calls `/medicine/search?q={input}`
3. Display up to 10 matches in dropdown
4. User can select from dropdown or continue typing custom name

## Data Updates

### Manual Update
1. Go to GitHub Actions
2. Run "Update Medicines Database" workflow
3. Redeploy application

### Automatic Updates
- Runs monthly on the 1st at 2 AM UTC
- Downloads latest medicine data
- Creates artifact for next deployment

## Benefits
1. Users can easily find medicines by name
2. Reduces typos and inconsistencies
3. Provides standardized medicine names
4. Includes active ingredients information
5. Automatically stays up-to-date via monthly updates

## Testing
- All existing tests pass
- Service properly handles missing data file
- Graceful fallback when database not available

## Future Enhancements
- Add fuzzy search for better matching
- Search by active ingredient (werkzamestoffen)
- Filter by pharmaceutical form
- Multi-language support
- Add medicine strength/dosage information to search results
