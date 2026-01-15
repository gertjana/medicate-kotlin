package dev.gertjanassies.service

import arrow.core.Either
import arrow.core.raise.either
import dev.gertjanassies.model.PasswordResetToken
import dev.gertjanassies.model.User
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import java.time.LocalDateTime
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
    private val fromEmail: String = "noreply@medicate.app"
) {
    private val json = Json { ignoreUnknownKeys = true }

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
     * Store password reset token in Redis with expiry
     */
    private suspend fun storePasswordResetToken(
        username: String,
        token: String,
        expiresAt: LocalDateTime
    ): Either<RedisError, Unit> {
        val resetToken = PasswordResetToken(token = token, expiresAt = expiresAt)
        return redisService.set("password_reset:$username", json.encodeToString(resetToken))
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
    private fun generatePasswordResetEmailHtml(username: String, token: String): String {
        val resetUrl = "https://medicate.app/reset-password?token=$token"
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
                        color: white;
                        text-decoration: none;
                        border-radius: 4px;
                        margin: 20px 0;
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
                        <h2>Hello $username,</h2>
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
     * Reset password - generate token, store it, and send email
     */
    suspend fun resetPassword(user: User): Either<EmailError, String> = either {
        // Validate user has an email
        if (user.email.isBlank()) {
            raise(EmailError.InvalidEmail("User has no email address"))
        }

        // Generate secure token
        val token = generateToken()

        // Set expiry to 1 hour from now
        val expiresAt = LocalDateTime.now().plusHours(1)

        // Store token in Redis
        storePasswordResetToken(user.username, token, expiresAt).mapLeft { redisError ->
            EmailError.SendFailed("Failed to store reset token: ${redisError.message}")
        }.bind()

        // Generate email HTML
        val emailHtml = generatePasswordResetEmailHtml(user.username, token)

        // Send email
        val emailId = sendEmail(
            to = user.email,
            subject = "Reset Your Medicate Password",
            htmlContent = emailHtml
        ).bind()

        emailId
    }
}
