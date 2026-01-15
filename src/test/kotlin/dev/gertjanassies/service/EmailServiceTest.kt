package dev.gertjanassies.service

import dev.gertjanassies.model.PasswordResetToken
import dev.gertjanassies.model.User
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.lettuce.core.RedisFuture
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime
import java.util.concurrent.CompletableFuture

class EmailServiceTest : FunSpec({
    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService
    lateinit var emailService: EmailService
    val testApiKey = "test_api_key_123"
    val testAppUrl = "http://localhost:5173"
    val environment = "test"
    val json = Json { ignoreUnknownKeys = true }

    // Helper to create RedisFuture mocks
    fun <T> createRedisFutureMock(value: T): RedisFuture<T> {
        val future = CompletableFuture<T>()
        future.complete(value)
        return mockk<RedisFuture<T>> {
            every { toCompletableFuture() } returns future
        }
    }

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    context("resetPassword") {
        test("should generate token, store it in Redis, and send email successfully") {
            // Mock Redis connection
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            // Mock successful Resend API response
            val mockEngine = MockEngine { request ->
                // Verify request to Resend API
                request.url.toString() shouldBe "https://api.resend.com/emails"
                request.headers["Authorization"] shouldBe "Bearer $testApiKey"
                request.method shouldBe HttpMethod.Post

                respond(
                    content = ByteReadChannel("""{"id":"email_123456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(json)
                }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123"
            )

            val result = emailService.resetPassword(user)

            // Should succeed
            result.isRight() shouldBe true
            val emailId = result.getOrNull()!!
            emailId shouldBe "email_123456"

            // Verify Redis was called to store token
            verify(exactly = 1) {
                mockAsyncCommands.set(
                    match { it == "password_reset:testuser" },
                    match { tokenJson ->
                        // Verify token structure
                        val token = json.decodeFromString<PasswordResetToken>(tokenJson)
                        token.token.isNotBlank()
                        token.expiresAt.isAfter(LocalDateTime.now())
                        true
                    }
                )
            }
        }

        test("should return error when user has no email") {
            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }
            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "testuser",
                email = "",
                passwordHash = "hash123"
            )

            val result = emailService.resetPassword(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()!!
            error.shouldBeInstanceOf<EmailError.InvalidEmail>()
            error.message shouldContain "no email address"
        }

        test("should return error when Redis storage fails") {
            // Mock Redis connection to throw exception
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } throws RuntimeException("Connection failed")

            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"id":"email_123"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123"
            )

            val result = emailService.resetPassword(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()!!
            error.shouldBeInstanceOf<EmailError.SendFailed>()
            error.message shouldContain "Failed to store reset token"
        }

        test("should return error when Resend API returns error") {
            // Mock successful Redis connection
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"error":"Invalid API key"}"""),
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123"
            )

            val result = emailService.resetPassword(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()!!
            error.shouldBeInstanceOf<EmailError.SendFailed>()
            error.message shouldContain "401"
        }

        test("should return error for invalid email format") {
            // Mock Redis connection since we reach that far
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel(""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }
            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "testuser",
                email = "invalid-email",
                passwordHash = "hash123"
            )

            val result = emailService.resetPassword(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()!!
            error.shouldBeInstanceOf<EmailError.InvalidEmail>()
        }

        test("should generate unique tokens for different requests") {
            // Mock Redis connection and capture stored tokens
            every { mockConnection.async() } returns mockAsyncCommands
            val capturedTokens = mutableListOf<String>()
            every { mockAsyncCommands.set(any(), capture(capturedTokens)) } returns createRedisFutureMock("OK")

            val mockEngine = MockEngine {
                respond(
                    content = ByteReadChannel("""{"id":"email_123"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123"
            )

            // Generate two tokens
            emailService.resetPassword(user)
            emailService.resetPassword(user)

            // Verify tokens are different
            val token1 = json.decodeFromString<PasswordResetToken>(capturedTokens[0]).token
            val token2 = json.decodeFromString<PasswordResetToken>(capturedTokens[1]).token
            token1.isNotBlank() shouldBe true
            token2.isNotBlank() shouldBe true
            (token1 != token2) shouldBe true
        }

        test("should include user information in email content") {
            // Mock Redis connection
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")

            var capturedEmailBody = ""
            val mockEngine = MockEngine { request ->
                capturedEmailBody = request.body.toByteArray().decodeToString()
                respond(
                    content = ByteReadChannel("""{"id":"email_123"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                username = "johndoe",
                email = "john@example.com",
                passwordHash = "hash123"
            )

            emailService.resetPassword(user)

            // Verify email contains username
            capturedEmailBody shouldContain "johndoe"
            capturedEmailBody shouldContain "john@example.com"
            capturedEmailBody shouldContain "Reset Password"
        }
    }

    context("verifyPasswordResetToken") {
        test("should successfully verify valid token and return username") {
            val username = "testuser"
            val token = "validtoken123"
            val expiresAt = LocalDateTime.now().plusHours(1)
            val resetToken = PasswordResetToken(token = token, expiresAt = expiresAt)
            val resetTokenJson = Json.encodeToString(resetToken)
            val key = "$environment:password_reset:$username"

            every { mockConnection.async() } returns mockAsyncCommands

            // Mock scan for password reset keys
            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            // Mock get for the token
            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(resetTokenJson)

            // Mock delete
            every { mockAsyncCommands.del(key) } returns createRedisFutureMock(1L)

            val result = redisService.verifyPasswordResetToken(token)

            result.isRight() shouldBe true
            result.getOrNull() shouldBe username

            verify(exactly = 1) { mockAsyncCommands.del(key) }
        }

        test("should return error for expired token") {
            val username = "testuser"
            val token = "expiredtoken123"
            val expiresAt = LocalDateTime.now().minusHours(1) // Expired
            val resetToken = PasswordResetToken(token = token, expiresAt = expiresAt)
            val resetTokenJson = Json.encodeToString(resetToken)
            val key = "$environment:password_reset:$username"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(resetTokenJson)

            val result = redisService.verifyPasswordResetToken(token)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldContain "Invalid or expired"
        }

        test("should return error for non-existent token") {
            val token = "nonexistenttoken"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns emptyList()
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            val result = redisService.verifyPasswordResetToken(token)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("should return error when token doesn't match") {
            val username = "testuser"
            val storedToken = "storedtoken123"
            val requestedToken = "differenttoken456"
            val expiresAt = LocalDateTime.now().plusHours(1)
            val resetToken = PasswordResetToken(token = storedToken, expiresAt = expiresAt)
            val resetTokenJson = Json.encodeToString(resetToken)
            val key = "$environment:password_reset:$username"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(resetTokenJson)

            val result = redisService.verifyPasswordResetToken(requestedToken)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
        }

        test("should verify token is deleted after successful verification") {
            val username = "testuser"
            val token = "validtoken123"
            val expiresAt = LocalDateTime.now().plusHours(1)
            val resetToken = PasswordResetToken(token = token, expiresAt = expiresAt)
            val resetTokenJson = Json.encodeToString(resetToken)
            val key = "$environment:password_reset:$username"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            every { mockAsyncCommands.get(key) } returns createRedisFutureMock(resetTokenJson)
            every { mockAsyncCommands.del(key) } returns createRedisFutureMock(1L)

            redisService.verifyPasswordResetToken(token)

            verify(exactly = 1) { mockAsyncCommands.del(key) }
        }

        test("should handle multiple tokens and find the correct one") {
            val username1 = "user1"
            val username2 = "user2"
            val token1 = "token1"
            val token2 = "token2"
            val expiresAt = LocalDateTime.now().plusHours(1)

            val resetToken1 = PasswordResetToken(token = token1, expiresAt = expiresAt)
            val resetToken2 = PasswordResetToken(token = token2, expiresAt = expiresAt)
            val resetTokenJson1 = Json.encodeToString(resetToken1)
            val resetTokenJson2 = Json.encodeToString(resetToken2)

            val key1 = "$environment:password_reset:$username1"
            val key2 = "$environment:password_reset:$username2"

            every { mockConnection.async() } returns mockAsyncCommands

            val scanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { scanCursor.keys } returns listOf(key1, key2)
            every { scanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)

            every { mockAsyncCommands.get(key1) } returns createRedisFutureMock(resetTokenJson1)
            every { mockAsyncCommands.get(key2) } returns createRedisFutureMock(resetTokenJson2)
            every { mockAsyncCommands.del(key2) } returns createRedisFutureMock(1L)

            val result = redisService.verifyPasswordResetToken(token2)

            result.isRight() shouldBe true
            result.getOrNull() shouldBe username2
            verify(exactly = 1) { mockAsyncCommands.del(key2) }
        }
    }
})
