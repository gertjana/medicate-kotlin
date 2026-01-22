package dev.gertjanassies.routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Helper functions to extract claims from JWT token
 */

/**
 * Extract username from JWT token
 */
fun ApplicationCall.getUsername(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("username")?.asString()
}

/**
 * Extract user ID from JWT token
 */
fun ApplicationCall.getUserId(): String? {
    val principal = principal<JWTPrincipal>()
    return principal?.payload?.getClaim("userId")?.asString()
}
