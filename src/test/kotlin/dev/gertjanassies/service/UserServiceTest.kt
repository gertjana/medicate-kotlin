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
        test("should successfully register a new user") {
            val username = "newuser"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            val result = redisService.registerUser(username)

            result.isRight() shouldBe true
            val user = result.getOrNull()!!
            user.username shouldBe username

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }

        test("should return error when username already exists") {
            val username = "existinguser"
            val userKey = "$environment:user:$username"
            val existingUser = User(username = username)
            val existingUserJson = json.encodeToString(existingUser)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(existingUserJson)

            val result = redisService.registerUser(username)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()
            result.leftOrNull()?.message shouldBe "Username already exists"

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 0) { mockAsyncCommands.set(any(), any()) }
        }

        test("should return error when registration fails") {
            val username = "testuser"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)
            every { mockAsyncCommands.set(userKey, any()) } returns
                createFailedRedisFutureMock(RuntimeException("Redis connection error"))

            val result = redisService.registerUser(username)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }

        test("should proceed with registration when checking existing user fails") {
            val username = "testuser"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            // When get() fails, getOrNull() returns null, so it proceeds to create user
            every { mockAsyncCommands.get(userKey) } returns
                createFailedRedisFutureMock(RuntimeException("Connection error"))
            every { mockAsyncCommands.set(userKey, any()) } returns createRedisFutureMock("OK")

            val result = redisService.registerUser(username)

            // Due to getOrNull(), the error is swallowed and registration proceeds
            result.isRight() shouldBe true
            val user = result.getOrNull()!!
            user.username shouldBe username

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
            verify(exactly = 1) { mockAsyncCommands.set(userKey, any()) }
        }
    }

    context("loginUser") {
        test("should successfully login existing user") {
            val username = "testuser"
            val userKey = "$environment:user:$username"
            val user = User(username = username)
            val userJson = json.encodeToString(user)

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(userJson)

            val result = redisService.loginUser(username)

            result.isRight() shouldBe true
            val loggedInUser = result.getOrNull()!!
            loggedInUser.username shouldBe username

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return NotFound when user does not exist") {
            val username = "nonexistent"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(null as String?)

            val result = redisService.loginUser(username)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.NotFound>()
            result.leftOrNull()?.message shouldBe "User not found"

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return error when Redis operation fails") {
            val username = "testuser"
            val userKey = "$environment:user:$username"

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns
                createFailedRedisFutureMock(RuntimeException("Connection error"))

            val result = redisService.loginUser(username)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.OperationError>()

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }

        test("should return error when user JSON is invalid") {
            val username = "testuser"
            val userKey = "$environment:user:$username"
            val invalidJson = """{"invalid": "json"}"""

            every { mockConnection.async() } returns mockAsyncCommands
            every { mockAsyncCommands.get(userKey) } returns createRedisFutureMock(invalidJson)

            val result = redisService.loginUser(username)

            result.isLeft() shouldBe true
            result.leftOrNull().shouldBeInstanceOf<RedisError.SerializationError>()

            verify(exactly = 1) { mockAsyncCommands.get(userKey) }
        }
    }
})
