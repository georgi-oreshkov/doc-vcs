#!/bin/bash
set -e

# This runs inside the database defined by $POSTGRES_DB
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    -- 1. Create the App User
    CREATE ROLE $APP_USER WITH LOGIN PASSWORD '$APP_PASSWORD';

    -- 2. Create the Schema for Isolation
    CREATE SCHEMA IF NOT EXISTS vcs_core;

    -- 3. Set the App User's default path to our schema
    ALTER ROLE $APP_USER SET search_path TO vcs_core, public;

    -- 4. Set Permissions
    GRANT USAGE ON SCHEMA vcs_core TO $APP_USER;
    GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA vcs_core TO $APP_USER;
    GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA vcs_core TO $APP_USER;

    -- 5. Ensure future tables are accessible
    ALTER DEFAULT PRIVILEGES IN SCHEMA vcs_core 
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $APP_USER;
    
    ALTER DEFAULT PRIVILEGES IN SCHEMA vcs_core 
    GRANT USAGE, SELECT ON SEQUENCES TO $APP_USER;
EOSQL