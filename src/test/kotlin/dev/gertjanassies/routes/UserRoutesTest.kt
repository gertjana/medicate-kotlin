package dev.gertjanassies.routes

import dev.gertjanassies.model.User
import dev.gertjanassies.model.request.UpdateProfileRequest
import dev.gertjanassies.model.request.UserRequest
import dev.gertjanassies.model.response.AuthResponse
import dev.gertjanassies.model.response.UserResponse
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UserRoutesTest : FunSpec({
    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService
    lateinit var jwtService: JwtService
    lateinit var emailService: EmailService
    lateinit var mockHttpClient: HttpClient

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)

        // Create real JwtService with test configuration
        jwtService = JwtService(
            secret = TestJwtConfig.SECRET,
            issuer = TestJwtConfig.ISSUER,
            audience = TestJwtConfig.AUDIENCE,
            accessTokenExpirationMs = 3600000,
            refreshTokenExpirationMs = 86400000
        )

        // Create mock HTTP client for EmailService
        mockHttpClient = HttpClient(MockEngine) {
            engine {
                addHandler { request ->
                    when (request.url.toString()) {
                        "https://api.resend.com/emails" -> {
                            respond(
                                content = """{"id": "email-id-123"}""",
                                status = HttpStatusCode.OK,
                                headers = headersOf(HttpHeaders.ContentType, "application/json")
                            )
                        }
                        else -> error("Unhandled ${request.url}")
                    }
                }
            }
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json()
            }
        }

        // Create real EmailService with mock HTTP client
        emailService = EmailService(
            httpClient = mockHttpClient,
            redisService = redisService,
            apiKey = "test-api-key",
            appUrl = "http://localhost:8080",
            fromEmail = "test@example.com"
        )
    }

    afterEach {
        clearAllMocks()
        mockHttpClient.close()
    }

    context("POST /user/register") {
        test("should register a new user successfully and send activation email") {
            val username = "testuser"
            val email = "testuser@example.com"
            val password = "password123"
            val request = UserRequest(username, email, password)

            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            // Mock email not in use check
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(null as String?)

            // Mock username index check (no existing users with this username)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)

            // Mock transaction
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(match { it.contains(":user:id:") }, any()) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(usernameIndexKey, match { it.matches(Regex("[0-9a-f-]{36}")) }) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(emailIndexKey, match { it.matches(Regex("[0-9a-f-]{36}")) }) } returns createRedisFutureMock("OK")
            val mockTransactionResult = mockk<io.lettuce.core.TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            // Mock verification token storage (setex sets value with TTL in seconds)
            every { mockAsyncCommands.setex(match { it.contains(":verification:token:") }, 86400, any()) } returns createRedisFutureMock("OK")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Created
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Registration successful! Please check your email to verify your account."
                body["email"] shouldBe email
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "test@example.com", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "test@example.com", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when password is too short") {
            val request = UserRequest("testuser", "test@example.com", "12345")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 on registration error") {
            val username = "existinguser"
            val email = "existing@example.com"
            val password = "password123"
            val request = UserRequest(username, email, password)

            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"
            val existingUserId = java.util.UUID.randomUUID().toString()

            // Mock email already in use
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(existingUserId)

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    context("POST /user/login") {
        test("should login user successfully and return JWT token") {
            val username = "testuser"
            val password = "password123"
            val userId = java.util.UUID.randomUUID()
            val request = UserRequest(username, "", password)

            // Create user with BCrypt-hashed password
            val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = passwordHash, isActive = true)

            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val userJson = json.encodeToString(user)

            // Mock username index lookup
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())

            // Mock getUserById
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<AuthResponse>()
                body.user.username shouldBe username
                body.token shouldNotBe ""
                // Refresh token should not be in response body (empty string)
                body.refreshToken shouldBe ""

                // Verify refresh token cookie was set
                val cookies = response.setCookie()
                val refreshCookie = cookies.find { it.name == "refresh_token" }
                refreshCookie shouldNotBe null
                refreshCookie?.httpOnly shouldBe true
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "", "password123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 401 on login error") {
            val username = "nonexistent"
            val password = "wrongpassword"
            val request = UserRequest(username, "", password)

            val usernameIndexKey = "medicate:$environment:user:username:$username"

            // Mock username not found
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    context("PUT /user/password") {
        test("should update password successfully") {
            val username = "testuser"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)
            val userId = java.util.UUID.randomUUID()
            val oldPasswordHash = org.mindrot.jbcrypt.BCrypt.hashpw("oldpassword", org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = oldPasswordHash, isActive = true)

            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val userJson = json.encodeToString(user)

            // Mock getUser (username index + getUserById)
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Mock password update
            every { mockAsyncCommands.set(userKey, match { it.contains(username) && it.contains("passwordHash") }) } returns createRedisFutureMock("OK")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Password updated successfully"
            }
        }

        test("should return 400 when username is blank") {
            val request = UserRequest("", "", "newpassword123")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when password is blank") {
            val request = UserRequest("testuser", "", "")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when password is too short") {
            val request = UserRequest("testuser", "", "short")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return OK with generic message when user not found") {
            val username = "nonexistent"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)

            val usernameIndexKey = "medicate:$environment:user:username:$username"

            // Mock username not found
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // Should return OK to avoid revealing user existence
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Password updated successfully"
            }
        }

        test("should return OK with generic message on update error") {
            val username = "testuser"
            val newPassword = "newpassword123"
            val request = UserRequest(username, "", newPassword)
            val userId = java.util.UUID.randomUUID()
            val oldPasswordHash = org.mindrot.jbcrypt.BCrypt.hashpw("oldpassword", org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = oldPasswordHash, isActive = true)

            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val userJson = json.encodeToString(user)

            // Mock getUser succeeds
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Mock password update fails (connection error or timeout)
            every { mockAsyncCommands.set(userKey, any()) } throws RuntimeException("Redis connection failed")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }

                // Should return OK to avoid revealing internal errors
                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "Password updated successfully"
            }
        }
    }

    context("Authentication Requirements") {
        test("POST /user/register should NOT require authentication (public endpoint)") {
            val request = UserRequest("testuser", "test@example.com", "password123")

            val usernameIndexKey = "medicate:$environment:user:username:testuser"
            val emailIndexKey = "medicate:$environment:user:email:test@example.com"

            // Mock email not in use check
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(null as String?)

            // Mock username index check
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)

            // Mock transaction
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(match { it.contains(":user:id:") }, any()) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(usernameIndexKey, any()) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(emailIndexKey, any()) } returns createRedisFutureMock("OK")
            val mockTransactionResult = mockk<io.lettuce.core.TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            // Mock verification token storage (setex sets value with TTL in seconds)
            every { mockAsyncCommands.setex(match { it.contains(":verification:token:") }, 86400, any()) } returns createRedisFutureMock("OK")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                // Should succeed WITHOUT authentication header
                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    // No Authorization header!
                }

                // Should succeed (201 Created) without authentication
                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("POST /user/login should NOT require authentication (public endpoint)") {
            val request = UserRequest("testuser", "", "password123")
            val userId = java.util.UUID.randomUUID()
            val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw("password123", org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = "testuser", email = "test@example.com", passwordHash = passwordHash, isActive = true)

            val usernameIndexKey = "medicate:$environment:user:username:testuser"
            val userKey = "medicate:$environment:user:id:$userId"
            val userJson = json.encodeToString(user)

            // Mock username index lookup
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())

            // Mock getUserById
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                // Should succeed WITHOUT authentication header
                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    // No Authorization header!
                }

                // Should succeed (200 OK) without authentication
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("PUT /user/password should work without authentication (currently public for password reset flow)") {
            val request = UserRequest("testuser", "", "newpassword123")
            val userId = java.util.UUID.randomUUID()
            val oldPasswordHash = org.mindrot.jbcrypt.BCrypt.hashpw("oldpassword", org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = "testuser", email = "test@example.com", passwordHash = oldPasswordHash, isActive = true)

            val usernameIndexKey = "medicate:$environment:user:username:testuser"
            val userKey = "medicate:$environment:user:id:$userId"
            val userJson = json.encodeToString(user)

            // Mock getUser
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Mock password update
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                install(ContentNegotiation) { json() }
                routing { userRoutes(redisService, jwtService, emailService) }

                val client = createClient { install(ClientContentNegotiation) { json() } }
                // Can work without authentication (for password reset)
                val response = client.put("/user/password") {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                    // No Authorization header
                }

                // Should succeed without authentication (currently public)
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }

    context("GET /user/profile") {
        test("should retrieve user profile successfully with valid JWT") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val user = User(
                id = userId,
                username = username,
                email = "test@example.com",
                firstName = "Test",
                lastName = "User",
                passwordHash = "hashedpassword"
            )

            val userKey = "medicate:$environment:user:id:$userId"
            val userJson = json.encodeToString(user)

            // Mock getUserById
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UserResponse>()
                body.username shouldBe username
                body.email shouldBe "test@example.com"
                body.firstName shouldBe "Test"
                body.lastName shouldBe "User"
            }
        }

        test("should return InternalServerError with generic message when user not found") {
            val username = "nonexistent"
            val userId = java.util.UUID.randomUUID()

            val userKey = "medicate:$environment:user:id:$userId"

            // Mock getUserById returns null (user not found)
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                // Return generic error message without revealing user existence
                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to retrieve profile"
            }
        }

        test("should return 500 on Redis error") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()

            val userKey = "medicate:$environment:user:id:$userId"

            // Mock Redis error
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } throws RuntimeException("Redis connection failed")

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.get("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }

    context("PUT /user/profile") {
        test("should update user profile successfully with valid JWT") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val updateRequest = UpdateProfileRequest(
                email = "updated@example.com",
                firstName = "Updated",
                lastName = "Name"
            )
            val existingUser = User(
                id = userId,
                username = username,
                email = "old@example.com",
                firstName = "Old",
                lastName = "Name",
                passwordHash = "hashedpassword"
            )

            val userKey = "medicate:$environment:user:id:$userId"
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val oldEmailIndexKey = "medicate:$environment:user:email:old@example.com"
            val newEmailIndexKey = "medicate:$environment:user:email:updated@example.com"
            val userJson = json.encodeToString(existingUser)

            // Mock getUserById for initial lookup (route calls this to get username from userId)
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Mock username index lookup for updateProfile's getUser call
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())

            // Mock email check (new email not in use)
            every { mockAsyncCommands.get(newEmailIndexKey) } returns createRedisFutureMock(null as String?)

            // Mock transaction for profile update
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(userKey, match { it.contains("Updated") && it.contains("Name") }) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.del(oldEmailIndexKey) } returns createRedisFutureMock(1L)
            every { mockAsyncCommands.set(newEmailIndexKey, userId.toString()) } returns createRedisFutureMock("OK")
            val mockTransactionResult = mockk<io.lettuce.core.TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UserResponse>()
                body.username shouldBe username
                body.email shouldBe updateRequest.email
                body.firstName shouldBe updateRequest.firstName
                body.lastName shouldBe updateRequest.lastName
            }
        }

        test("should return 400 when email is blank") {
            val username = "testuser"
            val updateRequest = UpdateProfileRequest(
                email = "",
                firstName = "Test",
                lastName = "User"
            )

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username)
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when firstName is blank") {
            val username = "testuser"
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "",
                lastName = "User"
            )

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username)
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return 400 when lastName is blank") {
            val username = "testuser"
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "Test",
                lastName = ""
            )

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username)
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                response.status shouldBe HttpStatusCode.BadRequest
            }
        }

        test("should return InternalServerError with generic message when user not found") {
            val username = "nonexistent"
            val userId = java.util.UUID.randomUUID()
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            )

            val userKey = "medicate:$environment:user:id:$userId"

            // Mock getUserById to return null (user doesn't exist)
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                // Return generic error message
                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to update profile"
            }
        }

        test("should return BadRequest with generic message on Redis error") {
            val username = "testuser"
            val userId = java.util.UUID.randomUUID()
            val updateRequest = UpdateProfileRequest(
                email = "test@example.com",
                firstName = "Test",
                lastName = "User"
            )
            val existingUser = User(
                id = userId,
                username = username,
                email = "old@example.com",
                firstName = "Old",
                lastName = "Name",
                passwordHash = "hashedpassword"
            )

            val userKey = "medicate:$environment:user:id:$userId"
            val newEmailIndexKey = "medicate:$environment:user:email:test@example.com"
            val userJson = json.encodeToString(existingUser)

            // Mock getUserById to succeed
            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            // Mock email check (new email not in use)
            every { mockAsyncCommands.get(newEmailIndexKey) } returns createRedisFutureMock(null as String?)

            // Mock transaction to fail
            every { mockAsyncCommands.multi() } throws RuntimeException("Redis connection failed")

            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(redisService)
                    }
                }

                val token = TestJwtConfig.generateToken(username, userId.toString())
                val client = createClient { install(ClientContentNegotiation) { json() } }

                val response = client.put("/user/profile") {
                    header(HttpHeaders.Authorization, "Bearer $token")
                    contentType(ContentType.Application.Json)
                    setBody(updateRequest)
                }

                // Return generic error message
                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to update profile. Email may already be in use."
            }
        }
    }
})
