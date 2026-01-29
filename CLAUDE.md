# Medicate - Developer Guide

## Project Overview

Medicate is a functional Kotlin REST API service for medicine tracking and adherence monitoring. The application features:

- Multi-user support with JWT authentication
- Complete data isolation per user
- Medicine inventory and schedule management
- Dosage history tracking and adherence monitoring
- Password reset via email (Resend API)
- SQLite medicine database search
- Redis-backed storage

## Architecture

### Functional Programming with Arrow

This project uses **Arrow** for functional error handling and data manipulation. Key patterns:

- **Either<L, R>**: Represents computations that can fail. Left contains errors, Right contains success values.
- **fold()**: Pattern match on Either to handle both success and failure cases.
- **map()**: Transform the right (success) value while preserving the error channel.
- **flatMap()**: Chain operations that return Either, avoiding nested Either types.

Example from Application.kt:202:
```kotlin
redisService.connect().fold(
    ifLeft = { error -> log.warn("Failed to connect: $error") },
    ifRight = { log.info("Successfully connected") }
)
```

### Service Layer Architecture

The application follows a clean service-oriented architecture:

**Services:**
- `StorageService` - Interface for data persistence operations
- `RedisService` - Redis implementation of StorageService with functional error handling
- `JwtService` - JWT token generation and validation
- `EmailService` - Email notifications via Resend API
- `MedicineSearchService` - SQLite medicine database queries

**Models:**
- Domain models in `src/main/kotlin/dev/gertjanassies/model/`
- Request/Response DTOs in `model/request/` and `model/response/`
- Custom serializers for UUID and LocalDateTime in `model/serializer/`

**Routes:**
- Routes are organized by feature domain in `src/main/kotlin/dev/gertjanassies/routes/`
- Authentication handled via Ktor's JWT plugin
- Public routes: health, auth, user registration
- Protected routes: medicines, schedules, dosage history, adherence

## Technology Stack

### Backend
- **Kotlin 1.9.22** with Java 21
- **Ktor 2.3.7** - Async web framework
- **Arrow 1.2.1** - Functional programming library
- **Lettuce 6.3.0** - Redis client
- **SQLite** - Medicine database
- **JWT** - Authentication
- **BCrypt** - Password hashing
- **Kotest 5.8.0** - Testing framework

### Frontend
- **SvelteKit** with TypeScript
- **TailwindCSS**

## Project Structure

```
src/main/kotlin/dev/gertjanassies/
├── Application.kt              # Main entry point, server configuration
├── model/
│   ├── *.kt                    # Domain models (User, Medicine, Schedule, etc.)
│   ├── request/                # Request DTOs
│   ├── response/               # Response DTOs
│   └── serializer/             # Custom serializers
├── routes/
│   ├── AuthRoutes.kt           # Login, password reset
│   ├── UserRoutes.kt           # User registration, profile
│   ├── MedicineRoutes.kt       # Medicine CRUD
│   ├── ScheduleRoutes.kt       # Schedule management
│   ├── DosageHistoryRoutes.kt  # Dosage tracking
│   ├── AdherenceRoutes.kt      # Adherence statistics
│   ├── DailyRoutes.kt          # Daily schedule view
│   └── HealthRoutes.kt         # Health check endpoint
├── service/
│   ├── StorageService.kt       # Storage interface
│   ├── RedisService.kt         # Redis implementation
│   ├── JwtService.kt           # JWT operations
│   ├── EmailService.kt         # Email notifications
│   └── MedicineSearchService.kt # Medicine database search
└── util/
    └── ValidationUtils.kt      # Input validation helpers

src/test/kotlin/dev/gertjanassies/
├── routes/                     # Route tests
├── service/                    # Service tests
└── util/                       # Utility tests
```

## Key Patterns and Conventions

### Error Handling

Use Arrow's `Either` for operations that can fail:

```kotlin
fun operation(): Either<String, Result> {
    return if (success) {
        Result.right()
    } else {
        "Error message".left()
    }
}

// Usage
operation().fold(
    ifLeft = { error -> call.respond(HttpStatusCode.BadRequest, error) },
    ifRight = { result -> call.respond(HttpStatusCode.OK, result) }
)
```

### Authentication

Protected routes are wrapped in `authenticate("auth-jwt")` block. Access user info via:

```kotlin
val principal = call.principal<JWTPrincipal>()
val username = principal?.payload?.getClaim("username")?.asString()
```

### Data Isolation

All user data is namespaced by username in Redis using keys like:
- `{env}:user:{username}`
- `{env}:medicine:{username}:{medicineId}`
- `{env}:schedule:{username}:{scheduleId}`

### Testing

- Use Kotest for test assertions
- Route tests use `testApplication` with mock services
- Service tests verify functional error handling
- Test helpers in `util/RedisTestHelpers.kt`

Example test structure:
```kotlin
class ServiceTest : FreeSpec({
    "operation" - {
        "should handle success" {
            // Arrange
            val service = createService()

            // Act
            val result = service.operation()

            // Assert
            result.isRight() shouldBe true
        }

        "should handle failure" {
            // Test error case
        }
    }
})
```

## Configuration

### Environment Variables

- `JWT_SECRET` - JWT signing key (required in production)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `REDIS_TOKEN` - Redis auth token (optional)
- `APP_ENV` - Environment name for key namespacing (default: test)
- `RESEND_API_KEY` - Resend API key for emails (optional)
- `APP_URL` - Application URL for email links (default: http://localhost:5173)
- `SERVE_STATIC` - Serve frontend files from /static (default: false)

### Application Config

Alternative to environment variables: `src/main/resources/application.conf`

## Common Development Tasks

### Running the Application

```bash
# Backend
./gradlew run

# Frontend (separate terminal)
cd frontend
npm install
npm run dev
```

### Testing

```bash
# Run all tests
./gradlew test

# Run with coverage
./gradlew test jacocoTestReport

# Coverage report at: build/reports/jacoco/test/html/index.html
```

### Building

```bash
# Build JAR
./gradlew jar

# JAR location: build/libs/*.jar
```

### Database Operations

The medicine database is SQLite-based. Search functionality is provided via `MedicineSearchService`.

### Redis Data Structure

User data is stored in Redis as JSON strings with the following key patterns:
- Users: `{env}:user:{username}`
- Medicines: `{env}:medicine:{username}:{id}`
- Schedules: `{env}:schedule:{username}:{id}`
- Dosage History: `{env}:dosage:{username}:{id}`
- Password Reset Tokens: `{env}:reset-token:{token}`

## Development Guidelines

### Code Style

- Use functional error handling with Arrow's `Either`
- Prefer immutable data classes
- Use Kotlin's null safety features
- Follow existing naming conventions
- Keep routes thin - business logic belongs in services

### Adding New Features

1. Define domain models in `model/`
2. Add request/response DTOs if needed
3. Implement service layer with functional error handling
4. Create routes with proper authentication
5. Write tests for both service and route layers
6. Update this documentation if adding significant functionality

### Testing
1. Write unit tests for new services
2. Write route tests for new endpoints
3. Only mock external dependencies (Redis, SQLite), never mock internal services
4. Validate if tests are adding value, not just coverage

### Security Considerations

- Never log sensitive data (passwords, tokens)
- Always use BCrypt for password hashing
- Validate and sanitize all user inputs
- Use parameterized queries for database operations
- Ensure proper data isolation between users
- Validate JWT tokens on all protected routes

### API Endpoints

All endpoints are prefixed with `/api`:

**Public:**
- `POST /api/auth/login` - User authentication
- `POST /api/auth/request-reset` - Request password reset
- `POST /api/auth/verify-token` - Verify reset token
- `POST /api/auth/reset-password` - Reset password with token
- `POST /api/users/register` - User registration
- `GET /api/users/activate/:token` - Activate account
- `GET /api/health` - Health check

**Protected (require JWT):**
- Medicine management (`/api/medicines/*`)
- Medicine search (`/api/medicines/search`)
- Schedule management (`/api/schedules/*`)
- Dosage history (`/api/dosage/*`)
- Adherence tracking (`/api/adherence/*`)
- Daily schedule view (`/api/daily/*`)
- User profile (`/api/users/profile`)

## Resources

- [Ktor Documentation](https://ktor.io/)
- [Arrow Documentation](https://arrow-kt.io/)
- [Kotest Documentation](https://kotest.io/)
- [Lettuce Documentation](https://lettuce.io/)

## License

MIT
