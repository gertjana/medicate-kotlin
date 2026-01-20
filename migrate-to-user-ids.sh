#!/bin/bash

# Migration script to convert from username-based keys to ID-based keys
# This script migrates user data from the old structure to the new structure
# All migrated data is prefixed with 'medicate:' to keep it separated

# Usage: ./migrate-to-user-ids.sh [environment]
# Example: ./migrate-to-user-ids.sh dev

ENVIRONMENT=${1:-production}
REDIS_HOST=${REDIS_HOST:-localhost}
REDIS_PORT=${REDIS_PORT:-6379}
MEDICATE_PREFIX="medicate"

echo "========================================"
echo "User ID Migration Script"
echo "========================================"
echo "Environment: $ENVIRONMENT"
echo "Redis Host: $REDIS_HOST:$REDIS_PORT"
echo "Medicate Prefix: $MEDICATE_PREFIX"
echo ""

# Check if redis-cli is available
if ! command -v redis-cli &> /dev/null; then
    echo "ERROR: redis-cli not found. Please install redis-cli."
    exit 1
fi

echo "Step 1: Scanning for existing users..."
echo ""

# Get all user keys from old structure
USER_KEYS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "$ENVIRONMENT:user:*" | grep -E "^$ENVIRONMENT:user:[^:]+$")

if [ -z "$USER_KEYS" ]; then
    echo "No users found to migrate."
    exit 0
fi

USER_COUNT=$(echo "$USER_KEYS" | wc -l | tr -d ' ')
echo "Found $USER_COUNT users to migrate"
echo ""

MIGRATED=0
FAILED=0

# Process each user
echo "Step 2: Migrating users..."
echo ""

while IFS= read -r key; do
    # Extract username from key
    USERNAME=$(echo "$key" | sed "s/^$ENVIRONMENT:user://")

    # Skip keys that are not direct user keys (they might be sub-keys)
    if echo "$key" | grep -q ":"; then
        if ! echo "$key" | grep -qE "^$ENVIRONMENT:user:[^:]+$"; then
            continue
        fi
    fi

    echo "Migrating user: $USERNAME"

    # Get user data
    USER_JSON=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT GET "$key")

    if [ -z "$USER_JSON" ] || [ "$USER_JSON" = "(nil)" ]; then
        echo "  ERROR: Could not fetch user data for $USERNAME"
        FAILED=$((FAILED + 1))
        continue
    fi

    echo "  Original JSON: $USER_JSON"

    # Generate a new UUID for this user
    if command -v uuidgen &> /dev/null; then
        USER_ID=$(uuidgen | tr '[:upper:]' '[:lower:]')
    else
        # Fallback: use Python to generate UUID
        USER_ID=$(python3 -c "import uuid; print(str(uuid.uuid4()))")
    fi

    echo "  Generated ID: $USER_ID"

    # Extract email from user JSON (if it exists)
    EMAIL=$(echo "$USER_JSON" | grep -o '"email":"[^"]*"' | cut -d'"' -f4 | tr '[:upper:]' '[:lower:]')

    # Add UUID to user JSON using Python for proper JSON manipulation
    # Pass JSON via stdin to avoid quote escaping issues
    UPDATED_JSON=$(echo "$USER_JSON" | python3 -c "
import json
import sys

try:
    user_data = json.loads(sys.stdin.read())
    user_data['id'] = '$USER_ID'
    print(json.dumps(user_data))
except Exception as e:
    print('ERROR: ' + str(e), file=sys.stderr)
    sys.exit(1)
")

    if [ $? -ne 0 ]; then
        echo "  ERROR: Failed to parse/update JSON for $USERNAME"
        FAILED=$((FAILED + 1))
        continue
    fi

    echo "  Updated JSON: $UPDATED_JSON"

    # Start Redis transaction
    redis-cli -h $REDIS_HOST -p $REDIS_PORT MULTI > /dev/null

    # Set new user data with ID (using medicate prefix)
    redis-cli -h $REDIS_HOST -p $REDIS_PORT SET "$MEDICATE_PREFIX:$ENVIRONMENT:user:id:$USER_ID" "$UPDATED_JSON" > /dev/null

    # Create username index (using medicate prefix)
    redis-cli -h $REDIS_HOST -p $REDIS_PORT SET "$MEDICATE_PREFIX:$ENVIRONMENT:user:username:$USERNAME" "$USER_ID" > /dev/null

    # Create email index if email exists (using medicate prefix)
    if [ -n "$EMAIL" ] && [ "$EMAIL" != "" ]; then
        echo "  Creating email index: $EMAIL"
        redis-cli -h $REDIS_HOST -p $REDIS_PORT SET "$MEDICATE_PREFIX:$ENVIRONMENT:user:email:$EMAIL" "$USER_ID" > /dev/null
    fi

    # Execute transaction
    EXEC_RESULT=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT EXEC)

    # EXEC returns an array of results - check if it's not empty and not an error
    if [ -n "$EXEC_RESULT" ] && ! echo "$EXEC_RESULT" | grep -qE "(error|Error|ERROR)"; then
        echo "  SUCCESS: User migrated"
        echo "  Transaction result: $EXEC_RESULT"

        # Now migrate all related data (medicine, schedule, dosagehistory)
        echo "  Migrating related data for user $USERNAME..."

        # Migrate medicines
        MEDICINE_KEYS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "$ENVIRONMENT:user:$USERNAME:medicine:*")
        if [ -n "$MEDICINE_KEYS" ]; then
            MEDICINE_COUNT=$(echo "$MEDICINE_KEYS" | wc -l | tr -d ' ')
            echo "    Found $MEDICINE_COUNT medicines to migrate"
            while IFS= read -r med_key; do
                if [ -n "$med_key" ]; then
                    MED_DATA=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT GET "$med_key")
                    # Extract medicine ID from key
                    MED_ID=$(echo "$med_key" | sed "s/^$ENVIRONMENT:user:$USERNAME:medicine://")
                    NEW_MED_KEY="$MEDICATE_PREFIX:$ENVIRONMENT:user:$USER_ID:medicine:$MED_ID"
                    redis-cli -h $REDIS_HOST -p $REDIS_PORT SET "$NEW_MED_KEY" "$MED_DATA" > /dev/null
                    echo "      Migrated medicine: $MED_ID"
                fi
            done <<< "$MEDICINE_KEYS"
        fi

        # Migrate schedules
        SCHEDULE_KEYS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "$ENVIRONMENT:user:$USERNAME:schedule:*")
        if [ -n "$SCHEDULE_KEYS" ]; then
            SCHEDULE_COUNT=$(echo "$SCHEDULE_KEYS" | wc -l | tr -d ' ')
            echo "    Found $SCHEDULE_COUNT schedules to migrate"
            while IFS= read -r sched_key; do
                if [ -n "$sched_key" ]; then
                    SCHED_DATA=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT GET "$sched_key")
                    # Extract schedule ID from key
                    SCHED_ID=$(echo "$sched_key" | sed "s/^$ENVIRONMENT:user:$USERNAME:schedule://")
                    NEW_SCHED_KEY="$MEDICATE_PREFIX:$ENVIRONMENT:user:$USER_ID:schedule:$SCHED_ID"
                    redis-cli -h $REDIS_HOST -p $REDIS_PORT SET "$NEW_SCHED_KEY" "$SCHED_DATA" > /dev/null
                    echo "      Migrated schedule: $SCHED_ID"
                fi
            done <<< "$SCHEDULE_KEYS"
        fi

        # Migrate dosage history
        HISTORY_KEYS=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS "$ENVIRONMENT:user:$USERNAME:dosagehistory:*")
        if [ -n "$HISTORY_KEYS" ]; then
            HISTORY_COUNT=$(echo "$HISTORY_KEYS" | wc -l | tr -d ' ')
            echo "    Found $HISTORY_COUNT dosage history entries to migrate"
            while IFS= read -r hist_key; do
                if [ -n "$hist_key" ]; then
                    HIST_DATA=$(redis-cli -h $REDIS_HOST -p $REDIS_PORT GET "$hist_key")
                    # Extract history ID from key
                    HIST_ID=$(echo "$hist_key" | sed "s/^$ENVIRONMENT:user:$USERNAME:dosagehistory://")
                    NEW_HIST_KEY="$MEDICATE_PREFIX:$ENVIRONMENT:user:$USER_ID:dosagehistory:$HIST_ID"
                    redis-cli -h $REDIS_HOST -p $REDIS_PORT SET "$NEW_HIST_KEY" "$HIST_DATA" > /dev/null
                    echo "      Migrated dosage history: $HIST_ID"
                fi
            done <<< "$HISTORY_KEYS"
        fi

        MIGRATED=$((MIGRATED + 1))

        # Optionally delete old key (commented out for safety - do this manually after verification)
        # redis-cli -h $REDIS_HOST -p $REDIS_PORT DEL "$key" > /dev/null
        # echo "  Deleted old key: $key"
    else
        echo "  ERROR: Failed to migrate user"
        echo "  Transaction result: $EXEC_RESULT"
        FAILED=$((FAILED + 1))
    fi

    echo ""
done <<< "$USER_KEYS"

echo "========================================"
echo "Migration Summary"
echo "========================================"
echo "Total users found: $USER_COUNT"
echo "Successfully migrated: $MIGRATED"
echo "Failed: $FAILED"
echo ""

if [ $MIGRATED -gt 0 ]; then
    echo "IMPORTANT: Original user keys are preserved."
    echo "All migrated data is prefixed with '$MEDICATE_PREFIX:'"
    echo ""
    echo "You can verify the new structure with:"
    echo "  # User data:"
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$MEDICATE_PREFIX:$ENVIRONMENT:user:id:*\""
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$MEDICATE_PREFIX:$ENVIRONMENT:user:username:*\""
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$MEDICATE_PREFIX:$ENVIRONMENT:user:email:*\""
    echo ""
    echo "  # Related data (replace {userId} with actual UUID):"
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$MEDICATE_PREFIX:$ENVIRONMENT:user:{userId}:medicine:*\""
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$MEDICATE_PREFIX:$ENVIRONMENT:user:{userId}:schedule:*\""
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$MEDICATE_PREFIX:$ENVIRONMENT:user:{userId}:dosagehistory:*\""
    echo ""
    echo "To view a specific migrated user:"
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT GET \"$MEDICATE_PREFIX:$ENVIRONMENT:user:username:<username>\""
    echo "  # Copy the UUID, then:"
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT GET \"$MEDICATE_PREFIX:$ENVIRONMENT:user:id:<uuid>\""
    echo ""
    echo "After verifying migration is successful, you can optionally delete old keys:"
    echo "  redis-cli -h $REDIS_HOST -p $REDIS_PORT KEYS \"$ENVIRONMENT:user:*\" | xargs redis-cli -h $REDIS_HOST -p $REDIS_PORT DEL"
fi

echo ""
echo "Migration complete!"
