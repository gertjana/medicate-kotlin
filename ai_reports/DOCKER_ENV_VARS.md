# Docker Compose Environment Variables Guide

## How to Pass Environment Variables to Docker Compose Containers

### Method 1: Direct in docker-compose.yml
Best for: Non-sensitive configuration values

```yaml
environment:
  - APP_ENV=production
  - LOG_LEVEL=INFO
```

### Method 2: From Host Environment (Recommended for secrets)
Best for: API keys, passwords, sensitive data

In `docker-compose.yml`:
```yaml
environment:
  - RESEND_API_KEY=${RESEND_API_KEY}
```

**Usage:**
```bash
# Set the variable before running docker-compose
export RESEND_API_KEY=re_your_api_key_here
docker-compose up
```

Or inline:
```bash
RESEND_API_KEY=re_your_api_key_here docker-compose up
```

### Method 3: Using .env File (Most Convenient)
Best for: Local development, keeping secrets out of git

1. Create a `.env` file in the same directory as `docker-compose.yml`:
```bash
cp .env.example .env
```

2. Edit `.env` and add your values:
```
RESEND_API_KEY=re_your_api_key_here
```

3. Docker Compose automatically loads `.env` file:
```bash
docker-compose up
```

**Important:** Add `.env` to `.gitignore` to keep secrets safe!

## Current Project Setup

Your `docker-compose.yml` now supports:
- **RESEND_API_KEY** - Pass from host environment or .env file
- **REDIS_HOST** - Defaults to host.docker.internal
- **REDIS_PORT** - Defaults to 6379
- **APP_ENV** - Defaults to production
- **LOG_LEVEL** - Defaults to INFO

## Quick Start

1. Copy the example file:
   ```bash
   cp .env.example .env
   ```

2. Edit `.env` and set your RESEND_API_KEY:
   ```bash
   nano .env
   # or
   vim .env
   ```

3. Run docker-compose:
   ```bash
   docker-compose up -d
   ```

4. Check if the variable was passed:
   ```bash
   docker-compose exec app env | grep RESEND_API_KEY
   ```

## Troubleshooting

If the environment variable is not being passed:
- Check if `.env` file exists in the same directory as `docker-compose.yml`
- Verify the variable is exported if passing from shell
- Rebuild the container: `docker-compose up --build`
- Check container env: `docker-compose exec app printenv`
