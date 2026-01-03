# Kotlin Ktor Arrow Project

A functional Kotlin REST API service built with Ktor framework, Arrow for functional programming, and Redis for backend storage.

## Configuration

The application can be configured via `src/main/resources/application.conf` or environment variables:

- `PORT` - Server port (default: 8080)
- `REDIS_HOST` - Redis host (default: localhost)
- `REDIS_PORT` - Redis port (default: 6379)

## Running the Application

### Using Gradle

```bash
./gradle run
```

### Building a JAR

```bash
./gradle build
java -jar build/libs/kotlin-ktor-arrow-1.0.0.jar
```
