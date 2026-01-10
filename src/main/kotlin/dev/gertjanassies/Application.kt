package dev.gertjanassies

import dev.gertjanassies.routes.adherenceRoutes
import dev.gertjanassies.routes.dailyRoutes
import dev.gertjanassies.routes.dosageHistoryRoutes
import dev.gertjanassies.routes.healthRoutes
import dev.gertjanassies.routes.medicineRoutes
import dev.gertjanassies.routes.scheduleRoutes
import dev.gertjanassies.routes.userRoutes
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import java.io.File

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    val serveStatic = environment.config.propertyOrNull("app.serveStatic")?.getString()?.toBoolean()
        ?: System.getenv("SERVE_STATIC")?.toBoolean() ?: false

    // Configure CORS - only needed in development when frontend is served separately
    if (!serveStatic) {
        install(CORS) {
            allowMethod(HttpMethod.Options)
            allowMethod(HttpMethod.Put)
            allowMethod(HttpMethod.Delete)
            allowMethod(HttpMethod.Patch)
            allowHeader(HttpHeaders.Authorization)
            allowHeader(HttpHeaders.ContentType)
            // Allow localhost origins and file:// protocol in development
            allowHost("localhost:5173") // Vite dev server
            allowHost("localhost:3000") // Alternative dev port
            allowHost("127.0.0.1:5173")
            allowHost("127.0.0.1:3000")
            // Allow any localhost port for development tools
            anyHost() // This allows file:// protocol and any localhost
        }
    }

    // Configure compression for static files
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }

    // Configure content negotiation
    install(ContentNegotiation) {
        json()
    }

    // Initialize Redis service (optional, for demonstration)
    val redisHost = environment.config.propertyOrNull("redis.host")?.getString()
        ?: System.getenv("REDIS_HOST") ?: "localhost"
    val redisPort = environment.config.propertyOrNull("redis.port")?.getString()?.toInt()
        ?: System.getenv("REDIS_PORT")?.toIntOrNull() ?: 6379
    val appEnvironment = environment.config.propertyOrNull("app.environment")?.getString()
        ?: System.getenv("APP_ENV") ?: "test"

    val redisService = RedisService(redisHost, redisPort, appEnvironment)

    // Attempt to connect to Redis (using functional error handling)
    redisService.connect().fold(
        ifLeft = { error ->
            this@module.log.warn("Failed to connect to Redis: $error. Continuing without Redis.")
        },
        ifRight = {
            this@module.log.info("Successfully connected to Redis at $redisHost:$redisPort")
        }
    )

    // Configure routing
    routing {
        // API routes under /api prefix
        route("/api") {
            healthRoutes()
            medicineRoutes(redisService)
            scheduleRoutes(redisService)
            dailyRoutes(redisService)
            dosageHistoryRoutes(redisService)
            adherenceRoutes(redisService)
            userRoutes(redisService)
        }

        // Serve static files if enabled
        if (serveStatic) {
            val staticDir = File("static")
            if (staticDir.exists()) {
                // Serve static assets (JS, CSS, images, etc.)
                staticFiles("/", staticDir)

                // SPA fallback: serve index.html for any non-API routes that don't match static files
                // This allows client-side routing to work correctly
                get("{...}") {
                    val path = call.request.path()
                    // Only serve index.html for non-API routes
                    if (!path.startsWith("/api/")) {
                        call.respondFile(File(staticDir, "index.html"))
                    }
                }
            } else {
                this@module.log.warn("Static directory not found at: ${staticDir.absolutePath}")
            }
        }
    }

    // Cleanup on shutdown
    environment.monitor.subscribe(ApplicationStopping) {
        redisService.close()
    }
}
