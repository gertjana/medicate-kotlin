# Email Service Implementation for Password Reset

This implementation provides a complete email service using Resend for password reset functionality.

## Files Created

### 1. Model: PasswordResetToken.kt
Location: `src/main/kotlin/dev/gertjanassies/model/PasswordResetToken.kt`

Stores password reset tokens with expiry time in Redis.

### 2. Service: EmailService.kt
Location: `src/main/kotlin/dev/gertjanassies/service/EmailService.kt`

Main email service with the following features:
- **Token Generation**: Generates secure random tokens using `SecureRandom`
- **Redis Storage**: Stores tokens with 1-hour expiry under key `password_reset:{username}`
- **Email Sending**: Sends HTML emails via Resend API
- **Error Handling**: Uses Arrow's `Either` for functional error handling

#### Key Method: `resetPassword`
```kotlin
suspend fun resetPassword(user: User): Either<EmailError, String>
```

**Steps:**
1. Validates user has an email address
2. Generates a secure 32-byte random token
3. Sets expiry to 1 hour from now
4. Stores token in Redis under `password_reset:{username}`
5. Generates HTML email with reset link
6. Sends email via Resend API
7. Returns email ID on success

### 3. Tests: EmailServiceTest.kt
Location: `src/test/kotlin/dev/gertjanassies/service/EmailServiceTest.kt`

Comprehensive test suite covering:
-  Successful password reset flow
-  User with no email error handling
-  Redis storage failure handling
-  Resend API error handling
-  Invalid email format validation
-  Unique token generation
-  Email content verification

All tests mock the Resend service using Ktor's MockEngine.

## Dependencies Added

Updated `build.gradle.kts` with:
```kotlin
// HTTP client for Resend API
implementation("io.ktor:ktor-client-core:2.3.7")
implementation("io.ktor:ktor-client-cio:2.3.7")
implementation("io.ktor:ktor-client-content-negotiation:2.3.7")

// Testing
testImplementation("io.ktor:ktor-client-mock:2.3.7")
```

## Configuration

### Environment Variable
Set the Resend API key:
```bash
export RESEND_APIKEY="re_your_api_key_here"
```

### Usage Example
```kotlin
val apiKey = System.getenv("RESEND_APIKEY") ?: "default_key"
val httpClient = HttpClient(CIO) {
    install(ContentNegotiation) {
        json()
    }
}

val emailService = EmailService(
    httpClient = httpClient,
    redisService = redisService,
    apiKey = apiKey,
    fromEmail = "noreply@medicate.app"
)

// Send password reset email
val user = User(
    username = "john",
    email = "john@example.com",
    passwordHash = "..."
)

emailService.resetPassword(user).fold(
    { error -> println("Error: ${error.message}") },
    { emailId -> println("Email sent: $emailId") }
)
```

## Email Template

The service generates a professional HTML email with:
- Header with "Medicate - Password Reset" branding
- Personalized greeting with username
- Reset password button linking to reset URL
- Plain text link as fallback
- 1-hour expiry warning
- Footer with branding and disclaimer

Reset URL format: `https://medicate.app/reset-password?token={token}`

## Error Handling

Three error types:
- `EmailError.InvalidEmail`: User has no/invalid email
- `EmailError.TokenGenerationFailed`: Failed to generate token
- `EmailError.SendFailed`: Redis storage or Resend API failure

## Testing

Run tests with:
```bash
./gradlew test --tests "*EmailServiceTest"
```

All 7 tests cover the complete password reset flow with mocked dependencies.

## Security Features

1. **Secure Token Generation**: Uses `SecureRandom` with 32 bytes (256 bits)
2. **Time-Limited Tokens**: 1-hour expiry stored in Redis
3. **URL-Safe Encoding**: Base64 URL encoding without padding
4. **Functional Error Handling**: Arrow's `Either` prevents exceptions

## Integration Points

- **RedisService**: Stores tokens with key `password_reset:{username}`
- **Resend API**: POST to `https://api.resend.com/emails`
- **User Model**: Requires `username` and `email` fields

## Next Steps

To integrate into your application:

1. **Sync Gradle dependencies**:
   ```bash
   ./gradlew build
   ```

2. **Create EmailService instance** in Application.kt:
   ```kotlin
   val apiKey = environment.config.propertyOrNull("resend.apiKey")?.getString()
       ?: System.getenv("RESEND_APIKEY")
       ?: ""

   val httpClient = HttpClient(CIO) {
       install(ContentNegotiation) { json() }
   }

   val emailService = EmailService(httpClient, redisService, apiKey)
   ```

3. **Add password reset route** (optional):
   ```kotlin
   post("/auth/forgot-password") {
       val username = call.receive<ForgotPasswordRequest>().username
       val user = redisService.getUser(username).getOrNull()

       if (user != null) {
           emailService.resetPassword(user)
           call.respond(HttpStatusCode.OK, "Reset email sent")
       } else {
           // Don't reveal if user exists
           call.respond(HttpStatusCode.OK, "If account exists, email sent")
       }
   }
   ```

4. **Add reset password verification route** to validate tokens and update passwords.
