# Admin User Management - Setup Guide

## Overview

The Medicate application now includes admin functionality that allows privileged users to manage other users in the system. Admins can:

- View all users in the system
- Activate/deactivate user accounts
- Completely delete users (including all their medicines, schedules, and dosage history)

## Security Model

### Admin Privileges

Admin privileges are stored in Redis using a Set data structure:
- Redis key: `medicate:{environment}:admins`
- Contains user IDs (UUIDs) of users with admin privileges
- Admin status is checked on every admin endpoint request (server-side verification)
- JWT tokens include an `isAdmin` claim for convenience, but the backend always validates against Redis

### Access Control

- Admin endpoints are protected by JWT authentication
- Additional admin verification middleware checks both:
  1. JWT `isAdmin` claim
  2. Redis admin set membership
- Non-admin users receive 403 Forbidden when attempting to access admin endpoints

### Safety Features

- Admins cannot deactivate or delete their own accounts
- Attempting self-modification returns 400 Bad Request
- This prevents admins from accidentally locking themselves out

## Granting Admin Privileges

### Using Redis CLI

Admin privileges must be granted manually via Redis CLI. There is no automatic admin account creation.

1. Connect to Redis:
```bash
redis-cli -h localhost -p 6379
```

For Redis Cloud or with authentication:
```bash
redis-cli -h <host> -p <port> -a <password>
```

2. Get the user ID for the user you want to make an admin:
```bash
# Search for user by username
KEYS medicate:production:user:username:*
# Example output: medicate:production:user:username:johndoe

# Get the user data to find their ID
GET medicate:production:user:username:johndoe
# Look for the "id" field in the JSON output
```

3. Add the user ID to the admins set:
```bash
SADD medicate:production:admins "user-uuid-here"
```

Replace `production` with your environment (e.g., `test`, `development`, `production`).

Example with actual UUID:
```bash
SADD medicate:production:admins "123e4567-e89b-12d3-a456-426614174000"
```

4. Verify the admin was added:
```bash
SMEMBERS medicate:production:admins
```

### Environment-Specific Keys

The admin set key depends on the `APP_ENV` environment variable:
- Test: `medicate:test:admins`
- Development: `medicate:development:admins`
- Production: `medicate:production:admins`

## Revoking Admin Privileges

To remove admin privileges from a user:

```bash
SREM medicate:production:admins "user-uuid-here"
```

Verify removal:
```bash
SMEMBERS medicate:production:admins
```

## Admin UI

### Accessing Admin Features

1. Grant admin privileges using Redis CLI (see above)
2. Log out and log back in to get a new JWT token with admin claim
3. Navigate to the Profile page
4. The "Admin - User Management" section appears below your profile

### Admin Actions

The admin interface displays all users in a table with:
- Username, email, full name
- Active/inactive status badge
- Admin role badge (if applicable)
- Action buttons:
  - **Activate**: Makes an inactive user active
  - **Deactivate**: Makes an active user inactive (disabled for yourself)
  - **Delete**: Completely removes user and all data (disabled for yourself)

### User Deletion

When an admin deletes a user, the following data is removed:
- User account and profile
- All medicines owned by the user
- All schedules for the user
- All dosage history for the user
- Admin privileges (if the user was an admin)

This is a permanent operation and cannot be undone.

## API Endpoints

All admin endpoints require JWT authentication and admin privileges.

### GET /api/admin/users

List all users in the system.

**Response:**
```json
[
  {
    "id": "uuid",
    "username": "johndoe",
    "email": "john@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "isActive": true,
    "isAdmin": false,
    "isSelf": false
  }
]
```

### PUT /api/admin/users/{userId}/activate

Activate a user account.

**Response:**
```json
{
  "username": "johndoe",
  "email": "john@example.com",
  "firstName": "John",
  "lastName": "Doe",
  "isAdmin": false
}
```

### PUT /api/admin/users/{userId}/deactivate

Deactivate a user account. Cannot deactivate yourself (400 Bad Request).

**Response:** Same as activate endpoint

### DELETE /api/admin/users/{userId}

Permanently delete a user and all associated data. Cannot delete yourself (400 Bad Request).

**Response:**
```json
{
  "message": "User deleted successfully"
}
```

## Troubleshooting

### Admin section not appearing after granting privileges

1. Verify the user ID is in the admins set:
   ```bash
   SMEMBERS medicate:production:admins
   ```

2. Log out and log back in to refresh the JWT token
   - The `isAdmin` claim is only added during login
   - Existing tokens don't automatically update

3. Check the browser console for errors

### Cannot access admin endpoints (403 Forbidden)

1. Ensure you're logged in with a valid JWT token
2. Verify your user ID is in the Redis admins set
3. Check that you're using the correct environment (test/development/production)
4. Ensure the Redis connection is working

### Trying to deactivate/delete yourself

This is a safety feature and is working as intended. Use a different admin account to manage your account, or:
1. Temporarily grant admin privileges to another user
2. Have them perform the action
3. Revoke their admin privileges if needed

## Best Practices

1. **Limit admin accounts**: Only grant admin privileges to trusted users
2. **Use environment-specific admins**: Don't reuse production admin IDs in test/development
3. **Monitor admin actions**: Check application logs for admin operations
4. **Backup before deletions**: User deletion is permanent and cannot be undone
5. **Regular audits**: Periodically review the list of admins

## Security Considerations

- Admin status is verified server-side on every request
- JWT admin claim is a convenience but never trusted alone
- Admin endpoints use the same JWT authentication as regular endpoints
- Self-modification prevention protects against accidental lockouts
- All admin actions are logged with user IDs for audit trails

## Example Workflow

### Making the first admin

1. Register a new account via the web UI or API
2. Note the username (e.g., "admin")
3. Connect to Redis and find the user:
   ```bash
   GET medicate:production:user:username:admin
   ```
4. Copy the user ID from the JSON response
5. Add to admins set:
   ```bash
   SADD medicate:production:admins "copied-user-id"
   ```
6. Log out and log back in as this user
7. Navigate to Profile page to see admin controls

### Managing users as an admin

1. Log in with an admin account
2. Go to Profile page
3. Scroll to "Admin - User Management" section
4. Use action buttons to activate, deactivate, or delete users
5. Confirm destructive actions in the dialog

## Environment Variables

The admin system uses these environment variables:
- `APP_ENV`: Environment name (test/development/production)
- `REDIS_HOST`: Redis server hostname
- `REDIS_PORT`: Redis server port
- `REDIS_TOKEN`: Redis authentication token (if required)
- `JWT_SECRET`: Secret for signing JWT tokens
