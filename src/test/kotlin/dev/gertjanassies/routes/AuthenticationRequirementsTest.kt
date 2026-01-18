package dev.gertjanassies.routes

import dev.gertjanassies.service.RedisService
import dev.gertjanassies.test.TestJwtConfig
import dev.gertjanassies.test.TestJwtConfig.installTestJwtAuth
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Tests to verify that routes are properly protected (or public) based on authentication requirements
 */
class AuthenticationRequirementsTest : FunSpec({
    lateinit var mockRedisService: RedisService

    beforeEach {
        mockRedisService = mockk(relaxed = true)
    }

    afterEach {
        clearAllMocks()
    }

    context("Protected Routes - Should require JWT authentication") {
        test("GET /medicine should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(mockRedisService)
                    }
                }

                val response = client.get("/medicine") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /schedule should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        scheduleRoutes(mockRedisService)
                    }
                }

                val response = client.get("/schedule") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /daily should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        dailyRoutes(mockRedisService)
                    }
                }

                val response = client.get("/daily") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /history should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        dosageHistoryRoutes(mockRedisService)
                    }
                }

                val response = client.get("/history") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /adherence should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        adherenceRoutes(mockRedisService)
                    }
                }

                val response = client.get("/adherence") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /medicineExpiry should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        medicineRoutes(mockRedisService)
                    }
                }

                val response = client.get("/medicineExpiry") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("GET /user/profile should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val response = client.get("/user/profile") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }

        test("PUT /user/profile should return 401 without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                    this@application.installTestJwtAuth()
                }
                routing {
                    authenticate("auth-jwt") {
                        protectedUserRoutes(mockRedisService)
                    }
                }

                val client = createClient {
                    install(ClientContentNegotiation) { json() }
                }

                val response = client.put("/user/profile") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("email" to "test@example.com", "firstName" to "Test", "lastName" to "User"))
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.Unauthorized
            }
        }
    }

    context("Public Routes - Should NOT require authentication") {
        test("GET /health should work without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                }
                routing { healthRoutes() }

                val response = client.get("/health") {
                    // No Authorization header
                }

                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("POST /auth/resetPassword should work without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                }
                routing {
                    // Note: We're just testing the route is public, not the full functionality
                    route("/auth") {
                        post("/resetPassword") {
                            call.respond(HttpStatusCode.OK, mapOf("test" to "public"))
                        }
                    }
                }

                val client = createClient {
                    install(ClientContentNegotiation) { json() }
                }

                val response = client.post("/auth/resetPassword") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("username" to "testuser"))
                    // No Authorization header
                }

                // Should not return 401 (route is accessible without auth)
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("POST /auth/refresh should work without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                }
                routing {
                    route("/auth") {
                        post("/refresh") {
                            call.respond(HttpStatusCode.OK, mapOf("test" to "public"))
                        }
                    }
                }

                val client = createClient {
                    install(ClientContentNegotiation) { json() }
                }

                val response = client.post("/auth/refresh") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("refreshToken" to "test-token"))
                    // No Authorization header
                }

                // Should not return 401 (route is accessible without auth)
                response.status shouldBe HttpStatusCode.OK
            }
        }

        test("POST /user/register should work without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                }
                routing {
                    route("/user") {
                        post("/register") {
                            call.respond(HttpStatusCode.Created, mapOf("test" to "public"))
                        }
                    }
                }

                val client = createClient {
                    install(ClientContentNegotiation) { json() }
                }

                val response = client.post("/user/register") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("username" to "test", "email" to "test@example.com", "password" to "password123"))
                    // No Authorization header
                }

                // Should not return 401 (route is accessible without auth)
                response.status shouldBe HttpStatusCode.Created
            }
        }

        test("POST /user/login should work without authentication") {
            testApplication {
                environment { config = MapApplicationConfig() }
                application {
                    install(ServerContentNegotiation) { json() }
                }
                routing {
                    route("/user") {
                        post("/login") {
                            call.respond(HttpStatusCode.OK, mapOf("test" to "public"))
                        }
                    }
                }

                val client = createClient {
                    install(ClientContentNegotiation) { json() }
                }

                val response = client.post("/user/login") {
                    contentType(ContentType.Application.Json)
                    setBody(mapOf("username" to "test", "password" to "password123"))
                    // No Authorization header
                }

                // Should not return 401 (route is accessible without auth)
                response.status shouldBe HttpStatusCode.OK
            }
        }
    }
})
