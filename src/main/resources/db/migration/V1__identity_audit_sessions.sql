-- REQ-SH-001–007 / TECHNICAL-SPEC: identity and audit foundation; no seeded accounts.
-- Flyway creates/owns the private facilityflow schema as facilityflow_migrator.
REVOKE ALL ON SCHEMA facilityflow FROM PUBLIC;
GRANT USAGE ON SCHEMA facilityflow TO facilityflow_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA facilityflow REVOKE ALL ON TABLES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA facilityflow REVOKE ALL ON SEQUENCES FROM PUBLIC;
ALTER DEFAULT PRIVILEGES IN SCHEMA facilityflow REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE TABLE facilityflow.user_account (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username text NOT NULL CHECK (username ~ '^[A-Za-z0-9._-]{3,32}$'),
    display_name text NOT NULL CHECK (
        char_length(display_name) BETWEEN 2 AND 80
        AND display_name = btrim(display_name)
        AND display_name ~ '[^[:space:]]'
    ),
    password_hash text NOT NULL CHECK (char_length(password_hash) > 0),
    role text NOT NULL CHECK (role IN ('REQUESTER', 'TECHNICIAN', 'FACILITIES_MANAGER')),
    active boolean NOT NULL DEFAULT true,
    session_version bigint NOT NULL DEFAULT 0 CHECK (session_version >= 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at)
);
CREATE UNIQUE INDEX user_account_username_unique ON facilityflow.user_account (lower(username));

CREATE TABLE facilityflow.audit_event (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    action text NOT NULL CHECK (char_length(action) BETWEEN 1 AND 100),
    target_type text NOT NULL CHECK (char_length(target_type) BETWEEN 1 AND 50),
    target_id uuid NOT NULL,
    details jsonb NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(details) = 'object')
);
CREATE INDEX audit_event_actor_time_idx ON facilityflow.audit_event (actor_id, occurred_at);
CREATE INDEX audit_event_target_time_idx ON facilityflow.audit_event (target_type, target_id, occurred_at);
CREATE INDEX audit_event_time_idx ON facilityflow.audit_event (occurred_at);

-- Adapted from Spring Session JDBC 4.1.1 schema-postgresql.sql, Apache-2.0.
-- REQ-SH-004–005: storage only; session behavior is implemented in the auth slice.
CREATE TABLE facilityflow.spring_session (
    primary_id char(36) NOT NULL PRIMARY KEY,
    session_id char(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name varchar(100)
);
CREATE UNIQUE INDEX spring_session_ix1 ON facilityflow.spring_session (session_id);
CREATE INDEX spring_session_ix2 ON facilityflow.spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON facilityflow.spring_session (principal_name);
CREATE TABLE facilityflow.spring_session_attributes (
    session_primary_id char(36) NOT NULL REFERENCES facilityflow.spring_session(primary_id) ON DELETE CASCADE,
    attribute_name varchar(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);

-- Private schema plus role policies; JDBC role is a trusted server, not a role user.
-- Object-level Requester/Technician authorization still belongs in the services.
ALTER TABLE facilityflow.user_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.audit_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.spring_session ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.spring_session_attributes ENABLE ROW LEVEL SECURITY;
CREATE POLICY server_accounts ON facilityflow.user_account TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_audit_read ON facilityflow.audit_event FOR SELECT TO facilityflow_app USING (true);
CREATE POLICY server_audit_append ON facilityflow.audit_event FOR INSERT TO facilityflow_app WITH CHECK (true);
CREATE POLICY server_sessions ON facilityflow.spring_session TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_session_attributes ON facilityflow.spring_session_attributes TO facilityflow_app USING (true) WITH CHECK (true);

GRANT SELECT, INSERT ON facilityflow.user_account TO facilityflow_app;
GRANT UPDATE (username, display_name, password_hash, role, active, session_version, version, updated_at)
    ON facilityflow.user_account TO facilityflow_app;
GRANT SELECT, INSERT ON facilityflow.audit_event TO facilityflow_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON facilityflow.spring_session, facilityflow.spring_session_attributes TO facilityflow_app;
-- No default grants on future tables. Each migration explicitly grants its needs.
