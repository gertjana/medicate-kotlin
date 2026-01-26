# Medicines Database Docker Integration

## Overview

The Docker build workflow now automatically includes the Dutch medicines database in the Docker image when available.

## How It Works

### 1. Medicines Database Update Workflow

The `update-medicines.yml` workflow:
- Runs monthly (1st of each month at 2 AM UTC) or manually
- Downloads `metadata.csv` from https://www.geneesmiddeleninformatiebank.nl/metadata.csv
- Converts it to JSON format using `convert-metadata-to-json.py`
- Uploads `medicines.json` as a GitHub Actions artifact (retained for 90 days)
- Commits the updated database to the repository

### 2. Docker Build Integration


The CI workflow (`ci.yml`) now includes these steps:

1. **Download Artifact** (optional):
   - Uses `dawidd6/action-download-artifact@v3` to fetch the latest `medicines-database` artifact
   - Downloads to `scripts/medicines.json`
   - Continues even if artifact is not found (`continue-on-error: true`)

2. **Check Existence**:
   - Verifies if `scripts/medicines.json` exists
   - Logs the file size if found
   - Sets output variable `medicines_exists` for potential future use

3. **Docker Build**:
   - The Dockerfile includes this line:
     ```dockerfile
     COPY scripts/medicines.jso[n] /app/data/ || true
     ```
   - The `[n]` pattern makes the file optional in Docker
   - The `|| true` ensures build doesn't fail if file is missing
   - If present, the database is copied to `/app/data/medicines.json` in the container

## File Locations

- **Source**: `scripts/medicines.json` (in repository)
- **Artifact**: GitHub Actions artifact named `medicines-database`
- **Container**: `/app/data/medicines.json`

## Retention

- **Artifact**: 90 days in GitHub Actions
- **Repository**: Committed and versioned with the code

## Manual Trigger

To update the medicines database manually:
1. Go to Actions tab in GitHub
2. Select "Update Medicines Database" workflow
3. Click "Run workflow"

## Benefits

- Docker images automatically include the latest medicines database when available
- Build process is resilient: succeeds whether database exists or not
- Database is cached as an artifact for faster builds
- Monthly automatic updates keep the database current
