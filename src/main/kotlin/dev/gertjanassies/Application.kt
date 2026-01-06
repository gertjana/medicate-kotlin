package dev.gertjanassies

import dev.gertjanassies.routes.dailyRoutes
import dev.gertjanassies.routes.dosageHistoryRoutes
import dev.gertjanassies.routes.healthRoutes
import dev.gertjanassies.routes.medicineRoutes
import dev.gertjanassies.routes.scheduleRoutes
import dev.gertjanassies.service.RedisService
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module)
        .start(wait = true)
}

fun Application.module() {
    // Configure CORS
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Patch)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        anyHost() // For development - restrict in production
    }
    
    // Configure content negotiation
    install(ContentNegotiation) {
        json()
    }

    // Initialize Redis service (optional, for demonstration)
    val redisHost = environment.config.propertyOrNull("redis.host")?.getString() ?: "localhost"
    val redisPort = environment.config.propertyOrNull("redis.port")?.getString()?.toInt() ?: 6379
    val appEnvironment = environment.config.propertyOrNull("app.environment")?.getString() ?: "test"
    
    val redisService = RedisService(redisHost, redisPort, appEnvironment)
    
    // Attempt to connect to Redis (using functional error handling)
    redisService.connect().fold(
        ifLeft = { error -> 
            log.warn("Failed to connect to Redis: $error. Continuing without Redis.")
        },
        ifRight = { 
            log.info("Successfully connected to Redis at $redisHost:$redisPort")
        }
    )

    // Configure routing
    routing {
        healthRoutes()
        medicineRoutes(redisService)
        scheduleRoutes(redisService)
        dailyRoutes(redisService)
        dosageHistoryRoutes(redisService)
    }

    // Cleanup on shutdown
    environment.monitor.subscribe(ApplicationStopping) {
        redisService.close()
    }
}
