#!/usr/bin/env bash
# Reset pet profile to EGG stage for testing.
# Usage:
#   ./reset-pet.sh              # reset user 1 (default)
#   ./reset-pet.sh 2            # reset a specific user ID

set -euo pipefail

USER_ID="${1:-1}"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-aimon}"
DB_USER="${DB_USER:-aimon}"
export PGPASSWORD="${DB_PASSWORD:-aimon}"

echo "Resetting pet for user_id=$USER_ID → EGG stage (level=1, xp=0) ..."

psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<SQL
UPDATE aimon.pet_profiles
SET
    stage               = 'EGG',
    level               = 1,
    xp                  = 0,
    hunger              = 50,
    energy              = 100,
    happiness           = 80,
    regression_warnings = 0,
    variant_id          = NULL,
    variant_expires_at  = NULL,
    variant_cooldown_at = NULL,
    updated_at          = NOW()
WHERE user_id = $USER_ID;

SELECT user_id, stage, level, xp, hunger, energy, happiness
FROM aimon.pet_profiles
WHERE user_id = $USER_ID;
SQL

echo "Done. Reconnect the frontend to pick up the new state."
