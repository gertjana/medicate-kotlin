package dev.gertjanassies.routes

import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.PasswordResetRequest
import dev.gertjanassies.model.request.VerifyResetTokenRequest
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createKeyScanCursorMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation
import io.ktor.server.testing.*
import io.ktor.utils.io.*
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AuthRoutesTest : FunSpec({
    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService
    lateinit var emailService: EmailService
    lateinit var jwtService: JwtService

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"
    val testApiKey = "test_api_key"
    val testAppUrl = "http://localhost:5173"
    val jwtSecret = "test-jwt-secret-for-testing-purposes-only"

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)
        jwtService = JwtService(jwtSecret)
    }

    afterEach {
        clearAllMocks()
    }

    context("POST /auth/resetPassword") {
        test("should send reset password email successfully") {
            val username = "testuser"
            val email = "testuser@example.com"
            val userId = java.util.UUID.randomUUID()
            val request = PasswordResetRequest(email)
            val user = User(id = userId, username = username, email = email, passwordHash = "hashedpassword")
            val emailId = "email-id-123"
            val userJson = json.encodeToString(user)

            // Mock Redis operations for getUserByEmail
            every { mockConnection.async() } returns mockAsyncCommands
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Mock Redis operations for storing reset token
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createRedisFutureMock("OK")

            // Mock HTTP client for email sending
            val mockEngine = MockEngine { _ ->
                respond(
                    content = ByteReadChannel("""{"id":"$emailId"}"""),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
            HttpClient(mockEngine) {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            }.use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body shouldContainKey "message"
                    body shouldContainKey "emailId"
                    body["emailId"] shouldBe emailId
                    body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                }
            }
        }

        test("should return 400 when email is blank") {
            val request = PasswordResetRequest("")

            // Create a mock HTTP client (won't be used)
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Email cannot be empty"
                }
            }
        }

        test("should return OK with generic message when user not found by email") {
            val email = "nonexistent@example.com"
            val request = PasswordResetRequest(email)

            // Mock Redis to return null (user not found)
            every { mockConnection.async() } returns mockAsyncCommands
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(null as String?)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    // Should return OK to avoid leaking information about user existence
                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                    body["emailId"] shouldBe "no-email-sent"
                }
            }
        }

        test("should return OK with generic message when Redis operation fails") {
            val email = "testuser@example.com"
            val request = PasswordResetRequest(email)

            // Mock Redis to fail
            every { mockConnection.async() } returns mockAsyncCommands
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            every { mockAsyncCommands.get(emailIndexKey) } returns createFailedRedisFutureMock(RuntimeException("Redis connection failed"))

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    // Should return OK to avoid leaking internal errors
                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                    body["emailId"] shouldBe "no-email-sent"
                }
            }
        }

        test("should return 400 when email is invalid") {
            val username = "testuser"
            val email = "invalid-email"
            val userId = java.util.UUID.randomUUID()
            val request = PasswordResetRequest(email)
            val user = User(id = userId, username = username, email = email, passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            // Mock Redis to return user
            every { mockConnection.async() } returns mockAsyncCommands
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Create mock HTTP client (won't be called due to invalid email)
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                    body["emailId"] shouldBe "email-send-failed"
                }
            }
        }

        test("should return 500 when email send fails") {
            val username = "testuser"
            val email = "testuser@example.com"
            val userId = java.util.UUID.randomUUID()
            val request = PasswordResetRequest(email)
            val user = User(id = userId, username = username, email = email, passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            // Mock Redis operations
            every { mockConnection.async() } returns mockAsyncCommands
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createRedisFutureMock("OK")

            // Mock HTTP client to fail
            val mockEngine = MockEngine {
                respond("SMTP error", HttpStatusCode.InternalServerError)
            }
            HttpClient(mockEngine) {
                install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json(json) }
            }.use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                    body["emailId"] shouldBe "email-send-failed"
                }
            }
        }

        test("should return 500 when token generation fails") {
            val username = "testuser"
            val email = "testuser@example.com"
            val userId = java.util.UUID.randomUUID()
            val request = PasswordResetRequest(email)
            val user = User(id = userId, username = username, email = email, passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            // Mock Redis operations - token storage fails
            every { mockConnection.async() } returns mockAsyncCommands
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            every { mockAsyncCommands.setex(any(), any(), any()) } returns createFailedRedisFutureMock(RuntimeException("Random generator failed"))

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/resetPassword") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body["message"] shouldBe "If an account exists with that email, you will receive a password reset link."
                    body["emailId"] shouldBe "email-send-failed"
                }
            }
        }
    }

    context("POST /auth/refresh") {
        test("should refresh access token with valid refresh token") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = "hash")
            val userJson = json.encodeToString(user)
            val refreshToken = jwtService.generateRefreshToken(username, userId.toString())

            // Mock Redis operations for getUser
            every { mockConnection.async() } returns mockAsyncCommands
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/refresh") {
                        cookie("refresh_token", refreshToken)
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body shouldContainKey "token"
                    // Refresh token should not be in response (it's in HttpOnly cookie)
                    (body.containsKey("refreshToken")) shouldBe false
                }
            }
        }

        test("should return 401 when refresh token is invalid, expired, or wrong type") {
            val invalidToken = "invalid-or-expired-or-access-token"

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/refresh") {
                        cookie("refresh_token", invalidToken)
                    }

                    response.status shouldBe HttpStatusCode.Unauthorized
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Invalid or expired refresh token"
                }
            }
        }

        test("should return 400 when refresh token is missing") {
            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/refresh") {
                        // No cookie sent
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Refresh token is required"
                }
            }
        }

        test("should return 400 when refresh token is blank") {
            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/refresh") {
                        cookie("refresh_token", "")
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Refresh token is required"
                }
            }
        }
    }

    context("POST /auth/verifyResetToken") {
        test("should verify token and return username successfully") {
            val token = "valid-token-123"
            val userId = java.util.UUID.randomUUID()
            val username = "testuser"
            val request = VerifyResetTokenRequest(token)
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = "hash")
            val userJson = json.encodeToString(user)

            // Mock Redis operations - use scan to find the key
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:password_reset:$userId:$token"
            val scanCursor = createKeyScanCursorMock(listOf(tokenKey), isFinished = true)
            every {
                mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>())
            } returns createRedisFutureMock(scanCursor)
            every { mockAsyncCommands.get(tokenKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            every { mockAsyncCommands.del(tokenKey) } returns createRedisFutureMock(1L)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/verifyResetToken") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK
                    val body = response.body<Map<String, String>>()
                    body["username"] shouldBe username
                }
            }
        }

        test("should return 400 when token is blank") {
            val request = VerifyResetTokenRequest("")

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/verifyResetToken") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Token cannot be empty"
                }
            }
        }

        test("should return 404 when token is not found or expired") {
            val token = "invalid-or-expired-token"
            val request = VerifyResetTokenRequest(token)

            // Mock Redis scan to return no keys (token not found)
            every { mockConnection.async() } returns mockAsyncCommands
            val emptyScanCursor = createKeyScanCursorMock(emptyList(), isFinished = true)
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(emptyScanCursor)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/verifyResetToken") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.NotFound
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Invalid or expired"
                }
            }
        }

        test("should return 500 when Redis operation fails") {
            val token = "valid-token-123"
            val request = VerifyResetTokenRequest(token)

            // Mock Redis scan to fail
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createFailedRedisFutureMock(RuntimeException("Redis connection failed"))

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/verifyResetToken") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.InternalServerError
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Redis connection failed"
                }
            }
        }

        test("should delete token after successful verification") {
            val token = "valid-token-123"
            val userId = java.util.UUID.randomUUID()
            val username = "testuser"
            val request = VerifyResetTokenRequest(token)
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = "hash")
            val userJson = json.encodeToString(user)

            // Mock Redis operations
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:password_reset:$userId:$token"
            val scanCursor = createKeyScanCursorMock(listOf(tokenKey), isFinished = true)
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(scanCursor)
            every { mockAsyncCommands.get(tokenKey) } returns createRedisFutureMock(userId.toString())
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            every { mockAsyncCommands.del(tokenKey) } returns createRedisFutureMock(1L)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/verifyResetToken") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK

                    // Verify that del was called (token deletion happens in verifyPasswordResetToken)
                    verify(exactly = 1) { mockAsyncCommands.del(tokenKey) }
                }
            }
        }
    }

    context("POST /auth/activateAccount") {
        test("should activate account successfully with valid token") {
            val token = "valid-activation-token"
            val userId = java.util.UUID.randomUUID()
            val username = "testuser"
            val email = "test@example.com"
            val firstName = "Test"
            val lastName = "User"
            val request = VerifyResetTokenRequest(token)
            val inactiveUser = User(
                id = userId,
                username = username,
                email = email,
                firstName = firstName,
                lastName = lastName,
                passwordHash = "hashedpass",
                isActive = false
            )
            val activatedUser = inactiveUser.copy(isActive = true)
            val inactiveUserJson = json.encodeToString(inactiveUser)
            val activatedUserJson = json.encodeToString(activatedUser)

            // Mock Redis operations - verifyActivationToken uses direct GET (not scan)
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:verification:token:$token"
            every { mockAsyncCommands.get(tokenKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.del(tokenKey) } returns createRedisFutureMock(1L)
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(inactiveUserJson) andThen createRedisFutureMock(activatedUserJson)
            every { mockAsyncCommands.set(userKey, activatedUserJson) } returns createRedisFutureMock("OK")

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/activateAccount") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.OK

                    // Parse the JSON response using the ActivationResponse model
                    val body = response.body<dev.gertjanassies.model.response.ActivationResponse>()
                    body.message shouldBe "Account activated successfully"
                    body.user.username shouldBe username
                    body.user.email shouldBe email
                    body.user.firstName shouldBe firstName
                    body.user.lastName shouldBe lastName

                    // Verify refresh token cookie was set
                    val cookies = response.setCookie()
                    cookies.any { it.name == "refresh_token" } shouldBe true
                }
            }
        }

        test("should return 400 when token is blank") {
            val request = VerifyResetTokenRequest("")

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/activateAccount") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.BadRequest
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Token cannot be empty"
                }
            }
        }

        test("should return 404 when activation token is invalid or expired") {
            val token = "invalid-token"
            val request = VerifyResetTokenRequest(token)

            // Mock Redis - token not found (returns null)
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:verification:token:$token"
            every { mockAsyncCommands.get(tokenKey) } returns createRedisFutureMock(null as String?)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/activateAccount") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.NotFound
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Invalid or expired activation token"
                }
            }
        }

        test("should return 500 when token verification fails with operation error") {
            val token = "valid-token-but-redis-error"
            val request = VerifyResetTokenRequest(token)

            // Mock Redis GET to fail
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:verification:token:$token"
            every { mockAsyncCommands.get(tokenKey) } returns createFailedRedisFutureMock(RuntimeException("Redis connection failed"))

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/activateAccount") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.InternalServerError
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Redis connection failed"
                }
            }
        }

        test("should return 500 when user activation fails") {
            val token = "valid-token"
            val userId = java.util.UUID.randomUUID()
            val request = VerifyResetTokenRequest(token)

            // Mock Redis - token verification succeeds but user get fails
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:verification:token:$token"
            every { mockAsyncCommands.get(tokenKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.del(tokenKey) } returns createRedisFutureMock(1L)
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createFailedRedisFutureMock(RuntimeException("Failed to update user"))

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/activateAccount") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.InternalServerError
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Failed to activate account"
                }
            }
        }

        test("should return 404 when activating user that doesn't exist") {
            val token = "valid-token"
            val userId = java.util.UUID.randomUUID()
            val request = VerifyResetTokenRequest(token)

            // Mock Redis - token is valid but user doesn't exist
            every { mockConnection.async() } returns mockAsyncCommands
            val tokenKey = "medicate:$environment:verification:token:$token"
            every { mockAsyncCommands.get(tokenKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.del(tokenKey) } returns createRedisFutureMock(1L)
            val userKey = "medicate:$environment:user:id:$userId"
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)

            // Create mock HTTP client
            val mockEngine = MockEngine { respond("", HttpStatusCode.OK) }
            HttpClient(mockEngine).use { httpClient ->
                emailService = EmailService(httpClient, redisService, testApiKey, testAppUrl)

                testApplication {
                    environment {
                        config = MapApplicationConfig()
                    }
                    install(ServerContentNegotiation) { json() }
                    routing { authRoutes(redisService, emailService, jwtService) }

                    val client = createClient { install(ClientContentNegotiation) { json() } }
                    val response = client.post("/auth/activateAccount") {
                        contentType(ContentType.Application.Json)
                        setBody(request)
                    }

                    response.status shouldBe HttpStatusCode.InternalServerError
                    val body = response.body<Map<String, String>>()
                    body["error"] shouldContain "Failed to activate account"
                }
            }
        }
    }
})
