package dev.gertjanassies.routes

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Health check routes
 */
fun Route.healthRoutes() {
    get("/health") {
        call.respond(HttpStatusCode.OK)
    }
}
