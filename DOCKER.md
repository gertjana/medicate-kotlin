# Docker Deployment

This project can be deployed as a single Docker container that serves both the frontend and backend.

## Building and Running

### Using Docker Compose (Recommended)

```bash
# Build and start the application with Redis
docker-compose up -d

# View logs
docker-compose logs -f app

# Stop the application
docker-compose down
```

The application will be available at http://localhost:8080

### Using Docker directly

```bash
# Build the image
docker build -t medicate-app .

# Run with Redis connection
docker run -d \
  -p 8080:8080 \
  -e REDIS_HOST=localhost \
  -e REDIS_PORT=6379 \
  -e APP_ENV=production \
  -e SERVE_STATIC=true \
  --name medicate \
  medicate-app
```

## Environment Variables

- `REDIS_HOST` - Redis server hostname (default: localhost)
- `REDIS_PORT` - Redis server port (default: 6379)
- `APP_ENV` - Application environment: test/production (default: test)
- `SERVE_STATIC` - Enable static file serving for frontend (default: false, set to true in Docker)
- `PORT` - Server port (default: 8080)

## Development Mode

For local development with hot reload:

### Backend
```bash
./gradlew run
```

### Frontend
```bash
cd frontend
npm run dev
```

In development mode:
- Frontend runs on http://localhost:5173 with Vite dev server
- Backend runs on http://localhost:8080
- CORS is enabled to allow cross-origin requests
- Frontend proxies API calls to backend via `/api` prefix

## Production Mode (Docker)

In production mode (when SERVE_STATIC=true):
- Both frontend and backend served from single origin (http://localhost:8080)
- CORS is **disabled** (not needed for same-origin)
- Frontend static files served from `/static` directory
- API routes accessible at `/health`, `/medicine`, `/schedule`, etc.
- SPA routing handled by serving index.html for non-API routes

## Architecture

The Docker image uses a multi-stage build:

1. **Frontend Build Stage**: Builds SvelteKit app as static files
2. **Backend Build Stage**: Compiles Kotlin code and creates fat JAR
3. **Runtime Stage**: Combines both into minimal JRE-based image

This approach:
- Reduces image size
- Eliminates CORS complexity
- Simplifies deployment
- Single container to manage
