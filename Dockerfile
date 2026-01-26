# Multi-stage single-container image: builds frontend & backend, then produces a container with nginx + JRE 21 + Node

# Stage 1: Build frontend SSR (SvelteKit Node)
FROM node:20-alpine AS frontend-builder
WORKDIR /app/frontend

# Copy package files and install dependencies
COPY frontend/package*.json ./
RUN npm ci

# Copy all frontend source files (including updated api.ts)
COPY frontend/ ./

# Add build info for debugging
ARG BUILD_TIME
RUN echo "Frontend built at: ${BUILD_TIME:-unknown}" > build-info.txt

# Build the frontend
RUN npm run build

# Stage 2: Build backend (assemble jar)
FROM eclipse-temurin:21-jdk-alpine AS backend-builder
WORKDIR /app
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true
COPY src ./src
RUN ./gradlew assemble --no-daemon -x test

# Stage 3: Final runtime image - JRE base, Node, and nginx
FROM eclipse-temurin:21-jre-alpine

# Install nginx, nodejs, curl, net-tools (for netstat debugging), and coreutils (for stdbuf unbuffered logging)
RUN apk add --no-cache nginx nodejs npm curl net-tools coreutils

# Create directories
RUN mkdir -p /usr/share/nginx/html /var/run/nginx /app /app/frontend /app/data

# Copy nginx config
COPY deployment/nginx.conf /etc/nginx/nginx.conf

# Copy SSR frontend build and node_modules
COPY --from=frontend-builder /app/frontend/build /app/frontend/build
COPY --from=frontend-builder /app/frontend/node_modules /app/frontend/node_modules
COPY --from=frontend-builder /app/frontend/package.json /app/frontend/package.json

# Copy backend jar
COPY --from=backend-builder /app/build/libs/*.jar /app/app.jar

# Copy medicines database (optional - created by GitHub Actions)
COPY data/medicines.jso[n] /app/data/ || true

# Set default medicines data directory (can be overridden)
ENV MEDICINES_DATA_DIR=/app/data

# Copy entrypoint script
COPY deployment/start.sh /usr/local/bin/start.sh
RUN chmod +x /usr/local/bin/start.sh

EXPOSE 80

CMD ["/usr/local/bin/start.sh"]
