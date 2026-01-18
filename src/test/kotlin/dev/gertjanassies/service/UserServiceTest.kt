package dev.gertjanassies.service

import dev.gertjanassies.model.User
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.api.async.RedisAsyncCommands
import io.mockk.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Test suite for User-related operations in RedisService.
 *
 * Tests cover:
 * - registerUser: user registration with duplicate check
 * - loginUser: user authentication and retrieval
 */
class UserServiceTest : FunSpec({

    lateinit var mockConnection: StatefulRedisConnection<String, String>
    lateinit var mockAsyncCommands: RedisAsyncCommands<String, String>
    lateinit var redisService: RedisService

    val json = Json { ignoreUnknownKeys = true }
    val environment = "test"

    beforeEach {
        mockConnection = mockk()
        mockAsyncCommands = mockk()
        redisService = RedisService(environment = environment, connection = mockConnection)
    }

    afterEach {
        clearAllMocks()
    }

    context("registerUser") {
        test("should successfully register a new user with hashed password") {
            val username = "newuser"
            val email = "newuser@example.com"
            val password = "password123"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            val result = redisService.registerUser(username, email, password)

            result.isRight() shouldBe true
            val user = result.getOrNull()!!
            user.username shouldBe username
            user.email shouldBe email
            user.passwordHash.isNotEmpty() shouldBe true

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }

        test("should return error when username already exists") {
            val username = "existinguser"
            val email = "existing@example.com"
            val password = "password123"
            val userKey = "$environment:user:$username"
            val existingUser = User(username = username, email = email, passwordHash = "hashedpassword")
            val existingUserJson = json.encodeToString(existingUser)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(existingUserJson)

            val result = redisService.registerUser(username, email, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Username already exists"

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 0) { mockAsyncCommands.set(any(), any()) }
        }

        test("should return error when registration fails") {
            val username = "testuser"
            val email = "test@example.com"
            val password = "password123"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)
            every { mockAsyncCommands.set(userKey, any()) } returns
                createFailedRedisFutureMock(RuntimeException("Redis connection error"))

            val result = redisService.registerUser(username, email, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }

        test("should proceed with registration when checking existing user fails") {
            val username = "testuser"
            val email = "test@example.com"
            val password = "password123"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            // When get() fails, getOrNull() returns null, so it proceeds to create user
            every { mockAsyncCommands.get(userKey) } returns
                createFailedRedisFutureMock(RuntimeException("Connection error"))
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            val result = redisService.registerUser(username, email, password)

            // Due to getOrNull(), the error is swallowed and registration proceeds
            result.isRight() shouldBe true
            val user = result.getOrNull()!!
            user.username shouldBe username

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }
    }

    context("loginUser") {
        test("should successfully login existing user with correct password") {
            val username = "testuser"
            val password = "password123"
            val userKey = "$environment:user:$username"
            // Hash the password using BCrypt to simulate stored user
            val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(username = username, email = "test@example.com", passwordHash = passwordHash)
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            val result = redisService.loginUser(username, password)

            result.isRight() shouldBe true
            val loggedInUser = result.getOrNull()!!
            loggedInUser.username shouldBe username

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return error when password is incorrect") {
            val username = "testuser"
            val correctPassword = "password123"
            val wrongPassword = "wrongpassword"
            val userKey = "$environment:user:$username"
            val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(correctPassword, org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(username = username, email = "test@example.com", passwordHash = passwordHash)
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            val result = redisService.loginUser(username, wrongPassword)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Invalid credentials"

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return NotFound when user does not exist") {
            val username = "nonexistent"
            val password = "password123"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.loginUser(username, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "User not found"

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return error when Redis operation fails") {
            val username = "testuser"
            val password = "password123"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns
                createFailedRedisFutureMock(RuntimeException("Connection error"))

            val result = redisService.loginUser(username, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return error when user JSON is invalid") {
            val username = "testuser"
            val password = "password123"
            val userKey = "$environment:user:$username"
            // Use completely malformed JSON that will fail to parse
            val invalidJson = """not valid json at all"""

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(invalidJson)

            val result = redisService.loginUser(username, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.SerializationError>()

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }
    }

    context("updateProfile") {
        test("should successfully update profile when email is not in use by another user") {
            val username = "testuser"
            val email = "newemail@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userKey = "$environment:user:$username"
            val user = User(username = username, email = "old@example.com", firstName = "OldFirst", lastName = "OldLast", passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock scan to return only the current user
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(userKey)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)
            // Mock get for email check
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            // Mock set for update
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isRight() shouldBe true
            val updatedUser = result.getOrNull()!!
            updatedUser.username shouldBe username
            updatedUser.email shouldBe email
            updatedUser.firstName shouldBe firstName
            updatedUser.lastName shouldBe lastName

            verify(atLeast = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }

        test("should return error when email is already in use by another user") {
            val username = "testuser"
            val email = "existing@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userKey = "$environment:user:$username"
            val otherUserKey = "$environment:user:otheruser"
            val user = User(username = username, email = "old@example.com", passwordHash = "hashedpassword")
            val otherUser = User(username = "otheruser", email = email, passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)
            val otherUserJson = json.encodeToString(otherUser)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock scan to return both users
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(userKey, otherUserKey)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)
            // Mock get for email check
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            every { mockAsyncCommands.get(otherUserKey) } returns createRedisFutureMock(otherUserJson)

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Email is already in use by another user"

            verify(atLeast = 1) { mockAsyncCommands.get(any()) }
            verify(exactly = 0) { mockAsyncCommands.set(any(), any()) }
        }

        test("should allow updating profile with same email (no change)") {
            val username = "testuser"
            val email = "same@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userKey = "$environment:user:$username"
            val user = User(username = username, email = email, firstName = "OldFirst", lastName = "OldLast", passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock scan to return only the current user
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(userKey)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)
            // Mock get for email check and user retrieval
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            // Mock set for update
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isRight() shouldBe true
            val updatedUser = result.getOrNull()!!
            updatedUser.username shouldBe username
            updatedUser.email shouldBe email
            updatedUser.firstName shouldBe firstName
            updatedUser.lastName shouldBe lastName

            verify(atLeast = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }

        test("should return NotFound when user does not exist") {
            val username = "nonexistent"
            val email = "new@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock scan to return empty
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns emptyList()
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)
            // Mock get to return null (user not found)
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "User not found"

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 0) { mockAsyncCommands.set(any(), any()) }
        }

        test("should be case-insensitive when checking email uniqueness") {
            val username = "testuser"
            val email = "EXISTING@EXAMPLE.COM"  // uppercase
            val firstName = "John"
            val lastName = "Doe"
            val userKey = "$environment:user:$username"
            val otherUserKey = "$environment:user:otheruser"
            val user = User(username = username, email = "old@example.com", passwordHash = "hashedpassword")
            val otherUser = User(username = "otheruser", email = "existing@example.com", passwordHash = "hashedpassword")  // lowercase
            val userJson = json.encodeToString(user)
            val otherUserJson = json.encodeToString(otherUser)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock scan to return both users
            val mockScanCursor = mockk<io.lettuce.core.KeyScanCursor<String>>()
            every { mockScanCursor.keys } returns listOf(userKey, otherUserKey)
            every { mockScanCursor.isFinished } returns true
            every { mockAsyncCommands.scan(any<io.lettuce.core.ScanArgs>()) } returns createRedisFutureMock(mockScanCursor)
            // Mock get for email check
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            every { mockAsyncCommands.get(otherUserKey) } returns createRedisFutureMock(otherUserJson)

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Email is already in use by another user"

            verify(atLeast = 1) { mockAsyncCommands.get(any()) }
            verify(exactly = 0) { mockAsyncCommands.set(any(), any()) }
        }
    }
})
