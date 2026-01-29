package dev.gertjanassies.routes

import arrow.core.raise.either
import dev.gertjanassies.model.response.AdminUserResponse
import dev.gertjanassies.model.response.AdminUsersListResponse
import dev.gertjanassies.model.response.toResponse
import dev.gertjanassies.service.StorageService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("AdminRoutes")

fun Route.adminRoutes(storageService: StorageService) {
    route("/admin") {
        intercept(ApplicationCallPipeline.Call) {
            val userId = call.getUserId()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Not authenticated"))
                return@intercept finish()
            }

            val isAdminResult = storageService.isUserAdmin(userId)

            isAdminResult.fold(
                { error ->
                    logger.error("Failed to verify admin status for user $userId", error)
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        mapOf("error" to "Failed to verify admin privileges")
                    )
                    return@intercept finish()
                },
                { isAdmin ->
                    if (!isAdmin) {
                        logger.warn("Non-admin user $userId attempted to access admin endpoint")
                        call.respond(
                            HttpStatusCode.Forbidden,
                            mapOf("error" to "Admin privileges required")
                        )
                        return@intercept finish()
                    }
                }
            )
        }

        get("/users") {
            val result = storageService.getAllUsers()

            result.fold(
                { error ->
                    logger.error("Failed to get all users: ${error.message}")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to retrieve users"))
                },
                { users ->
                    val currentUserId = call.getUserId()
                    val adminIds = storageService.getAllAdmins().getOrNull() ?: emptySet()

                    val usersWithAdminFlag = users.map { user ->
                        AdminUserResponse(
                            id = user.id.toString(),
                            username = user.username,
                            email = user.email,
                            firstName = user.firstName,
                            lastName = user.lastName,
                            isActive = user.isActive,
                            isAdmin = adminIds.contains(user.id.toString()),
                            isSelf = user.id.toString() == currentUserId
                        )
                    }

                    logger.debug("Successfully retrieved ${users.size} users for admin")
                    call.respond(HttpStatusCode.OK, AdminUsersListResponse(usersWithAdminFlag))
                }
            )
        }

        put("/users/{userId}/activate") {
            val targetUserId = call.parameters["userId"]
            if (targetUserId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID is required"))
                return@put
            }

            val result = storageService.activateUser(targetUserId)

            result.fold(
                { error ->
                    logger.error("Failed to activate user $targetUserId: ${error.message}")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to activate user"))
                },
                { user ->
                    logger.debug("Admin activated user $targetUserId")
                    val currentUserId = call.getUserId()
                    val adminIds = storageService.getAllAdmins().getOrNull() ?: emptySet()
                    call.respond(HttpStatusCode.OK, AdminUserResponse(
                        id = user.id.toString(),
                        username = user.username,
                        email = user.email,
                        firstName = user.firstName,
                        lastName = user.lastName,
                        isActive = user.isActive,
                        isAdmin = adminIds.contains(user.id.toString()),
                        isSelf = user.id.toString() == currentUserId
                    ))
                }
            )
        }

        put("/users/{userId}/deactivate") {
            val targetUserId = call.parameters["userId"]
            if (targetUserId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID is required"))
                return@put
            }

            val currentUserId = call.getUserId()
            if (targetUserId == currentUserId) {
                logger.warn("Admin $currentUserId attempted to deactivate themselves")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot deactivate your own account"))
                return@put
            }

            val result = storageService.deactivateUser(targetUserId)

            result.fold(
                { error ->
                    logger.error("Failed to deactivate user $targetUserId: ${error.message}")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to deactivate user"))
                },
                { user ->
                    logger.debug("Admin deactivated user $targetUserId")
                    val adminIds = storageService.getAllAdmins().getOrNull() ?: emptySet()
                    call.respond(HttpStatusCode.OK, AdminUserResponse(
                        id = user.id.toString(),
                        username = user.username,
                        email = user.email,
                        firstName = user.firstName,
                        lastName = user.lastName,
                        isActive = user.isActive,
                        isAdmin = adminIds.contains(user.id.toString()),
                        isSelf = user.id.toString() == currentUserId
                    ))
                }
            )
        }

        delete("/users/{userId}") {
            val targetUserId = call.parameters["userId"]
            if (targetUserId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "User ID is required"))
                return@delete
            }

            val currentUserId = call.getUserId()
            if (targetUserId == currentUserId) {
                logger.warn("Admin $currentUserId attempted to delete themselves")
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Cannot delete your own account"))
                return@delete
            }

            val result = storageService.deleteUserCompletely(targetUserId)

            result.fold(
                { error ->
                    logger.error("Failed to delete user $targetUserId: ${error.message}")
                    call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to delete user"))
                },
                {
                    logger.debug("Admin completely deleted user $targetUserId and all associated data")
                    call.respond(HttpStatusCode.OK, mapOf("message" to "User deleted successfully"))
                }
            )
        }
    }
}
