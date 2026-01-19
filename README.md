# Medicate - Medicine Tracking Application

A functional Kotlin REST API service built with Ktor framework, Arrow for functional programming, and Redis for backend storage. Features multi-user support with JWT authentication and complete data isolation per user.

## Quick Start

### Prerequisites
- Java 21+
- Redis server
- Node.js 18+ (for frontend)

### 1. Start Redis
```bash
redis-server
```
or as a docker container

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

### Environment variables
- `JWT_SECRET` - Secret key for signing JWT tokens (generate with `openssl rand -base64 64`)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)
- `APP_ENV` - Environment name for Redis keys (default: test)
- `RESEND_API_KEY` - API key for Resend email service (for password reset)
- `APP_URL` - Application URL for password reset emails (default: http://localhost:5173)

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

## License

MIT
