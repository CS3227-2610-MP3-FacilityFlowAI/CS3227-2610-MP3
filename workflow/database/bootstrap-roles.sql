-- NFR-SEC-001: one-time administrative provisioning, no passwords in this file.
-- Run as the Supabase database owner, before Flyway, in a fresh development project.
-- Existing role names cause rollback; do not silently alter unrelated roles.
BEGIN;
CREATE ROLE facilityflow_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE facilityflow_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT facilityflow_migrator TO postgres;
GRANT CONNECT, CREATE ON DATABASE postgres TO facilityflow_migrator;
GRANT CONNECT ON DATABASE postgres TO facilityflow_app;
COMMIT;
