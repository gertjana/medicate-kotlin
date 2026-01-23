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
    private fun generatePasswordResetEmailHtml(user: User, token: String): String {
        val displayName = if (user.firstName.isNotBlank() && user.lastName.isNotBlank()) {
            "${user.firstName} ${user.lastName}"
        } else if (user.firstName.isNotBlank()) {
            user.firstName
        } else {
            user.username
        }

        val resetUrl = "$appUrl/reset-password?token=$token"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                    .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                    .header { background-color: #4682b4; color: white; padding: 20px; text-align: center; }
                    .content { background-color: #f9f9f9; padding: 30px; }
                    .button {
                        display: inline-block;
                        padding: 12px 24px;
                        background-color: #4682b4;
                        color: white !important;
                        text-decoration: none;
                        border-radius: 4px;
                        margin: 20px 0;
                        font-weight: bold;
                    }
                    .button:visited {
                        color: white !important;
                        text-decoration: none;
                    }
                    .button:hover {
                        color: white !important;
                        text-decoration: none;
                    }
                    .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Medicate - Password Reset</h1>
                    </div>
                    <div class="content">
                        <h2>Hello $displayName,</h2>
                        <p>We received a request to reset your password. Click the button below to create a new password:</p>
                        <p style="text-align: center;">
                            <a href="$resetUrl" class="button">Reset Password</a>
                        </p>
                        <p>Or copy and paste this link into your browser:</p>
                        <p style="word-break: break-all; color: #4682b4;">$resetUrl</p>
                        <p><strong>This link will expire in 1 hour.</strong></p>
                        <p>If you didn't request a password reset, you can safely ignore this email.</p>
                    </div>
                    <div class="footer">
                        <p>&copy; 2026 Medicate. All rights reserved.</p>
                        <p>This is an automated email. Please do not reply.</p>
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Reset password - generate token, store it with TTL, and send email
     */
    suspend fun resetPassword(user: User): Either<EmailError, String> = either {
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
        val emailHtml = generatePasswordResetEmailHtml(user, token)

        // Send email
        val emailId = sendEmail(
            to = user.email,
            subject = "Reset Your Medicate Password",
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
    suspend fun sendVerificationEmail(user: User): Either<EmailError, String> = either {
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
        val emailHtml = generateVerificationEmailHtml(user, token)

        // Send email
        val emailId = sendEmail(
            to = user.email,
            subject = "Verify Your Medicate Account",
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
    private fun generateVerificationEmailHtml(user: User, token: String): String {
        val verificationLink = "$appUrl/activate-account?token=$token"
        val displayName = if (user.firstName.isNotBlank() && user.lastName.isNotBlank()) {
            "${user.firstName} ${user.lastName}"
        } else if (user.firstName.isNotBlank()) {
            user.firstName
        } else {
            user.username
        }

        return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <style>
                body { font-family: Arial, sans-serif; line-height: 1.6; color: #333; }
                .container { max-width: 600px; margin: 0 auto; padding: 20px; }
                .header { background-color: #4682b4; color: white; padding: 20px; text-align: center; }
                .content { background-color: #f9f9f9; padding: 30px; }
                .button {
                    display: inline-block;
                    padding: 12px 24px;
                    background-color: #4682b4;
                    color: white !important;
                    text-decoration: none;
                    border-radius: 4px;
                    margin: 20px 0;
                    font-weight: bold;
                }
                .button:visited {
                    color: white !important;
                    text-decoration: none;
                }
                .button:hover {
                    color: white !important;
                    text-decoration: none;
                }
                .footer { text-align: center; padding: 20px; color: #666; font-size: 12px; }
            </style>
        </head>
        <body>
            <div class="container">
                <div class="header">
                    <h1>Medicate - Activate Your Account</h1>
                </div>
                <div class="content">
                    <h2>Hello $displayName,</h2>
                    <p>Thank you for registering with Medicate. To complete your registration and activate your account, please verify your email address by clicking the button below:</p>
                    <p style="text-align: center;">
                        <a href="$verificationLink" class="button">Activate Account</a>
                    </p>
                    <p>Or copy and paste this link into your browser:</p>
                    <p style="word-break: break-all; color: #4682b4;">$verificationLink</p>
                    <p><strong>This link will expire in 24 hours.</strong></p>
                    <p>If you didn't create an account with Medicate, you can safely ignore this email.</p>
                </div>
                <div class="footer">
                    <p>&copy; 2026 Medicate. All rights reserved.</p>
                    <p>This is an automated email. Please do not reply.</p>
                </div>
            </div>
        </body>
        </html>
        """.trimIndent()
    }
}
