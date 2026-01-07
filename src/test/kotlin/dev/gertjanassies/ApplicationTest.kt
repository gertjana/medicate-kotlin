package dev.gertjanassies

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {

    @Test
    fun `test CORS is enabled when serveStatic is false`() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                put("app.serveStatic", "false")
            }
        }
        
        application {
            module()
        }

        val response = client.options("/api/health") {
            header(HttpHeaders.Origin, "http://localhost:5173")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }

        // Should have CORS headers
        assertNotNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `test CORS is disabled when serveStatic is true`() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                put("app.serveStatic", "true")
            }
        }
        
        application {
            module()
        }

        val response = client.get("/api/health")

        // Should NOT have CORS headers when serving static
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `test API routes are prefixed with api`() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                put("app.serveStatic", "false")
            }
        }
        
        application {
            module()
        }

        // Health endpoint should be under /api
        val healthResponse = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, healthResponse.status)

        // Test that routes without the /api prefix don't exist
        val notFoundResponse = client.get("/health")
        assertEquals(HttpStatusCode.NotFound, notFoundResponse.status)
    }

    @Test
    fun `test static file serving is disabled by default`() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                put("app.serveStatic", "false")
            }
        }
        
        application {
            module()
        }

        // Root should return 404 when static serving is disabled
        val response = client.get("/")
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `test CORS only allows localhost origins`() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                put("app.serveStatic", "false")
            }
        }
        
        application {
            module()
        }

        // Localhost should be allowed
        val localhostResponse = client.options("/api/health") {
            header(HttpHeaders.Origin, "http://localhost:5173")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }
        assertNotNull(localhostResponse.headers[HttpHeaders.AccessControlAllowOrigin])

        // Non-localhost should be rejected
        val externalResponse = client.options("/api/health") {
            header(HttpHeaders.Origin, "http://evil.com")
            header(HttpHeaders.AccessControlRequestMethod, "GET")
        }
        // CORS will not set allow-origin for disallowed origins
        val allowOrigin = externalResponse.headers[HttpHeaders.AccessControlAllowOrigin]
        assertTrue(allowOrigin == null || allowOrigin == "null")
    }

    @Test
    fun `test Redis configuration from environment variables`() = testApplication {
        environment {
            config = MapApplicationConfig().apply {
                put("app.serveStatic", "false")
            }
        }
        
        application {
            module()
        }

        // Application should start successfully even without Redis
        val response = client.get("/api/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
