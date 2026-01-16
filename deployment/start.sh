#!/bin/sh
set -e

echo "=== Starting Medicate Application ==="
PORT=80

# Start backend in background with logs to stdout
echo "Starting backend (Ktor)..."
java -jar /app/app.jar 2>&1 | sed 's/^/[BACKEND] /' &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID"

# Start SvelteKit SSR frontend in background with logs to stdout
echo "Starting frontend (SvelteKit SSR)..."
cd /app/frontend
# Set NODE_ENV to production for better performance
export NODE_ENV=production
node build/index.js 2>&1 | sed 's/^/[FRONTEND] /' &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID"
cd /

# Wait for backend to become healthy (max 60s)
echo "Waiting for backend to be ready..."
MAX_WAIT=60
SLEPT=0
while [ $SLEPT -lt $MAX_WAIT ]; do
  if curl -sSf http://127.0.0.1:8080/api/health >/dev/null 2>&1; then
    echo "✓ Backend is up and healthy"
    break
  fi
  sleep 1
  SLEPT=$((SLEPT+1))
done

if [ $SLEPT -ge $MAX_WAIT ]; then
  echo "⚠ Warning: Backend did not become ready within ${MAX_WAIT}s"
  echo "Backend process status:"
  ps aux | grep java || echo "Backend process not found"
else
  echo "✓ Backend became ready after ${SLEPT}s"
fi

# Wait for frontend SSR to be ready (max 60s)
echo "Waiting for frontend to be ready..."
SLEPT=0
while [ $SLEPT -lt $MAX_WAIT ]; do
  if curl -sSf http://127.0.0.1:3000 >/dev/null 2>&1; then
    echo "✓ Frontend SSR is up"
    break
  fi
  sleep 1
  SLEPT=$((SLEPT+1))
done

if [ $SLEPT -ge $MAX_WAIT ]; then
  echo "⚠ Warning: Frontend SSR did not become ready within ${MAX_WAIT}s"
  echo "Frontend process status:"
  ps aux | grep node || echo "Frontend process not found"
  echo "Checking if port 3000 is in use:"
  netstat -tuln | grep 3000 || echo "Port 3000 is not in use"
else
  echo "✓ Frontend SSR became ready after ${SLEPT}s"
fi

# Start nginx (foreground) with logs
echo "=== Starting nginx ==="
echo "Application is ready and serving on port $PORT"
exec nginx -g 'daemon off;'
