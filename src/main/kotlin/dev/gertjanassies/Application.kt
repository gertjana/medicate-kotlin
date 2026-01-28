package dev.gertjanassies

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import dev.gertjanassies.model.serializer.LocalDateTimeSerializer
import dev.gertjanassies.model.serializer.UUIDSerializer
import dev.gertjanassies.routes.adherenceRoutes
import dev.gertjanassies.routes.authRoutes
import dev.gertjanassies.routes.dailyRoutes
import dev.gertjanassies.routes.dosageHistoryRoutes
import dev.gertjanassies.routes.healthRoutes
import dev.gertjanassies.routes.medicineRoutes
import dev.gertjanassies.routes.medicineSearchRoutes
import dev.gertjanassies.routes.protectedUserRoutes
import dev.gertjanassies.routes.scheduleRoutes
import dev.gertjanassies.routes.userRoutes
import dev.gertjanassies.service.EmailService
import dev.gertjanassies.service.JwtService
import dev.gertjanassies.service.RedisService
import dev.gertjanassies.service.StorageService
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.http.content.staticFiles
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.path
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import java.io.File
import java.util.*

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
            allowHost("localhost:5173", schemes = listOf("http", "https"))
            allowHost("127.0.0.1:5173", schemes = listOf("http", "https"))
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
    val redisToken = environment.config.propertyOrNull("REDIS_TOKEN")?.getString()
        ?: System.getenv("REDIS_TOKEN") ?: ""

    val redisService: StorageService = RedisService(redisHost, redisPort, redisToken, appEnvironment)

    // Attempt to connect to Redis (using functional error handling)
    (redisService as RedisService).connect().fold(
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

    if (resendApiKey.isEmpty()) {
        log.warn("⚠️  RESEND_API_KEY is not set - email functionality will not work")
        log.warn("⚠️  Set the RESEND_API_KEY environment variable to enable password reset and account activation emails")
    }

    val httpClient = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    val emailService = EmailService(httpClient, redisService, resendApiKey, appUrl)

    // Initialize JWT Service
    val jwtSecret = environment.config.propertyOrNull("jwt.secret")?.getString()
        ?: System.getenv("JWT_SECRET")
        ?: "default-secret-change-in-production"

    if (jwtSecret.startsWith("default-secret")) {
        log.warn("⚠️  Using default JWT secret! Set JWT_SECRET environment variable in production!")
    }

    val jwtService = JwtService(jwtSecret)

    // Install JWT Authentication
    install(Authentication) {
        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience("medicate-users")
                    .withIssuer("medicate-app")
                    .build()
            )
            validate { credential ->
                val username = credential.payload.getClaim("username").asString()
                if (username != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or expired token"))
            }
        }
    }

    log.info("Configuring routing...")
    // Configure routing
    routing {
        // API routes under /api prefix
        route("/api") {
            // Public routes (no authentication required)
            healthRoutes()
            authRoutes(redisService, emailService, jwtService)
            userRoutes(redisService, jwtService, emailService)  // Login/register are public
            medicineSearchRoutes()  // Medicine database search (public - used in add/edit forms)

            // Protected routes (require JWT authentication)
            authenticate("auth-jwt") {
                protectedUserRoutes(redisService)
                medicineRoutes(redisService)
                scheduleRoutes(redisService)
                dailyRoutes(redisService)
                dosageHistoryRoutes(redisService)
                adherenceRoutes(redisService)
            }
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
        (redisService as? RedisService)?.close()
    }
}
