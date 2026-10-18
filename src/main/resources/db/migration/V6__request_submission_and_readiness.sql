-- REQ-RQ-002/011, REQ-SH-010/014: durable duplicate-submission rejection.
CREATE TABLE facilityflow.request_submission (
    requester_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    command_id uuid NOT NULL,
    request_id uuid NOT NULL UNIQUE REFERENCES facilityflow.maintenance_request(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (requester_id, command_id)
);
ALTER TABLE facilityflow.request_submission ENABLE ROW LEVEL SECURITY;
CREATE POLICY server_request_submissions ON facilityflow.request_submission
    TO facilityflow_app USING (true) WITH CHECK (true);
GRANT SELECT, INSERT ON facilityflow.request_submission TO facilityflow_app;

-- TECHNICAL-SPEC: expose only version/readiness, never writable Flyway history.
CREATE VIEW facilityflow.schema_version AS
    SELECT version, success FROM facilityflow.flyway_schema_history WHERE version IS NOT NULL;
GRANT SELECT ON facilityflow.schema_version TO facilityflow_app;
