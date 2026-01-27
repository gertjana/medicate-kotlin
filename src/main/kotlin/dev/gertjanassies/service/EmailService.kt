package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.raise.either
import dev.gertjanassies.model.User
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.SecureRandom
import java.util.*

sealed class EmailError(val message: String) {
    data class SendFailed(val reason: String) : EmailError("Failed to send email: $reason")
    data class InvalidEmail(val email: String) : EmailError("Invalid email address: $email")
    data class TokenGenerationFailed(val reason: String) : EmailError("Failed to generate token: $reason")
}

@Serializable
private data class ResendEmailRequest(
    val from: String,
    val to: List<String>,
    val subject: String,
    val html: String
)

@Serializable
private data class ResendEmailResponse(
    val id: String
)

/**
 * Email service using Resend API
 */
class EmailService(
    private val httpClient: HttpClient,
    private val redisService: RedisService,
    private val apiKey: String,
    private val appUrl: String,
    private val fromEmail: String = "no-reply@gertjanassies.dev"
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val logger = LoggerFactory.getLogger(EmailService::class.java)

    /**
     * Load email template from resources
     */
    private fun loadTemplate(templateName: String, locale: String = "en"): String {
        val resourcePath = "/email-templates/$templateName-$locale.html"
        return try {
            this::class.java.getResource(resourcePath)?.readText()
                ?: throw IllegalStateException("Template not found: $resourcePath")
        } catch (e: Exception) {
            logger.warn("Failed to load template $resourcePath, falling back to 'en': ${e.message}")
            // Fallback to English if the requested locale template doesn't exist
            if (locale != "en") {
                loadTemplate(templateName, "en")
            } else {
                throw e
            }
        }
    }

    /**
     * Replace template variables
     */
    private fun replaceTemplateVars(template: String, vars: Map<String, String>): String {
        var result = template
        vars.forEach { (key, value) ->
            result = result.replace("{{$key}}", value)
        }
        return result
    }

    /**
     * Generate a secure random token for password reset
     */
    private fun generateToken(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Store password reset token in Redis with TTL expiry
     * Redis will automatically delete the key after the specified time
     * Key format: medicate:{environment}:password_reset:{userId}:{token}
     */
    private suspend fun storePasswordResetToken(
        userId: String,
        token: String,
        ttlSeconds: Long = 3600 // 1 hour default
    ): Either<RedisError, Unit> {
        // Use the same key format as RedisService uses for all other keys
        // This ensures verifyPasswordResetToken can find the token via SCAN
        val environment = redisService.getEnvironment()
        val key = "medicate:$environment:password_reset:$userId:$token"
        logger.debug("Storing password reset token for user ID: $userId in environment: $environment with TTL: $ttlSeconds seconds")
        return redisService.setex(key, ttlSeconds, userId)
            .map { } // Convert Either<RedisError, String> to Either<RedisError, Unit>
    }

    /**
     * Send email using Resend API
     */
    private suspend fun sendEmail(
        to: String,
        subject: String,
        htmlContent: String
    ): Either<EmailError, String> = either {
        if (to.isBlank() || !to.contains("@")) {
            raise(EmailError.InvalidEmail(to))
        }

        val request = ResendEmailRequest(
            from = fromEmail,
            to = listOf(to),
            subject = subject,
            html = htmlContent
        )

        Either.catch {
            val response = httpClient.post("https://api.resend.com/emails") {
                header("Authorization", "Bearer $apiKey")
                contentType(ContentType.Application.Json)
                setBody(json.encodeToString(request))
            }

            if (response.status.isSuccess()) {
                val responseBody = response.bodyAsText()
                val resendResponse = json.decodeFromString<ResendEmailResponse>(responseBody)
                resendResponse.id
            } else {
                throw Exception("HTTP ${response.status.value}: ${response.bodyAsText()}")
            }
        }.mapLeft { e ->
            EmailError.SendFailed(e.message ?: "Unknown error")
        }.bind()
    }

    /**
     * Generate HTML content for password reset email
     */
    private fun generatePasswordResetEmailHtml(user: User, token: String, locale: String = "en"): String {
        val displayName = if (user.firstName.isNotBlank() && user.lastName.isNotBlank()) {
            "${user.firstName} ${user.lastName}"
        } else if (user.firstName.isNotBlank()) {
            user.firstName
        } else {
            user.username
        }

        val resetUrl = "$appUrl/reset-password?token=$token"

        val template = loadTemplate("password-reset", locale)
        return replaceTemplateVars(template, mapOf(
            "displayName" to displayName,
            "resetUrl" to resetUrl
        ))
    }

    /**
     * Get email subject for password reset based on locale
     */
    private fun getPasswordResetSubject(locale: String): String {
        return when (locale) {
            "nl" -> "Reset Uw Medicate Wachtwoord"
            else -> "Reset Your Medicate Password"
        }
    }

    /**
     * Reset password - generate token, store it with TTL, and send email
     */
    suspend fun resetPassword(user: User, locale: String = "en"): Either<EmailError, String> = either {
        // Validate user has an email
        if (user.email.isBlank()) {
            raise(EmailError.InvalidEmail("User has no email address"))
        }

        // Generate secure token
        val token = generateToken()

        // Store token in Redis with 1 hour TTL (3600 seconds)
        // Redis will automatically delete the key after 1 hour
        // Use user ID instead of username since multiple users can have same username
        storePasswordResetToken(user.id.toString(), token, ttlSeconds = 3600).mapLeft { redisError ->
            EmailError.SendFailed("Failed to store reset token: ${redisError.message}")
        }.bind()

        // Generate email HTML
        val emailHtml = generatePasswordResetEmailHtml(user, token, locale)

        // Send email
        val emailId = sendEmail(
            to = user.email,
            subject = getPasswordResetSubject(locale),
            htmlContent = emailHtml
        ).bind()

        emailId
    }

    /**
     * Validate email format
     */
    private val EMAIL_REGEX =
        Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

    private fun isValidEmail(email: String): Boolean {
        return EMAIL_REGEX.matches(email.trim())
    }

    /**
     * Send email verification email to newly registered user
     */
    suspend fun sendVerificationEmail(user: User, locale: String = "en"): Either<EmailError, String> = either {
        logger.debug("Sending verification email to ${user.email} for user ID: ${user.id}")

        // Validate email format
        if (!isValidEmail(user.email)) {
            raise(EmailError.InvalidEmail(user.email))
        }

        // Generate verification token
        val token = generateToken()

        // Store verification token in Redis with 24 hour expiry
        storeVerificationToken(user.id.toString(), token, ttlSeconds = 86400).mapLeft { redisError ->
            EmailError.SendFailed("Failed to store verification token: ${redisError.message}")
        }.bind()

        // Generate email HTML
        val emailHtml = generateVerificationEmailHtml(user, token, locale)

        // Send email
        val emailId = sendEmail(
            to = user.email,
            subject = getVerificationSubject(locale),
            htmlContent = emailHtml
        ).bind()

        emailId
    }

    /**
     * Store verification token in Redis with TTL expiry
     * Key format: medicate:{environment}:verification:token:{token}
     * This allows O(1) lookup with GET instead of O(N) SCAN operation
     */
    private suspend fun storeVerificationToken(
        userId: String,
        token: String,
        ttlSeconds: Long = 86400 // 24 hours default
    ): Either<RedisError, Unit> {
        val environment = redisService.getEnvironment()
        val key = "medicate:$environment:verification:token:$token"
        logger.debug("Storing verification token for user ID: $userId in environment: $environment with TTL: $ttlSeconds seconds")
        return redisService.setex(key, ttlSeconds, userId)
            .map { } // Convert Either<RedisError, String> to Either<RedisError, Unit>
    }

    /**
     * Generate HTML content for verification email
     */
    private fun generateVerificationEmailHtml(user: User, token: String, locale: String = "en"): String {
        val verificationLink = "$appUrl/activate-account?token=$token"
        val displayName = if (user.firstName.isNotBlank() && user.lastName.isNotBlank()) {
            "${user.firstName} ${user.lastName}"
        } else if (user.firstName.isNotBlank()) {
            user.firstName
        } else {
            user.username
        }

        val template = loadTemplate("account-activation", locale)
        return replaceTemplateVars(template, mapOf(
            "displayName" to displayName,
            "verificationLink" to verificationLink
        ))
    }

    /**
     * Get email subject for account verification based on locale
     */
    private fun getVerificationSubject(locale: String): String {
        return when (locale) {
            "nl" -> "Verifieer Uw Medicate Account"
            else -> "Verify Your Medicate Account"
        }
    }
}
