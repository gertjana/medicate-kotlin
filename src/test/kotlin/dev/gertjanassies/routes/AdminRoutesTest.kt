package dev.gertjanassies.routes

import arrow.core.left
import arrow.core.right
import dev.gertjanassies.model.User
import dev.gertjanassies.model.response.AdminUserResponse
import dev.gertjanassies.model.response.AdminUsersListResponse
import dev.gertjanassies.model.response.UserResponse
import dev.gertjanassies.service.RedisError
import dev.gertjanassies.service.StorageService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.serialization.kotlinx.json.*
import io.mockk.*
import java.util.*

class AdminRoutesTest : FunSpec({
    lateinit var mockStorageService: StorageService
    val adminUsername = "adminuser"
    val adminUserId = UUID.randomUUID()
    val adminToken = TestJwtConfig.generateToken(adminUsername, adminUserId.toString(), isAdmin = true)
    
    val regularUsername = "regularuser"
    val regularUserId = UUID.randomUUID()
    val regularToken = TestJwtConfig.generateToken(regularUsername, regularUserId.toString(), isAdmin = false)

    beforeEach {
        mockStorageService = mockk()
    }

    afterEach {
        clearAllMocks()
    }

    context("Authorization checks") {
        test("should return 401 when no token provided") {
            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/admin/users")

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("should return 403 when non-admin user attempts to access admin endpoint") {
            coEvery { mockStorageService.isUserAdmin(regularUserId.toString()) } returns false.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/admin/users") {
                    header("Authorization", "Bearer $regularToken")
                }

                response.status shouldBe HttpStatusCode.Forbidden
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Admin privileges required"

                coVerify { mockStorageService.isUserAdmin(regularUserId.toString()) }
            }
        }

        test("should return 500 when admin status check fails") {
            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/admin/users") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to verify admin privileges"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
            }
        }
    }

    context("GET /admin/users") {
        test("should return list of users for admin") {
            val user1 = User(
                id = UUID.randomUUID(),
                username = "user1",
                email = "user1@example.com",
                passwordHash = "hash1",
                isActive = true
            )
            val user2 = User(
                id = UUID.randomUUID(),
                username = "user2",
                email = "user2@example.com",
                passwordHash = "hash2",
                isActive = false
            )
            val users = listOf(user1, user2)

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.getAllUsers() } returns users.right()
            coEvery { mockStorageService.getAllAdmins() } returns setOf(adminUserId.toString()).right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/admin/users") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<AdminUsersListResponse>()
                body.users.size shouldBe 2

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.getAllUsers() }
                coVerify { mockStorageService.getAllAdmins() }
            }
        }

        test("should return 500 when getAllUsers fails") {
            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.getAllUsers() } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.get("/admin/users") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to retrieve users"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.getAllUsers() }
            }
        }
    }

    context("PUT /admin/users/{userId}/activate") {
        test("should activate user successfully") {
            val targetUserId = UUID.randomUUID().toString()
            val targetUser = User(
                id = UUID.fromString(targetUserId),
                username = "targetuser",
                email = "target@example.com",
                passwordHash = "hash",
                isActive = true
            )

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.activateUser(targetUserId) } returns targetUser.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/admin/users/$targetUserId/activate") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<UserResponse>()
                body.username shouldBe "targetuser"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.activateUser(targetUserId) }
            }
        }

        test("should return 500 when activate fails") {
            val targetUserId = UUID.randomUUID().toString()

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.activateUser(targetUserId) } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/admin/users/$targetUserId/activate") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to activate user"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.activateUser(targetUserId) }
            }
        }
    }

    context("PUT /admin/users/{userId}/deactivate") {
        test("should deactivate user successfully") {
            val targetUserId = UUID.randomUUID().toString()
            val targetUser = User(
                id = UUID.fromString(targetUserId),
                username = "targetuser",
                email = "target@example.com",
                passwordHash = "hash",
                isActive = false
            )

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.deactivateUser(targetUserId) } returns targetUser.right()
            coEvery { mockStorageService.getAllAdmins() } returns emptySet<String>().right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/admin/users/$targetUserId/deactivate") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<AdminUserResponse>()
                body.username shouldBe "targetuser"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.deactivateUser(targetUserId) }
                coVerify { mockStorageService.getAllAdmins() }
            }
        }

        test("should return 400 when attempting to deactivate self") {
            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/admin/users/${adminUserId}/deactivate") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Cannot deactivate your own account"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify(exactly = 0) { mockStorageService.deactivateUser(any()) }
            }
        }

        test("should return 500 when deactivate fails") {
            val targetUserId = UUID.randomUUID().toString()

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.deactivateUser(targetUserId) } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.put("/admin/users/$targetUserId/deactivate") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to deactivate user"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.deactivateUser(targetUserId) }
            }
        }
    }

    context("DELETE /admin/users/{userId}") {
        test("should delete user successfully") {
            val targetUserId = UUID.randomUUID().toString()

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.deleteUserCompletely(targetUserId) } returns Unit.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.delete("/admin/users/$targetUserId") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.OK
                val body = response.body<Map<String, String>>()
                body["message"] shouldBe "User deleted successfully"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.deleteUserCompletely(targetUserId) }
            }
        }

        test("should return 400 when attempting to delete self") {
            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.delete("/admin/users/${adminUserId}") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.BadRequest
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Cannot delete your own account"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify(exactly = 0) { mockStorageService.deleteUserCompletely(any()) }
            }
        }

        test("should return 500 when delete fails") {
            val targetUserId = UUID.randomUUID().toString()

            coEvery { mockStorageService.isUserAdmin(adminUserId.toString()) } returns true.right()
            coEvery { mockStorageService.deleteUserCompletely(targetUserId) } returns RedisError.OperationError("Database error").left()

            testApplication {
                environment {
                    config = MapApplicationConfig()
                }
                application {
                    install(ContentNegotiation) { json() }
                    installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adminRoutes(mockStorageService)
                    }
                }

                val client = createClient { install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) { json() } }
                val response = client.delete("/admin/users/$targetUserId") {
                    header("Authorization", "Bearer $adminToken")
                }

                response.status shouldBe HttpStatusCode.InternalServerError
                val body = response.body<Map<String, String>>()
                body["error"] shouldBe "Failed to delete user"

                coVerify { mockStorageService.isUserAdmin(adminUserId.toString()) }
                coVerify { mockStorageService.deleteUserCompletely(targetUserId) }
            }
        }
    }
})
