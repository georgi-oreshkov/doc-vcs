#!/bin/bash
set -e

#ADMINISTRATIVE TASKS
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "postgres" <<-EOSQL
    -- Create App Database and User
    CREATE DATABASE vcs_db;
    CREATE ROLE $APP_USER WITH LOGIN PASSWORD '$APP_PASSWORD';
    
    -- Create Keycloak Database and User
    CREATE DATABASE keycloak;
    CREATE ROLE keycloak WITH LOGIN PASSWORD '$KC_DB_PASSWORD';
EOSQL

#APP SCHEMA TASKS
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "vcs_db" <<-EOSQL
    -- Create the Schema for Isolation
    CREATE SCHEMA IF NOT EXISTS vcs_core;

    -- Assign ownership and search path
    ALTER DATABASE vcs_db OWNER TO $APP_USER;
    ALTER ROLE $APP_USER SET search_path TO vcs_core, public;

    -- Set Permissions
    GRANT ALL PRIVILEGES ON SCHEMA vcs_core TO $APP_USER;
    
    -- Ensure future tables are accessible
    ALTER DEFAULT PRIVILEGES IN SCHEMA vcs_core 
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO $APP_USER;
    
    ALTER DEFAULT PRIVILEGES IN SCHEMA vcs_core 
    GRANT USAGE, SELECT ON SEQUENCES TO $APP_USER;
EOSQL

#KEYCLOAK PERMISSIONS
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "keycloak" <<-EOSQL
    ALTER DATABASE keycloak OWNER TO keycloak;
    GRANT ALL PRIVILEGES ON DATABASE keycloak TO keycloak;
EOSQL