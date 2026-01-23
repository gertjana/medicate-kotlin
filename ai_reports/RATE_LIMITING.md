# Rate Limiting Implementation

## Overview

Implemented rate limiting at the nginx reverse proxy layer to protect authentication endpoints from brute-force attacks and abuse. This provides security before requests even reach the application backend.

## Implementation Details

### Rate Limiting Zones

Defined three separate rate limiting zones in nginx:

1. **auth_limit**: 5 requests per minute per IP
   - Used for login, registration, and token verification
   - Allows small bursts (2 extra requests) for legitimate retry scenarios

2. **reset_limit**: 3 requests per 5 minutes per IP (0.6 requests/min)
   - Very strict limit for password reset requests
   - Prevents email flooding and abuse
   - Allows only 1 burst request

3. **api_limit**: 60 requests per minute per IP
   - General API rate limiting
   - Allows larger bursts (10 requests) for normal application usage

### Protected Endpoints

#### Very Strict (3 per 5 minutes)
- `POST /api/auth/resetPassword` - Password reset email requests

#### Strict (5 per minute)
- `POST /api/user/register` - User registration
- `POST /api/user/login` - User login
- `POST /api/auth/verifyResetToken` - Password reset token verification

#### General (60 per minute)
- All other `/api/` endpoints

### Configuration

**Location**: `/deployment/nginx.conf`

```nginx
# Rate limiting zones
limit_req_zone $binary_remote_addr zone=auth_limit:10m rate=5r/m;
limit_req_zone $binary_remote_addr zone=reset_limit:10m rate=3r/5m;
limit_req_zone $binary_remote_addr zone=api_limit:10m rate=60r/m;

# Applied to specific endpoints
location /api/user/login {
    limit_req zone=auth_limit burst=2 nodelay;
    limit_req_status 429;
    # ... proxy configuration
}
```

### Response Codes

- **429 Too Many Requests**: Returned when rate limit is exceeded
- Includes standard nginx rate limit headers for client information

## Benefits

### Security
- **Brute-force protection**: Attackers cannot try thousands of password combinations
- **Email flood prevention**: Limits password reset email abuse
- **Resource protection**: Prevents DoS attacks on authentication endpoints
- **Early filtering**: Blocks malicious requests at nginx layer before they reach application

### Performance
- **Zero application code**: No backend changes needed
- **Minimal overhead**: Native nginx feature, very efficient
- **Memory efficient**: Uses binary IP addresses for compact storage
- **Shared state**: Works across all nginx worker processes

### User Experience
- **Burst allowance**: Legitimate users can retry a few times quickly
- **Per-IP limiting**: One user's mistakes don't affect others
- **Clear error code**: 429 status is standard and well-understood
- **No impact on normal usage**: Limits are generous for legitimate use

## Rate Limit Reasoning

### Login (5/minute)
- Average user might mistype password 2-3 times
- 5 per minute allows legitimate retries
- Prevents automated brute-force (which needs hundreds of attempts)

### Registration (5/minute)
- Prevents mass account creation
- Normal users only register once
- Allows form resubmission on error

### Password Reset (3 per 5 minutes)
- Strictest limit because it sends emails
- Prevents email flooding
- Normal users only need 1 reset request
- Protects against targeted harassment

### General API (60/minute)
- Allows normal application usage
- Dashboard loads multiple endpoints
- Provides protection against automated scraping

## Testing

### Manual Testing

```bash
# Test login rate limit (should fail after 7 requests)
for i in {1..10}; do
  curl -X POST https://your-domain.com/api/user/login \
    -H "Content-Type: application/json" \
    -d '{"username":"test","password":"test"}' \
    -w "\nStatus: %{http_code}\n"
  sleep 1
done

# Test password reset limit (should fail after 4 requests)
for i in {1..5}; do
  curl -X POST https://your-domain.com/api/auth/resetPassword \
    -H "Content-Type: application/json" \
    -d '{"email":"test@test.com"}' \
    -w "\nStatus: %{http_code}\n"
  sleep 30
done
```

### Expected Behavior

1. First 5 login attempts within a minute: Success (or normal auth response)
2. 6th and 7th attempts (burst): Success
3. 8th+ attempts: 429 Too Many Requests
4. After 1 minute: Rate limit resets, can try again

## Deployment

The rate limiting is automatically applied when:
1. Docker image is rebuilt with updated nginx.conf
2. Container is restarted
3. Nginx configuration is reloaded

```bash
# Rebuild and deploy
docker build -t your-app .
docker compose up -d

# Or reload nginx without rebuilding
docker exec your-container nginx -s reload
```

## Monitoring

Check nginx logs for rate limit events:

```bash
# View rate limit rejections
docker logs your-container 2>&1 | grep "limiting requests"

# Monitor in real-time
docker logs -f your-container 2>&1 | grep "429"
```

## Future Enhancements

Possible improvements:

1. **IP Whitelist**: Exempt trusted IPs (e.g., monitoring services)
2. **Geo-blocking**: Block or further limit specific countries if needed
3. **User-based limiting**: Track limits per user ID instead of just IP
4. **Dynamic limits**: Adjust based on detected attack patterns
5. **Alerts**: Notify admins when limits are frequently hit

## Alternative: Backend Rate Limiting

If you need more sophisticated rate limiting (e.g., per-user instead of per-IP), you could implement it in Ktor using:
- Ktor's `RateLimiting` plugin
- Redis-based rate limiting (shared across instances)
- Custom middleware with token bucket algorithm

However, nginx rate limiting is sufficient for most use cases and provides better performance.

## Notes

- **Shared hosting**: In cloud environments behind load balancers, ensure X-Forwarded-For header is properly set
- **IPv6**: `$binary_remote_addr` handles both IPv4 and IPv6 efficiently
- **Memory**: 10m zone can store ~160,000 IP addresses (sufficient for most applications)
- **State**: Rate limit state is kept in nginx worker shared memory (survives reloads)

## References

- [Nginx Rate Limiting Guide](http://nginx.org/en/docs/http/ngx_http_limit_req_module.html)
- [OWASP: Blocking Brute Force Attacks](https://owasp.org/www-community/controls/Blocking_Brute_Force_Attacks)
