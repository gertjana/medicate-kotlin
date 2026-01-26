package dev.gertjanassies.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ValidationUtilsTest : FunSpec({

    context("isValidUrl") {
        test("should accept null URLs") {
            ValidationUtils.isValidUrl(null) shouldBe true
        }

        test("should accept blank URLs") {
            ValidationUtils.isValidUrl("") shouldBe true
            ValidationUtils.isValidUrl("   ") shouldBe true
        }

        test("should accept valid HTTP URLs") {
            ValidationUtils.isValidUrl("http://example.com") shouldBe true
            ValidationUtils.isValidUrl("http://example.com/path") shouldBe true
        }

        test("should accept valid HTTPS URLs") {
            ValidationUtils.isValidUrl("https://example.com") shouldBe true
            ValidationUtils.isValidUrl("https://example.com/path/to/resource") shouldBe true
        }

        test("should reject URLs without scheme") {
            ValidationUtils.isValidUrl("example.com") shouldBe false
            ValidationUtils.isValidUrl("www.example.com") shouldBe false
        }

        test("should reject URLs with invalid scheme") {
            ValidationUtils.isValidUrl("ftp://example.com") shouldBe false
            ValidationUtils.isValidUrl("javascript:alert(1)") shouldBe false
        }

        test("should reject malformed URLs") {
            ValidationUtils.isValidUrl("not a url") shouldBe false
            ValidationUtils.isValidUrl("http://") shouldBe false
        }
    }

    context("isTrustedDomain") {
        test("should accept null URLs") {
            ValidationUtils.isTrustedDomain(null) shouldBe true
        }

        test("should accept blank URLs") {
            ValidationUtils.isTrustedDomain("") shouldBe true
            ValidationUtils.isTrustedDomain("   ") shouldBe true
        }

        test("should accept URLs from trusted domains") {
            ValidationUtils.isTrustedDomain("https://geneesmiddeleninformatiebank.nl") shouldBe true
            ValidationUtils.isTrustedDomain("https://cbg-meb.nl/path") shouldBe true
            ValidationUtils.isTrustedDomain("https://farmacotherapeutischkompas.nl") shouldBe true
            ValidationUtils.isTrustedDomain("https://apotheek.nl") shouldBe true
            ValidationUtils.isTrustedDomain("https://rijksoverheid.nl") shouldBe true
        }

        test("should accept URLs from subdomains of trusted domains") {
            ValidationUtils.isTrustedDomain("https://www.geneesmiddeleninformatiebank.nl") shouldBe true
            ValidationUtils.isTrustedDomain("https://db.cbg-meb.nl") shouldBe true
        }

        test("should reject URLs from untrusted domains") {
            ValidationUtils.isTrustedDomain("https://example.com") shouldBe false
            ValidationUtils.isTrustedDomain("https://malicious.com") shouldBe false
        }

        test("should reject URLs from domains that contain trusted domain as substring") {
            ValidationUtils.isTrustedDomain("https://fakecbg-meb.nl.malicious.com") shouldBe false
        }

        test("should be case-insensitive") {
            ValidationUtils.isTrustedDomain("https://CBG-MEB.NL") shouldBe true
            ValidationUtils.isTrustedDomain("https://Geneesmiddeleninformatiebank.NL") shouldBe true
        }
    }

    context("validateBijsluiterUrl") {
        test("should return null for valid trusted URLs") {
            ValidationUtils.validateBijsluiterUrl("https://geneesmiddeleninformatiebank.nl/path") shouldBe null
            ValidationUtils.validateBijsluiterUrl("https://www.cbg-meb.nl/document.pdf") shouldBe null
        }

        test("should return null for null or blank URLs") {
            ValidationUtils.validateBijsluiterUrl(null) shouldBe null
            ValidationUtils.validateBijsluiterUrl("") shouldBe null
        }

        test("should return error for invalid URL format") {
            val error = ValidationUtils.validateBijsluiterUrl("not a url")
            error shouldBe "Invalid URL format. Must be a valid HTTP or HTTPS URL."
        }

        test("should return error for untrusted domains") {
            val error = ValidationUtils.validateBijsluiterUrl("https://example.com/document.pdf")
            error shouldBe "URL must be from a trusted domain (e.g., geneesmiddeleninformatiebank.nl, cbg-meb.nl)."
        }

        test("should return error for URLs without HTTP/HTTPS scheme") {
            val error = ValidationUtils.validateBijsluiterUrl("ftp://cbg-meb.nl/file")
            error shouldBe "Invalid URL format. Must be a valid HTTP or HTTPS URL."
        }
    }
})
