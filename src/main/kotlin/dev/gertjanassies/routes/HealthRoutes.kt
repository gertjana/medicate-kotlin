package dev.gertjanassies.routes

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.right
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.healthRoutes() {
    get("/health") {
        either {
            performHealthCheck().bind()
            call.respond(HttpStatusCode.OK)
        }.onLeft { error ->
            call.respond(HttpStatusCode.ServiceUnavailable, mapOf("error" to error))
        }
    }
}

private suspend fun performHealthCheck(): Either<String, Unit> {    
    // Optionally check for more things (but not external services, never external services)
    return Unit.right()
}
