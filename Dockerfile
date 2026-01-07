# Multi-stage build for Kotlin Ktor + SvelteKit application

# Stage 1: Build frontend
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Copy frontend package files
COPY frontend/package*.json ./
RUN npm ci

# Copy frontend source and build
COPY frontend/ ./
RUN npm run build

# Stage 2: Build backend
FROM eclipse-temurin:21-jdk AS backend-builder
WORKDIR /app

# Copy gradle wrapper and build files
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./

# Make gradlew executable
RUN chmod +x gradlew

# Download dependencies (cached layer)
RUN ./gradlew dependencies --no-daemon || true

# Copy source and build
COPY src ./src
RUN ./gradlew assemble --no-daemon -x test

# Stage 3: Runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy backend JAR
COPY --from=backend-builder /app/build/libs/*.jar ./app.jar

# Copy built frontend
COPY --from=frontend-builder /app/frontend/build ./static

# Expose port
EXPOSE 8080

# Run application
ENTRYPOINT ["java", "-jar", "app.jar"]
