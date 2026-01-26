# URL Validation Implementation for Bijsluiter Field

## Overview
This document describes the server-side URL validation implementation added to address security feedback from PR #54.

## Problem Statement
The bijsluiter (package leaflet) field in the MedicineRequest and Medicine models accepted any URL without server-side validation. While the frontend HTML input type was set to 'url', there was no backend validation to ensure URLs were well-formed or pointed to trusted domains.

## Solution
Implemented comprehensive server-side URL validation with the following components:

### 1. ValidationUtils Utility Class
Created `src/main/kotlin/dev/gertjanassies/util/ValidationUtils.kt` with three validation functions:

#### isValidUrl(url: String?): Boolean
- Validates that a URL has a valid HTTP or HTTPS scheme
- Checks that the URL has a valid host
- Returns true for null or blank URLs (field is optional)
- Uses Java's URI class for validation

#### isTrustedDomain(url: String?): Boolean
- Validates that the URL's domain is in the trusted domains list
- Supports both exact domain matches and subdomain matches
- Case-insensitive comparison
- Returns true for null or blank URLs (field is optional)

#### validateBijsluiterUrl(url: String?): String?
- Combines both validations
- Returns null if validation passes
- Returns descriptive error message if validation fails

### 2. Trusted Domains
The following Dutch medicine information domains are whitelisted:
- geneesmiddeleninformatiebank.nl
- cbg-meb.nl
- farmacotherapeutischkompas.nl
- apotheek.nl
- rijksoverheid.nl

These domains are official sources for medicine information in the Netherlands.

### 3. Route Integration
Modified two endpoints in `MedicineRoutes.kt`:

#### POST /medicine
- Added validation before creating a new medicine
- Returns 400 Bad Request with error message if validation fails
- Validation occurs before calling the storage service

#### PUT /medicine/{id}
- Added validation before updating an existing medicine
- Returns 400 Bad Request with error message if validation fails
- Validation occurs before calling the storage service

### 4. Error Messages
Two types of validation errors are returned:
- "Invalid URL format. Must be a valid HTTP or HTTPS URL."
- "URL must be from a trusted domain (e.g., geneesmiddeleninformatiebank.nl, cbg-meb.nl)."

## Testing
Comprehensive test coverage was added:

### ValidationUtilsTest
- Tests for null and blank URLs (should pass)
- Tests for valid HTTP and HTTPS URLs
- Tests for invalid URL schemes (ftp, javascript, etc.)
- Tests for malformed URLs
- Tests for trusted domain validation
- Tests for subdomain support
- Tests for case-insensitivity
- Tests for the combined validateBijsluiterUrl function

### MedicineRoutesTest
Added integration tests for:
- Creating medicines with valid bijsluiter URLs (should succeed)
- Creating medicines with invalid URL format (should return 400)
- Creating medicines with untrusted domains (should return 400)
- Updating medicines with valid bijsluiter URLs (should succeed)
- Updating medicines with invalid URL format (should return 400)
- Updating medicines with untrusted domains (should return 400)

## Code Changes Summary
Files modified:
- src/main/kotlin/dev/gertjanassies/routes/MedicineRoutes.kt
- src/main/kotlin/dev/gertjanassies/routes/MedicineSearchRoutes.kt (fixed duplicate endpoint)
- src/test/kotlin/dev/gertjanassies/routes/MedicineRoutesTest.kt

Files created:
- src/main/kotlin/dev/gertjanassies/util/ValidationUtils.kt
- src/test/kotlin/dev/gertjanassies/util/ValidationUtilsTest.kt

## Security Considerations
- Server-side validation prevents malicious URLs from being stored
- Domain whitelisting ensures only trusted sources are used
- URL scheme validation prevents XSS attacks via javascript: URLs
- Validation occurs before data reaches the storage layer

## Testing Results
- All existing tests pass
- New validation tests pass
- No code review issues found
- No security vulnerabilities detected by CodeQL

## Future Enhancements
Potential improvements that could be considered:
- Make trusted domains configurable via environment variables
- Add URL reachability checks (verify the URL is actually accessible)
- Add certificate validation for HTTPS URLs
- Log validation failures for security monitoring
