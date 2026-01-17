package dev.gertjanassies

import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
import dev.gertjanassies.routes.adherenceRoutes
import dev.gertjanassies.routes.authRoutes
import dev.gertjanassies.routes.dailyRoutes
import dev.gertjanassies.routes.dosageHistoryRoutes
import dev.gertjanassies.routes.healthRoutes
import dev.gertjanassies.routes.medicineRoutes
import dev.gertjanassies.routes.scheduleRoutes
import dev.gertjanassies.routes.userRoutes
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.RedisService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import org.slf4j.event.Level
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

fun main() {
    embeddedServer(Netty, port = 8080, host = "127.0.0.1", module = Application::module)
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
            // Allow requests from localhost:5173 for local development
            allowHost("localhost:5173", schemes = listOf("http", "https"))
            allowHost("127.0.0.1:5173", schemes = listOf("http", "https"))
            allowHost("medicate-kotlin.onrender.com", schemes = listOf("https"))
        }
    }

    // Configure content negotiation
    install(ContentNegotiation) {
        json(
            Json {
                serializersModule = SerializersModule {
                    contextual(java.util.UUID::class, UUIDSerializer)
                    contextual(java.time.LocalDateTime::class, LocalDateTimeSerializer)
                }
                ignoreUnknownKeys = true
            }
        )
    }

    // Initialize Redis service
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

    // Initialize EmailService
    val resendApiKey = environment.config.propertyOrNull("resend.apiKey")?.getString()
        ?: System.getenv("RESEND_API_KEY") ?: ""
    val appUrl = environment.config.propertyOrNull("app.url")?.getString()
        ?: System.getenv("APP_URL") ?: "http://localhost:5173"

    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val emailService = EmailService(httpClient, redisService, resendApiKey, appUrl)

    log.info("Configuring routing...")
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
            authRoutes(redisService, emailService)
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
