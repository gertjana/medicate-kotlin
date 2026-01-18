# Medicate - Medicine Tracking Application

A functional Kotlin REST API service built with Ktor framework, Arrow for functional programming, and Redis for backend storage. Features multi-user support with JWT authentication and complete data isolation per user.

## Features

- 💊 **Medicine Management** - Track medicines with dosage, stock, and descriptions
- 📅 **Schedule Management** - Create schedules for taking medicines
- 📊 **Dosage History** - Record and track medicine intake
- 📈 **Adherence Tracking** - Monitor weekly adherence to schedules
- 👥 **Multi-User Support** - Each user has completely isolated data
- 🔐 **JWT Authentication** - Secure authentication with cryptographically signed tokens
- 📧 **Password Reset** - Email-based password reset flow
- ⏰ **Medicine Expiry Tracking** - Calculate when medicines will run out based on schedules

## Security

This application uses **JWT (JSON Web Token) authentication** with **refresh tokens** for secure user authentication:

- ✅ Cryptographically signed tokens (HMAC SHA-256)
- ✅ Short-lived access tokens (1 hour)
- ✅ Long-lived refresh tokens (30 days)
- ✅ Automatic token refresh (seamless UX)
- ✅ Cannot be forged without secret key
- ✅ All protected routes require valid JWT
- ✅ Production-grade security

**User Experience:** Users stay logged in for 30 days with automatic token refresh in the background.

See `REFRESH_TOKEN_IMPLEMENTATION.md` for complete details.

## Quick Start

### Prerequisites
- Java 21+
- Redis server running on localhost:6379
- Node.js 18+ (for frontend)

### 1. Start Redis
```bash
redis-server
```

### 2. Set Environment Variables
```bash
# Required for production, optional for development
export JWT_SECRET=$(openssl rand -base64 64)
export RESEND_API_KEY=your-resend-api-key  # For password reset emails
```

### 3. Start Backend
```bash
./gradlew run
```

### 4. Start Frontend (in new terminal)
```bash
cd frontend
npm install
npm run dev
```

### 5. Access Application
Open http://localhost:5173 in your browser

## Configuration

The application can be configured via `src/main/resources/application.conf` or environment variables:

### Required (Production)
- `JWT_SECRET` - Secret key for signing JWT tokens (generate with `openssl rand -base64 64`)

### Optional
- `PORT` - Server port (default: 8080)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `APP_ENV` - Environment name for Redis keys (default: test)
- `RESEND_API_KEY` - API key for Resend email service (for password reset)
- `APP_URL` - Application URL for password reset emails (default: http://localhost:5173)

## API Endpoints

All endpoints require `X-Username` header (automatically sent by frontend when logged in).

### User
- `POST /api/user/register` - Register new user
- `POST /api/user/login` - Login existing user

### Medicine
- `GET /api/medicine` - Get all medicines
- `POST /api/medicine` - Create medicine
- `PUT /api/medicine/{id}` - Update medicine
- `DELETE /api/medicine/{id}` - Delete medicine
- `POST /api/addstock` - Add stock to medicine

### Schedule
- `GET /api/schedule` - Get all schedules
- `POST /api/schedule` - Create schedule
- `PUT /api/schedule/{id}` - Update schedule
- `DELETE /api/schedule/{id}` - Delete schedule

### Daily & History
- `GET /api/daily` - Get today's schedule
- `POST /api/takedose` - Record dose taken
- `GET /api/history` - Get dosage history

### Analytics
- `GET /api/adherence` - Get weekly adherence
- `GET /api/lowstock` - Get low stock medicines

## Technology Stack

**Backend:**
- Kotlin 1.9.22
- Ktor 2.3.7
- Arrow 1.2.1 (Functional Programming)
- Lettuce 6.3.0 (Redis Client)
- Kotest 5.8.0 (Testing)
- JWT (JSON Web Tokens) for Authentication

**Frontend:**
- SvelteKit
- TypeScript
- TailwindCSS

## Authentication

This application uses **JWT (JSON Web Token)** authentication with **refresh tokens**:

1. **Register/Login** - User receives an access token + refresh token
2. **Token Storage** - Both tokens stored in browser localStorage
3. **API Requests** - Access token sent in `Authorization: Bearer <token>` header
4. **Token Validation** - Backend validates signature, expiration, and claims
5. **Auto-Refresh** - When access token expires, frontend automatically uses refresh token to get new access token
6. **Logout** - Both tokens removed from localStorage

**Token Lifespans:**
- **Access Token:** 1 hour (for security - short-lived)
- **Refresh Token:** 30 days (for UX - stay logged in longer)

**How It Works:**
- Access tokens are used for every API request
- When access token expires (after 1 hour), frontend automatically:
  - Uses refresh token to get new access token
  - Retries the original request
  - User doesn't notice anything!
- When refresh token expires (after 30 days), user must login again

**Security:**
- Algorithm: HMAC SHA-256
- Access tokens expire quickly (limits damage if stolen)
- Refresh tokens only used during refresh (not on every request)
- Cannot be forged without `JWT_SECRET`

See `REFRESH_TOKEN_IMPLEMENTATION.md` for complete implementation details.

## Testing

### Backend Tests
```bash
./gradlew test

# Result: 165/165 tests passing ✅
```

All protected routes are tested with JWT authentication using the `TestJwtConfig` helper.

### Frontend Build
```bash
cd frontend && npm run build

# Result: Build successful ✅
```

## Production Deployment

See `PRODUCTION_DEPLOYMENT_CHECKLIST.md` for complete deployment guide.

**Quick Deploy:**
1. Generate JWT secret: `openssl rand -base64 64`
2. Set environment variables in hosting platform
3. Deploy: `git push origin main`

**Required Environment Variables:**
```bash
JWT_SECRET=<strong-random-secret>
REDIS_URL=<redis-connection-string>
RESEND_API_KEY=<email-api-key>
APP_URL=<your-app-url>
```

## Documentation

- `FINAL_JWT_SUMMARY.md` - Complete JWT implementation overview
- `JWT_IMPLEMENTATION.md` - Backend technical details
- `FRONTEND_JWT_COMPLETE.md` - Frontend implementation
- `PASSWORD_RESET_FIX.md` - Password reset flow
- `PRODUCTION_DEPLOYMENT_CHECKLIST.md` - Deployment guide

## License

MIT

## Author

[Gert Jan Assies](https://gertjanassies.dev)

**Database:**
- Redis

## Development

### Running Tests
```bash
./gradlew test
```

### Building for Production
```bash
./gradlew build
```

## Redis Key Structure

Multi-user data isolation using:
```
{environment}:user:{username}:medicine:{medicineId}
{environment}:user:{username}:schedule:{scheduleId}
{environment}:user:{username}:dosagehistory:{dosageId}
```

Each user's data is completely isolated from other users.
