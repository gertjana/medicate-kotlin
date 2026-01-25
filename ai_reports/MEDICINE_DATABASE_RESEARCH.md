# Medicine Database Research - EU Focus

## Investigation: Public Medicine Databases (EU Sources)

**Date:** January 24, 2026
**Updated:** January 24, 2026
**Purpose:** Research publicly available EU-focused medicine/drug databases that could be integrated into the Medicate application to allow users to search and select from a list of known medicines.

**Criteria:**
- EU/European focus (excluding US-only sources)
- Free to use
- API or downloadable data available
- Must include drug names and dosages

---

## Available EU Medicine Databases

### 1. WHO ATC/DDD Index
- **Source:** World Health Organization
- **URL:** https://www.whocc.no/atc_ddd_index/
- **Format:** Web interface, downloadable files
- **Content:**
  - Anatomical Therapeutic Chemical (ATC) classification
  - Drug names (generic)
  - Defined Daily Doses (DDD)
  - International scope
- **License:** Free for non-commercial use
- **Update Frequency:** Annually

**Pros:**
- International scope (not U.S.-centric)
- WHO standard classification
- Includes dosage information (DDD)
- Free to use

**Cons:**
- No API available
- Primarily generic names (may lack brand names)
- Requires manual download and parsing
- Focused on classification more than comprehensive drug list

### 2. European Medicines Agency (EMA) Database
- **Source:** European Medicines Agency
- **URL:** https://www.ema.europa.eu/en/medicines
- **Format:** Web interface, some downloadable data
- **Content:**
  - EU-approved medicines
  - Brand and generic names
  - Active substances
  - Therapeutic areas
- **License:** Public (EMA data)

**Pros:**
- European focus (good for EU users)
- Official regulatory data
- Free to access

**Cons:**
- No comprehensive API
- Focused on EU-approved medicines only
- Less structured for programmatic access

### 3. SPOR (Substances, Products, Organisations and Referentials)
- **Source:** European Medicines Agency (EMA)
- **URL:** https://spor.ema.europa.eu/
- **Format:** API and downloadable data
- **Content:**
  - EU medicines database
  - Product names (brand and generic)
  - Active substances
  - Marketing authorizations
  - Dosage forms and strengths
- **License:** Free (EU public data)
- **API:** Yes - SPOR API available
- **Update Frequency:** Regular updates

**Pros:**
- Official EU medicine database
- Free API access
- Includes dosage information
- European regulatory standard
- Comprehensive EU market coverage

**Cons:**
- Complex data structure
- Requires understanding of EU regulatory terminology
- Documentation could be better
- May require API registration

### 4. ChEMBL Database
- **Source:** European Bioinformatics Institute (EBI/EMBL)
- **URL:** https://www.ebi.ac.uk/chembl/
- **Format:** API and downloadable files
- **Content:**
  - Drug compounds
  - Generic and brand names
  - Chemical structures
  - Biological activities
- **License:** Free and open (CC BY-SA 3.0)
- **API:** Yes - RESTful API available
- **Update Frequency:** Regular updates

**Pros:**
- European source (EMBL-EBI)
- Free and open source
- Good API documentation
- Includes international drugs
- No registration required for API

**Cons:**
- More focused on bioactivity than clinical use
- May lack dosage forms for some drugs
- Scientific/research orientation

### 5. PubChem (International, Free API)
- **Source:** National Institutes of Health (NIH) - International scope
- **URL:** https://pubchem.ncbi.nlm.nih.gov/
- **Format:** API and downloadable files
- **Content:**
  - Chemical compounds including drugs
  - Drug names (generic and brand)
  - Chemical structures
  - International scope
- **License:** Public domain
- **API:** Yes - multiple APIs available (PUG REST, PUG SOAP)
- **Update Frequency:** Daily

**Pros:**
- Free API access
- No authentication required
- International drug coverage (not US-only)
- Includes European medicines
- Well-documented API
- JSON/XML format

**Cons:**
- Focused on chemistry rather than clinical dosing
- May require mapping to get dosage forms
- Large dataset may need filtering

### 6. Dutch Medicine Database (G-Standaard/Z-Index)
- **Source:** Z-Index (Netherlands)
- **URL:** https://www.z-indeIf x.nl/
- **Format:** API and downloadable files (commercial license required for full access)
- **Content:**
  - Dutch medicine database
  - Brand and generic names
  - Dosage forms and strengths
  - ATC codes
- **License:** Free for limited use / Commercial license available

**Pros:**
- Comprehensive Dutch medicine database
- Includes dosage information
- Well-structured data

**Cons:**
- Netherlands-specific
- Full API access requires commercial license
- Limited to Dutch market

---

## EU-Focused Recommendations

### Option 1: WHO ATC/DDD Index + EMA SPOR (Recommended for EU)
**Best for:** EU users, free access, official data

**Implementation approach:**
1. Download WHO ATC/DDD database for medicine classification
2. Use EMA SPOR API for EU-approved medicines lookup
3. Create local search index combining both sources
4. Implement autocomplete against combined dataset
5. Periodic updates from both sources

**Sample implementation:**
- Weekly download of WHO ATC/DDD updates
- Query SPOR API for detailed medicine information
- Cache results in Redis for fast lookup
- Fallback to manual entry if medicine not found

**Pros:**
- Both free EU/international sources
- Official regulatory data
- Includes dosage information (DDD from WHO)
- No US-centric bias

**Cons:**
- Requires combining multiple sources
- SPOR API may have learning curve
- More implementation work than single API

**Estimated effort:** 4-6 days

### Option 2: ChEMBL API (Easiest EU Option)
**Best for:** Quick integration, European source, free API

**Implementation approach:**
1. Create medicine search endpoint using ChEMBL API
2. Query by compound name or synonym
3. Map ChEMBL data to Medicine model
4. Implement autocomplete in frontend
5. Cache frequently searched medicines

**Sample API call:**
```bash
curl "https://www.ebi.ac.uk/chembl/api/data/molecule/search.json?q=paracetamol&limit=10"
```

**Pros:**
- European source (EMBL-EBI)
- Free RESTful API
- No authentication required
- Good JSON documentation
- International drug coverage

**Cons:**
- May lack some dosage form details
- Scientific focus rather than clinical

**Estimated effort:** 2-3 days

### Option 3: PubChem API (Best for International Coverage)
**Best for:** International drug database, free API, well-documented

**Implementation approach:**
1. Use PubChem PUG REST API for medicine search
2. Search by compound name or synonym
3. Extract relevant drug information
4. Implement autocomplete interface
5. Cache search results locally

**Sample API call:**
```bash
curl "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound/name/aspirin/JSON"
```

**Pros:**
- Free API with no authentication
- International coverage (includes EU medicines)
- Well-documented
- JSON format
- Fast and reliable

**Cons:**
- May need additional mapping for dosage forms
- Chemistry-focused rather than clinical

**Estimated effort:** 2-3 days

### Option 4: Combined Local Database (Best for Control)
**Best for:** Full control, offline capability, curated list

**Implementation approach:**
1. Download WHO ATC/DDD database
2. Parse and import commonly prescribed medicines
3. Optionally enhance with EMA SPOR data
4. Store in Redis with search index
5. Create backend search endpoint
6. Periodic manual/automated updates

**Pros:**
- Fast local searches
- No external API dependencies
- Can curate for your user base
- EU-focused data
- Works offline

**Cons:**
- More implementation work
- Need update mechanism
- Initial data preparation effort

**Estimated effort:** 5-7 days

---

## Proposed Implementation Plan for EU Users

### Phase 1: Medicine Search Feature (Recommended: ChEMBL API)

1. **Backend Service** (`MedicineSearchService.kt`):
   ```kotlin
   class MedicineSearchService {
       suspend fun searchMedicines(query: String): Either<SearchError, List<MedicineSearchResult>>

       // ChEMBL API integration
       private suspend fun searchChEMBL(query: String): Either<SearchError, List<ChEMBLResult>>
   }
   ```

2. **New API Endpoint** (`/api/medicine/search?q=paracetamol`):
   - Returns list of matching medicines from ChEMBL
   - Each result includes: name, synonyms, basic info

3. **Frontend Enhancement**:
   - Add autocomplete/search input to medicine form
   - Display search results as user types (debounced)
   - Allow selection to pre-fill medicine name
   - Still allow manual entry for dosage and other details

4. **Data Model**:
   ```kotlin
   data class MedicineSearchResult(
       val name: String,              // Primary name
       val synonyms: List<String>,    // Alternative names
       val description: String?,      // Brief description
       val atcCode: String?          // ATC classification if available
   )
   ```

### Phase 2: Enhanced with WHO ATC/DDD (Optional)

1. Download and import WHO ATC/DDD database
2. Add DDD (Defined Daily Dose) information to search results
3. Map medicines to therapeutic categories
4. Suggest standard dosages based on DDD

### Phase 3: Local Cache and Optimization

1. Cache ChEMBL search results in Redis
2. Build index of frequently searched medicines
3. Reduce API calls with intelligent caching
4. Add fallback for offline operation

---

## Testing Recommendations

1. **Test with ChEMBL API first** (no setup required)
2. Verify coverage of common European medicines
3. Test with different language inputs if needed
4. Evaluate user feedback on search quality
5. Monitor API response times and reliability

---

## Privacy & Security Considerations

- Medicine searches should NOT be logged with user identifiers
- Use HTTPS for all API calls
- Cache search results appropriately (respect API terms)
- Consider rate limiting to avoid API abuse
- No personal health data sent to external APIs

---

## Next Steps

1. **Decide on initial approach** (recommend ChEMBL for EU MVP)
2. **Create proof-of-concept** with simple ChEMBL search
3. **Test with real users** to validate usefulness
4. **Iterate based on feedback**
5. **Consider combined approach** if single source insufficient

---

## Resources

- WHO ATC/DDD Index: https://www.whocc.no/atc_ddd_index/
- EMA SPOR: https://spor.ema.europa.eu/
- ChEMBL API Documentation: https://chembl.gitbook.io/chembl-interface-documentation/web-services/chembl-data-web-services
- PubChem API: https://pubchem.ncbi.nlm.nih.gov/docs/pug-rest
- EMA Medicines Database: https://www.ema.europa.eu/en/medicines

---

## Conclusion

**Medicine database integration is feasible with EU-focused free sources.**

The **ChEMBL API** (European Bioinformatics Institute) provides the easiest path to implementation with good international coverage and European origin. Combined with **WHO ATC/DDD** data for dosage standards, this provides a solid foundation for EU users.

**Recommended first step:** Create a simple search endpoint using ChEMBL API with autocomplete in the frontend, while still allowing manual entry for flexibility. This gives users the convenience of searching without requiring strict medicine selection.

**Alternative approach:** For more comprehensive EU regulatory data, consider combining WHO ATC/DDD downloads with EMA SPOR API queries, though this requires more implementation effort.
