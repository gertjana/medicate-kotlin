package dev.gertjanassies.util

import java.net.URI

object ValidationUtils {
    private val TRUSTED_DOMAINS = setOf(
        "geneesmiddeleninformatiebank.nl",
        "cbg-meb.nl",
        "farmacotherapeutischkompas.nl",
        "apotheek.nl",
        "rijksoverheid.nl"
    )

    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return true
        }

        return try {
            val uri = URI(url)
            uri.scheme != null && uri.host != null &&
                (uri.scheme == "http" || uri.scheme == "https")
        } catch (e: Exception) {
            false
        }
    }

    fun isTrustedDomain(url: String?): Boolean {
        if (url.isNullOrBlank()) {
            return true
        }

        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase() ?: return false
            TRUSTED_DOMAINS.any { trustedDomain ->
                host == trustedDomain || host.endsWith(".$trustedDomain")
            }
        } catch (e: Exception) {
            false
        }
    }

    fun validateBijsluiterUrl(url: String?): String? {
        if (!isValidUrl(url)) {
            return "Invalid URL format. Must be a valid HTTP or HTTPS URL."
        }

        if (!isTrustedDomain(url)) {
            return "URL must be from a trusted domain (e.g., geneesmiddeleninformatiebank.nl, cbg-meb.nl)."
        }

        return null
    }
}
