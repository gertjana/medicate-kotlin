package dev.gertjanassies.service

import dev.gertjanassies.model.User
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
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
import kotlinx.serialization.json.Json
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
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createRedisFutureMock("OK")

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

            emailService = EmailService(
                HttpClient(mockEngine) {
                    install(ContentNegotiation) {
                        json(json)
                    }
                },
                redisService,
                testApiKey,
                testAppUrl
            )

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123"
            )

            val result = emailService.resetPassword(user)

            // Should succeed
            result.isRight() shouldBe true
            val emailId = result.getOrNull()!!
            emailId shouldBe "email_123456"

            // Verify Redis setex was called with TTL and user ID (not username)
            verify(exactly = 1) {
                mockAsyncCommands.setex(
                    match { it.startsWith("medicate:test:password_reset:${user.id}:") },
                    3600L, // 1 hour TTL
                    user.id.toString() // Store user ID as value (changed from username)
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
                id = java.util.UUID.randomUUID(),
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
            every { mockAsyncCommands.setex(any(), any(), any()) } throws RuntimeException("Connection failed")

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
                id = java.util.UUID.randomUUID(),
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
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createRedisFutureMock("OK")

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
                id = java.util.UUID.randomUUID(),
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
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createRedisFutureMock("OK")

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
                id = java.util.UUID.randomUUID(),
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
            // Mock Redis connection and capture stored keys (which contain tokens)
            every { mockConnection.async() } returns mockAsyncCommands
            val capturedKeys = mutableListOf<String>()
            every { mockAsyncCommands.setex(capture(capturedKeys), any(), any()) } returns createRedisFutureMock("OK")

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
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123"
            )

            // Generate two tokens
            emailService.resetPassword(user)
            emailService.resetPassword(user)

            // Verify tokens are different (extracted from keys)
            // Keys are in format: "medicate:test:password_reset:{userId}:TOKEN"
            val token1 = capturedKeys[0].substringAfterLast(":")
            val token2 = capturedKeys[1].substringAfterLast(":")
            token1.isNotBlank() shouldBe true
            token2.isNotBlank() shouldBe true
            (token1 != token2) shouldBe true
        }

        test("should include user information in email content") {
            // Mock Redis connection
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createRedisFutureMock("OK")

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
                id = java.util.UUID.randomUUID(),
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

    context("sendVerificationEmail") {
        test("should send verification email successfully and store token") {
            // Mock Redis connection
            every { mockConnection.async() } returns mockAsyncCommands
            val capturedToken = slot<String>()
            val capturedKey = slot<String>()
            every {
                mockAsyncCommands.setex(capture(capturedKey), 86400L, capture(capturedToken))
            } returns createRedisFutureMock("OK")

            var capturedEmailBody = ""
            val mockEngine = MockEngine { request ->
                capturedEmailBody = request.body.toByteArray().decodeToString()
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                firstName = "John",
                lastName = "Doe",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isRight() shouldBe true
            val emailId = result.getOrNull()
            emailId shouldBe "email_456"

            // Verify token was stored with correct format and TTL
            val storedKey = capturedKey.captured
            storedKey shouldContain "medicate:test:verification:${user.id}:"
            verify(exactly = 1) {
                mockAsyncCommands.setex(
                    match { it.startsWith("medicate:test:verification:${user.id}:") },
                    86400L,
                    user.id.toString()
                )
            }

            // Verify email content
            capturedEmailBody shouldContain "test@example.com"
            capturedEmailBody shouldContain "Verify Your Medicate Account"
            capturedEmailBody shouldContain "John Doe"
            capturedEmailBody shouldContain "activate-account?token="
        }

        test("should return InvalidEmail error for blank email") {
            every { mockConnection.async() } returns mockAsyncCommands

            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()
            error.shouldBeInstanceOf<EmailError.InvalidEmail>()
            error.message shouldContain "Invalid email address"
        }

        test("should return InvalidEmail error for email without @") {
            every { mockConnection.async() } returns mockAsyncCommands

            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "invalidemail.com",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()
            error.shouldBeInstanceOf<EmailError.InvalidEmail>()
        }

        test("should return SendFailed error when token storage fails") {
            every { mockConnection.async() } returns mockAsyncCommands
            every {
                mockAsyncCommands.setex(any(), any(), any())
            } returns createFailedRedisFutureMock(RuntimeException("Redis error"))

            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()
            error.shouldBeInstanceOf<EmailError.SendFailed>()
            error.message shouldContain "Failed to store verification token"
        }

        test("should return SendFailed error when email API fails") {
            every { mockConnection.async() } returns mockAsyncCommands
            every {
                mockAsyncCommands.setex(any(), any(), any())
            } returns createRedisFutureMock("OK")

            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""{"error":"API key invalid"}"""),
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isLeft() shouldBe true
            val error = result.leftOrNull()
            error.shouldBeInstanceOf<EmailError.SendFailed>()
        }

        test("should use firstName only when lastName is blank") {
            every { mockConnection.async() } returns mockAsyncCommands
            every {
                mockAsyncCommands.setex(any(), any(), any())
            } returns createRedisFutureMock("OK")

            var capturedEmailBody = ""
            val mockEngine = MockEngine { request ->
                capturedEmailBody = request.body.toByteArray().decodeToString()
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                firstName = "Jane",
                lastName = "",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isRight() shouldBe true

            // Verify email uses first name only
            capturedEmailBody shouldContain "Hello Jane"
            (capturedEmailBody.contains("Jane ")).shouldBe(false)
        }

        test("should use 'there' when both names are blank") {
            every { mockConnection.async() } returns mockAsyncCommands
            every {
                mockAsyncCommands.setex(any(), any(), any())
            } returns createRedisFutureMock("OK")

            var capturedEmailBody = ""
            val mockEngine = MockEngine { request ->
                capturedEmailBody = request.body.toByteArray().decodeToString()
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                firstName = "",
                lastName = "",
                passwordHash = "hash123",
                isActive = false
            )

            val result = emailService.sendVerificationEmail(user)

            result.isRight() shouldBe true

            // Verify email uses username when no names provided
            capturedEmailBody shouldContain "Hello testuser"
        }

        test("should generate unique tokens for multiple verification emails") {
            every { mockConnection.async() } returns mockAsyncCommands

            val capturedKeys = mutableListOf<String>()
            every {
                mockAsyncCommands.setex(capture(capturedKeys), any(), any())
            } returns createRedisFutureMock("OK")

            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""{"id":"email_456"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }

            val httpClient = HttpClient(mockEngine) {
                install(ContentNegotiation) { json(json) }
            }

            emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

            val user = User(
                id = java.util.UUID.randomUUID(),
                username = "testuser",
                email = "test@example.com",
                passwordHash = "hash123",
                isActive = false
            )

            // Send two verification emails
            emailService.sendVerificationEmail(user)
            emailService.sendVerificationEmail(user)

            // Verify tokens are different
            val token1 = capturedKeys[0].substringAfterLast(":")
            val token2 = capturedKeys[1].substringAfterLast(":")
            token1.isNotBlank() shouldBe true
            token2.isNotBlank() shouldBe true
            (token1 != token2) shouldBe true
        }
    }
})
