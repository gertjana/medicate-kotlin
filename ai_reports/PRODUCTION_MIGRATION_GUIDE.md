# Production Migration Guide - Docker Environment

## Overview

This guide explains how to run the `migrate-to-user-ids.sh` script in a production environment where the application runs in Docker containers.

## Prerequisites

- Access to the production server (SSH or similar)
- Docker installed on production server
- Redis container running
- Network access between containers

## Migration Options

### Option 1: Run Migration Script Inside Redis Container (Recommended)

This is the simplest approach since the Redis container already has `redis-cli` installed.

#### Steps:

1. **Copy the migration script to the production server:**
   ```bash
   scp migrate-to-user-ids.sh user@production-server:/tmp/
   ```

2. **SSH into the production server:**
   ```bash
   ssh user@production-server
   ```

3. **Find your Redis container name:**
   ```bash
   docker ps | grep redis
   # or if using docker-compose:
   docker-compose ps
   ```

4. **Copy the script into the Redis container:**
   ```bash
   docker cp /tmp/migrate-to-user-ids.sh <redis-container-name>:/tmp/
   ```

5. **Execute the migration inside the Redis container:**
   ```bash
   docker exec -it <redis-container-name> bash
   cd /tmp
   chmod +x migrate-to-user-ids.sh
   ./migrate-to-user-ids.sh prod
   ```

   Or as a one-liner:
   ```bash
   docker exec -it <redis-container-name> bash -c "cd /tmp && chmod +x migrate-to-user-ids.sh && ./migrate-to-user-ids.sh prod"
   ```

6. **Verify the migration:**
   ```bash
   docker exec -it <redis-container-name> redis-cli KEYS "medicate:prod:user:*"
   ```

---

### Option 2: Run from Application Container

If your application container has `redis-cli` installed, you can run it from there.

#### Steps:

1. **Copy script to production server:**
   ```bash
   scp migrate-to-user-ids.sh user@production-server:/tmp/
   ```

2. **Find your application container name:**
   ```bash
   docker ps | grep kotlin-ktor
   # or check your docker-compose.yml
   ```

3. **Copy script into application container:**
   ```bash
   docker cp /tmp/migrate-to-user-ids.sh <app-container-name>:/tmp/
   ```

4. **Install redis-cli if needed (inside container):**
   ```bash
   docker exec -it <app-container-name> bash
   apt-get update && apt-get install -y redis-tools
   ```

5. **Run the migration with Redis connection settings:**
   ```bash
   docker exec -it <app-container-name> bash -c \
     "cd /tmp && REDIS_HOST=redis REDIS_PORT=6379 ./migrate-to-user-ids.sh prod"
   ```

   Note: Replace `redis` with your actual Redis service name from docker-compose.yml

---

### Option 3: Run from Host Machine with Docker Network

If you have `redis-cli` on the host machine, you can connect to Redis through Docker networking.

#### Steps:

1. **Find Redis container IP or use port forwarding:**
   ```bash
   # Get Redis container IP
   docker inspect <redis-container-name> | grep IPAddress

   # Or check if Redis port is exposed
   docker ps | grep redis
   ```

2. **If Redis port is exposed (e.g., 6379:6379):**
   ```bash
   cd /path/to/script
   ./migrate-to-user-ids.sh prod
   ```

3. **If Redis port is NOT exposed, create a temporary port forward:**
   ```bash
   # In one terminal:
   docker port <redis-container-name>

   # Or expose temporarily (not recommended in production):
   docker run -it --rm --network <docker-network> --link <redis-container-name>:redis redis:latest redis-cli -h redis
   ```

---

### Option 4: Run via Docker Compose Exec (Recommended for docker-compose setups)

If you're using docker-compose:

#### Steps:

1. **Copy script to production server:**
   ```bash
   scp migrate-to-user-ids.sh user@production-server:/tmp/
   ```

2. **Navigate to your docker-compose directory on production:**
   ```bash
   cd /path/to/docker-compose
   ```

3. **Copy script into Redis service:**
   ```bash
   docker-compose cp /tmp/migrate-to-user-ids.sh redis:/tmp/
   ```

4. **Execute migration:**
   ```bash
   docker-compose exec redis bash -c "cd /tmp && chmod +x migrate-to-user-ids.sh && ./migrate-to-user-ids.sh prod"
   ```

5. **Verify migration:**
   ```bash
   docker-compose exec redis redis-cli KEYS "medicate:prod:user:*" | head -20
   ```

---

## Important Production Considerations

### 1. Backup Before Migration

**CRITICAL**: Always backup your Redis data before running the migration!

```bash
# Inside Redis container or via docker exec:
docker exec <redis-container-name> redis-cli SAVE

# Or from docker-compose:
docker-compose exec redis redis-cli SAVE

# Copy the backup file to a safe location:
docker cp <redis-container-name>:/data/dump.rdb /backup/redis-backup-$(date +%Y%m%d-%H%M%S).rdb
```

### 2. Environment Variables

The script uses these environment variables:
- `REDIS_HOST` (default: localhost)
- `REDIS_PORT` (default: 6379)

Set them appropriately for your Docker setup:
```bash
# Example for docker-compose with service name 'redis':
REDIS_HOST=redis REDIS_PORT=6379 ./migrate-to-user-ids.sh prod
```

### 3. Stop Application During Migration

To prevent data inconsistencies, stop the application before migrating:

```bash
# If using docker-compose:
docker-compose stop backend

# Run migration
docker-compose exec redis bash -c "cd /tmp && ./migrate-to-user-ids.sh prod"

# Update application to use new code
docker-compose pull backend
docker-compose up -d backend
```

### 4. Verify Migration Success

After migration, verify the data:

```bash
# Check user count
docker exec <redis-container-name> redis-cli KEYS "medicate:prod:user:id:*" | wc -l

# Check a specific user
docker exec <redis-container-name> redis-cli GET "medicate:prod:user:username:yourusername"
# Copy the UUID from output

# Check user data
docker exec <redis-container-name> redis-cli GET "medicate:prod:user:id:<paste-uuid-here>"

# Check medicines for a user
docker exec <redis-container-name> redis-cli KEYS "medicate:prod:user:<uuid>:medicine:*"
```

### 5. Deploy Updated Application

After successful migration, deploy the updated application code:

```bash
# Pull latest image
docker-compose pull backend

# Restart with new code
docker-compose up -d backend

# Check logs
docker-compose logs -f backend
```

### 6. Cleanup Old Data (AFTER VERIFICATION!)

Only after thoroughly testing the new system, clean up old keys:

```bash
docker exec <redis-container-name> redis-cli --scan --pattern "prod:user:*" | \
  xargs -L 1 docker exec <redis-container-name> redis-cli DEL

# Or via script in container:
docker exec <redis-container-name> bash -c \
  'redis-cli KEYS "prod:user:*" | xargs redis-cli DEL'
```

---

## Example: Complete Production Migration on render.com

**Important**: render.com doesn't provide SSH access. You must use the web shell in the dashboard.

### Method 1: Using Web Shell (Recommended)

1. **Open Web Shell:**
   - Go to render.com dashboard
   - Navigate to your web service (backend)
   - Click on "Shell" tab
   - This opens a terminal in your running container

2. **Check if redis-cli is available:**
   ```bash
   which redis-cli
   ```

   If not found, install it:
   ```bash
   apt-get update && apt-get install -y redis-tools
   ```

3. **Verify Redis connection:**
   ```bash
   echo $REDIS_HOST
   echo $REDIS_PORT
   redis-cli -h $REDIS_HOST -p $REDIS_PORT PING
   ```
   Should return: `PONG`

4. **Create the migration script in the container:**
   Since you can't upload files, you need to create the script directly:

   ```bash
   cat > /tmp/migrate-to-user-ids.sh << 'ENDOFSCRIPT'
   #!/bin/bash

   # [Copy the ENTIRE content of migrate-to-user-ids.sh here]
   # You can find this in your local project at:
   # /Users/gertjan/Projects/kotlin-ktor-arrow/migrate-to-user-ids.sh

   ENDOFSCRIPT

   chmod +x /tmp/migrate-to-user-ids.sh
   ```

   **Tip**: Copy the entire script from your local machine and paste it in the web shell between the `<<` markers.

5. **Run the migration:**
   ```bash
   cd /tmp
   ./migrate-to-user-ids.sh prod
   ```

6. **Verify the migration:**
   ```bash
   redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "medicate:prod:user:username:*" | wc -l
   redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "medicate:prod:user:id:*" | wc -l
   ```
   Both commands should show the same number (number of users).

7. **Deploy updated code:**
   - Commit and push your updated code to Git
   - render.com will automatically detect and deploy the new version

### Method 2: Using Python Script (Alternative)

If creating the bash script is difficult in the web shell, you can use this Python one-liner approach:

1. **Open Web Shell in render.com dashboard**

2. **Ensure Redis connection:**
   ```bash
   apt-get update && apt-get install -y redis-tools python3
   ```

3. **Run Python migration directly:**
   ```bash
   python3 << 'ENDPYTHON'
   import redis
   import json
   import os
   from uuid import uuid4

   # Connect to Redis
   r = redis.Redis(
       host=os.getenv('REDIS_HOST', 'localhost'),
       port=int(os.getenv('REDIS_PORT', 6379)),
       decode_responses=True
   )

   env = 'prod'
   prefix = 'medicate'

   print("Starting migration...")

   # Get all users
   old_pattern = f"{env}:user:*"
   exclude_patterns = [':medicine:', ':schedule:', ':dosagehistory:', ':token:']

   user_keys = []
   for key in r.scan_iter(match=old_pattern):
       if not any(pattern in key for pattern in exclude_patterns):
           user_keys.append(key)

   print(f"Found {len(user_keys)} users to migrate")

   for user_key in user_keys:
       # Extract username from key
       username = user_key.split(':')[-1]

       # Get user data
       user_json = r.get(user_key)
       if not user_json:
           continue

       user_data = json.loads(user_json)

       # Generate UUID if not exists
       if 'id' not in user_data:
           user_data['id'] = str(uuid4())

       user_id = user_data['id']

       # Create new keys
       username_index_key = f"{prefix}:{env}:user:username:{username}"
       user_id_key = f"{prefix}:{env}:user:id:{user_id}"
       email_index_key = f"{prefix}:{env}:user:email:{user_data.get('email', '').lower()}"

       # Store in new structure
       r.set(username_index_key, user_id)
       r.set(user_id_key, json.dumps(user_data))
       if user_data.get('email'):
           r.set(email_index_key, user_id)

       # Migrate related data (medicines, schedules, etc.)
       for data_type in ['medicine', 'schedule', 'dosagehistory']:
           old_pattern = f"{env}:user:{username}:{data_type}:*"
           for old_key in r.scan_iter(match=old_pattern):
               item_id = old_key.split(':')[-1]
               new_key = f"{prefix}:{env}:user:{user_id}:{data_type}:{item_id}"
               data = r.get(old_key)
               if data:
                   r.set(new_key, data)

       print(f"Migrated user: {username} -> {user_id}")

   print("Migration complete!")
   ENDPYTHON
   ```

### Method 3: Add Migration to Your Application (Best for Production)

Instead of running a one-off script, add migration logic to your application startup:

1. **Create a migration function in your Kotlin app** (add to Application.kt):
   ```kotlin
   suspend fun runMigrationIfNeeded(redisService: RedisService) {
       val migrationKey = "medicate:prod:migration:user-ids-complete"

       // Check if migration already ran
       redisService.get(migrationKey).fold(
           { /* Migration needed */ },
           { migrationDone ->
               if (migrationDone == "true") {
                   log.info("Migration already completed, skipping")
                   return
               }
           }
       )

       log.info("Starting user ID migration...")
       // Add migration logic here
       // ...

       redisService.set(migrationKey, "true")
       log.info("Migration completed successfully")
   }
   ```

2. **Deploy the application with migration code**

3. **The migration runs automatically on first startup**

4. **Deploy again without the migration code once confirmed**

### Important Notes for render.com:

1. **No Persistent Shell Access**: The web shell is ephemeral. Each time you open it, you get a fresh container.

2. **Use Environment Variables**: Redis connection details are available as environment variables:
   - `$REDIS_HOST`
   - `$REDIS_PORT`
   - No password needed if using render.com managed Redis

3. **Backup Strategy**: render.com Redis doesn't provide direct backup access. Consider:
   - Using Redis BGSAVE before migration
   - Exporting data to a dump file
   - Testing migration thoroughly in a staging environment first

4. **Zero Downtime**: To minimize downtime:
   - The migration script preserves old data
   - Deploy new code that can read both old and new formats
   - Verify everything works
   - Clean up old data later

### Quick Start for render.com:

```bash
# 1. Open Shell in render.com dashboard

# 2. Install redis-cli
apt-get update && apt-get install -y redis-tools

# 3. Test connection
redis-cli -h $REDIS_HOST -p $REDIS_PORT PING

# 4. Create and run migration script
# (paste the entire migrate-to-user-ids.sh content)
cat > /tmp/migrate.sh << 'EOF'
[paste script here]
EOF
chmod +x /tmp/migrate.sh
./tmp/migrate.sh prod

# 5. Verify
redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "medicate:prod:user:*" | head

# 6. Deploy new code via Git push
```

---

## Troubleshooting

### Script can't find redis-cli

```bash
# Install in Debian/Ubuntu containers:
apt-get update && apt-get install -y redis-tools

# Install in Alpine containers:
apk add redis
```

### Can't connect to Redis from container

Check Docker network:
```bash
docker network ls
docker network inspect <network-name>
```

Ensure containers are on the same network, or use the service name from docker-compose.yml.

### Permission denied

```bash
chmod +x migrate-to-user-ids.sh
```

### Script hangs or times out

Redis might be on a different host/port. Check environment:
```bash
env | grep REDIS
```

---

## Summary

**Recommended approach for production:**

1. Backup Redis data
2. Stop backend application
3. Copy migration script to Redis container
4. Run migration inside Redis container
5. Verify migration success
6. Deploy updated application code
7. Test thoroughly
8. Clean up old data after confirmation

This ensures minimal downtime and maximum safety during the migration.
