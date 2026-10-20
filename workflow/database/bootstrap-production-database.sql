-- NFR-DEP-001 / ADR-008: run once as the owner through psql with ON_ERROR_STOP.
-- Run outside a transaction. Existing names must fail; never drop/recreate them.
-- Passwords are assigned separately with masked psql prompts.
CREATE ROLE facilityflow_prod_migrator NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
CREATE ROLE facilityflow_prod_app NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
GRANT facilityflow_prod_migrator TO postgres;
CREATE DATABASE facilityflow_prod OWNER facilityflow_prod_migrator TEMPLATE template0;
REVOKE ALL ON DATABASE facilityflow_prod FROM PUBLIC;
GRANT CONNECT ON DATABASE facilityflow_prod TO facilityflow_prod_app;
