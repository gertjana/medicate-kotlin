package dev.gertjanassies.service

import dev.gertjanassies.model.User
import dev.gertjanassies.util.createFailedRedisFutureMock
import dev.gertjanassies.util.createRedisFutureMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.lettuce.core.TransactionResult
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
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock checks for existing username and email - both return null (not found)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(null as String?)
            // Mock transaction
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")
            val mockTransactionResult = mockk<TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            val result = redisService.registerUser(username, email, password)

            result.isRight() shouldBe true
            val user = result.getOrNull()!!
            user.username shouldBe username
            user.email shouldBe email
            user.passwordHash.isNotEmpty() shouldBe true
            user.id.toString().isNotEmpty() shouldBe true

            // Verify username index check
            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            // Verify email index check
            verify(exactly = 1) { mockAsyncCommands.get(emailIndexKey) }
            // Verify transaction was used
            verify(exactly = 1) { mockAsyncCommands.multi() }
            verify(exactly = 1) { mockAsyncCommands.exec() }
        }

        test("should return error when username already exists") {
            val username = "existinguser"
            val email = "existing@example.com"
            val password = "password123"
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val existingUserId = java.util.UUID.randomUUID().toString()

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock username index returns existing userId
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(existingUserId)

            val result = redisService.registerUser(username, email, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Username already exists"

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
        }

        test("should return error when registration fails") {
            val username = "testuser"
            val email = "test@example.com"
            val password = "password123"
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            every { mockConnection.async() } returns mockAsyncCommands
            // Username and email don't exist
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(null as String?)
            // Transaction fails
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.exec() } returns createFailedRedisFutureMock(RuntimeException("Redis connection error"))

            val result = redisService.registerUser(username, email, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
        }

        test("should proceed with registration when checking existing user fails") {
            val username = "testuser"
            val email = "test@example.com"
            val password = "password123"
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            every { mockConnection.async() } returns mockAsyncCommands
            // When username check fails, getOrNull() returns null, so it proceeds
            every { mockAsyncCommands.get(usernameIndexKey) } returns
                createFailedRedisFutureMock(RuntimeException("Connection error"))
            // Email check succeeds
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(null as String?)
            // Transaction succeeds
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")
            val mockTransactionResult = mockk<TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            val result = redisService.registerUser(username, email, password)

            // Due to getOrNull(), the username check error is swallowed and registration proceeds
            result.isRight() shouldBe true
            val user = result.getOrNull()!!
            user.username shouldBe username
        }
    }

    context("loginUser") {
        test("should successfully login existing user with correct password") {
            val username = "testuser"
            val password = "password123"
            val userId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"

            // Hash the password using BCrypt to simulate stored user
            val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(password, org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = passwordHash)
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock username index lookup
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            // Mock user data lookup
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            val result = redisService.loginUser(username, password)

            result.isRight() shouldBe true
            val loggedInUser = result.getOrNull()!!
            loggedInUser.username shouldBe username
            loggedInUser.id shouldBe userId

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return error when password is incorrect") {
            val username = "testuser"
            val correctPassword = "password123"
            val wrongPassword = "wrongpassword"
            val userId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"

            val passwordHash = org.mindrot.jbcrypt.BCrypt.hashpw(correctPassword, org.mindrot.jbcrypt.BCrypt.gensalt())
            val user = User(id = userId, username = username, email = "test@example.com", passwordHash = passwordHash)
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock username index lookup
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            // Mock user data lookup
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            val result = redisService.loginUser(username, wrongPassword)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Invalid credentials"

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return NotFound when user does not exist") {
            val username = "nonexistent"
            val password = "password123"
            val usernameIndexKey = "medicate:$environment:user:username:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            // Username index returns null (user doesn't exist)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.loginUser(username, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "User not found"

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
        }

        test("should return error when Redis operation fails") {
            val username = "testuser"
            val password = "password123"
            val usernameIndexKey = "medicate:$environment:user:username:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            // Redis operation fails
            every { mockAsyncCommands.get(usernameIndexKey) } returns
                createFailedRedisFutureMock(RuntimeException("Connection error"))

            val result = redisService.loginUser(username, password)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
        }

        test("should return error when user JSON is invalid") {
            val username = "testuser"
            val password = "password123"
            val userId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock username index lookup
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            // Mock invalid JSON in user data
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock("invalid json")

            val result = redisService.loginUser(username, password)

            result.isLeft() shouldBe true
            // getUserById returns SerializationError for invalid JSON
            result.leftOrNull().shouldBeInstanceOf<RedisError.SerializationError>()
        }
    }

    context("updateProfile") {
        test("should successfully update profile when email is not in use by another user") {
            val username = "testuser"
            val email = "newemail@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val newEmailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            val user = User(id = userId, username = username, email = "old@example.com", firstName = "OldFirst", lastName = "OldLast", passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock getUser (username index → user data)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            // Mock email uniqueness check - new email not in use
            every { mockAsyncCommands.get(newEmailIndexKey) } returns createRedisFutureMock(null as String?)
            // Mock transaction
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.del(any()) } returns createRedisFutureMock(1L)
            val mockTransactionResult = mockk<TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isRight() shouldBe true
            val updatedUser = result.getOrNull()!!
            updatedUser.username shouldBe username
            updatedUser.email shouldBe email
            updatedUser.firstName shouldBe firstName
            updatedUser.lastName shouldBe lastName

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.get(newEmailIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.multi() }
            verify(exactly = 1) { mockAsyncCommands.exec() }
        }

        test("should return error when email is already in use by another user") {
            val username = "testuser"
            val email = "existing@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userId = java.util.UUID.randomUUID()
            val otherUserId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            val user = User(id = userId, username = username, email = "old@example.com", passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock getUser (username index → user data)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            // Mock email uniqueness check - email already used by different user
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(otherUserId.toString())

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Email is already in use by another user"

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.get(emailIndexKey) }
            verify(exactly = 0) { mockAsyncCommands.multi() }
            verify(exactly = 0) { mockAsyncCommands.exec() }
        }

        test("should allow updating profile with same email (no change)") {
            val username = "testuser"
            val email = "same@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val userId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val emailIndexKey = "medicate:$environment:user:email:${email.lowercase()}"

            val user = User(id = userId, username = username, email = email, firstName = "OldFirst", lastName = "OldLast", passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock getUser (username index → user data)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            // Mock email uniqueness check - same email returns same userId (allowed)
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(userId.toString())
            // Mock transaction
            every { mockAsyncCommands.multi() } returns createRedisFutureMock("OK")
            every { mockAsyncCommands.set(any(), any()) } returns createRedisFutureMock("OK")
            val mockTransactionResult = mockk<TransactionResult>()
            every { mockTransactionResult.wasDiscarded() } returns false
            every { mockAsyncCommands.exec() } returns createRedisFutureMock(mockTransactionResult)

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isRight() shouldBe true
            val updatedUser = result.getOrNull()!!
            updatedUser.username shouldBe username
            updatedUser.email shouldBe email
            updatedUser.firstName shouldBe firstName
            updatedUser.lastName shouldBe lastName

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.get(emailIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.multi() }
            verify(exactly = 1) { mockAsyncCommands.exec() }
        }

        test("should return NotFound when user does not exist") {
            val username = "nonexistent"
            val email = "new@example.com"
            val firstName = "John"
            val lastName = "Doe"
            val usernameIndexKey = "medicate:$environment:user:username:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock username index returns null (user not found)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "User not found"

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 0) { mockAsyncCommands.multi() }
            verify(exactly = 0) { mockAsyncCommands.exec() }
        }

        test("should be case-insensitive when checking email uniqueness") {
            val username = "testuser"
            val email = "EXISTING@EXAMPLE.COM"  // uppercase
            val firstName = "John"
            val lastName = "Doe"
            val userId = java.util.UUID.randomUUID()
            val otherUserId = java.util.UUID.randomUUID()
            val usernameIndexKey = "medicate:$environment:user:username:$username"
            val userKey = "medicate:$environment:user:id:$userId"
            val emailIndexKey = "medicate:$environment:user:email:existing@example.com"  // lowercase in index

            val user = User(id = userId, username = username, email = "old@example.com", passwordHash = "hashedpassword")
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            // Mock getUser (username index → user data)
            every { mockAsyncCommands.get(usernameIndexKey) } returns createRedisFutureMock(userId.toString())
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)
            // Mock email uniqueness check - email (lowercase) is used by another user
            every { mockAsyncCommands.get(emailIndexKey) } returns createRedisFutureMock(otherUserId.toString())

            val result = redisService.updateProfile(username, email, firstName, lastName)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Email is already in use by another user"

            verify(exactly = 1) { mockAsyncCommands.get(usernameIndexKey) }
            verify(exactly = 1) { mockAsyncCommands.get(emailIndexKey) }
            verify(exactly = 0) { mockAsyncCommands.multi() }
            verify(exactly = 0) { mockAsyncCommands.exec() }
        }
    }
})
