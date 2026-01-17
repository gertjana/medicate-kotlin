# Deployment Fixes for Render.com

## Issues Fixed

### 1. SSR API Calls Using Wrong URLs
**Problem:** All SvelteKit `+server.ts` files were using `localhost:8080` which doesn't work in Docker.
**Solution:** Changed all to `127.0.0.1:8080` (13 files)

### 2. Log Buffering
**Problem:** Backend logs weren't appearing in Render.com due to buffering in sed pipes.
**Solution:** Added `stdbuf` to force unbuffered output and installed `coreutils` package.

### 3. Nginx Debug Logging
**Problem:** Can't see detailed nginx proxy information.
**Solution:** Added debug log format with `$upstream_addr` and `$upstream_status` variables.

### 4. Error Interception
**Problem:** Nginx was intercepting backend errors and returning generic 503.
**Solution:** Set `proxy_intercept_errors off` to pass through actual backend responses.

## Files Changed

1. **frontend/src/routes/api/**/+server.ts** (13 files)
   - Changed `localhost:8080` → `127.0.0.1:8080`

2. **deployment/start.sh**
   - Added `stdbuf -oL -eL` to both backend and frontend commands
   - Forces line-buffered output for real-time logs

3. **Dockerfile**
   - Added `coreutils` package for `stdbuf` command

4. **deployment/nginx.conf**
   - Added debug log format
   - Set `proxy_intercept_errors off`
   - Added detailed error logging

## Testing

### Local Docker Test:
```bash
docker-compose up --build
# Test registration at http://localhost:8000
```

### Verify Build Contains Fixes:
```bash
./test-api-config.sh
# Should show: ✓ Found internal API URL (127.0.0.1:8080)
```

## Deploy to Render.com

```bash
git add .
git commit -m "Fix SSR, logging, and nginx configuration for production"
git push
```

## Expected Render.com Logs

After deployment, you should see:
```
[BACKEND] 12:34:56.789 [main] INFO ktor.application - Application started
[FRONTEND] Listening on port 3000
10.1.2.3 - domain.com [timestamp] "POST /api/user/register HTTP/1.1" 200 123 ... - upstream: 127.0.0.1:8080 upstream_status: 200
```

## Debugging 403 Errors

If still getting 403, check nginx logs for:
- `upstream_status: 403` → Backend is returning 403 (check backend logs)
- `upstream_status: -` → Backend not responding (connection issue)
- No upstream info → Nginx returning 403 directly (permission issue)

## Environment Variables on Render.com

**REQUIRED:**
- `SERVE_STATIC=true` - **CRITICAL!** Disables CORS restrictions. Without this, all API calls will return 403 because CORS only allows localhost:5173.

**Optional:**
- `LOG_LEVEL=DEBUG` - For verbose backend logging
- `APP_URL=https://your-domain.onrender.com` - For password reset emails
