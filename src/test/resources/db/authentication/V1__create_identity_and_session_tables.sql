CREATE TABLE user_account (
    id UUID PRIMARY KEY,
    username VARCHAR(32) NOT NULL,
    normalized_username VARCHAR(32) NOT NULL UNIQUE,
    display_name VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    session_version BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_user_account_role CHECK (
        role IN ('REQUESTER', 'TECHNICIAN', 'FACILITIES_MANAGER')
    ),
    CONSTRAINT ck_user_account_username_normalized CHECK (
        normalized_username = LOWER(username)
    )
);

CREATE INDEX ix_user_account_role_active ON user_account (role, active);

-- Authentication-only H2 fixture; real readiness and Requester tests use migrated PostgreSQL.
CREATE VIEW schema_version AS
    SELECT * FROM (VALUES ('1', true), ('2', true), ('3', true), ('4', true), ('5', true)) AS versions(version, success);

CREATE TABLE audit_event (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actor_id UUID NOT NULL REFERENCES user_account(id),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL
);

CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INTEGER NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100),
    CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
);

CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    CONSTRAINT spring_session_attributes_pk PRIMARY KEY (
        session_primary_id,
        attribute_name
    ),
    CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
        REFERENCES spring_session(primary_id) ON DELETE CASCADE
);
