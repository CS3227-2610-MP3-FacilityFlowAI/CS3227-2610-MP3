-- REQ-RQ-001–011, REQ-TECH-001–011, REQ-MGR-001–019: shared storage prerequisites.
-- Human role/object/state authorization remains in deterministic use-case services.
CREATE TABLE facilityflow.category (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (char_length(name) BETWEEN 2 AND 50 AND name = btrim(name) AND name ~ '[^[:space:]]'),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at)
);
CREATE UNIQUE INDEX category_name_unique ON facilityflow.category (lower(name));
INSERT INTO facilityflow.category (name) VALUES
    ('Electrical'), ('Plumbing'), ('HVAC'), ('Structural'), ('Cleaning'), ('Safety'), ('Other');

CREATE TABLE facilityflow.maintenance_request (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    display_number integer GENERATED ALWAYS AS IDENTITY
        (SEQUENCE NAME facilityflow.request_display_number_seq MINVALUE 1 MAXVALUE 999999 NO CYCLE) UNIQUE,
    display_id text GENERATED ALWAYS AS ('FF-' || lpad(display_number::text, 6, '0')) STORED UNIQUE,
    requester_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    recorded_by uuid NOT NULL REFERENCES facilityflow.user_account(id),
    title text NOT NULL CHECK (char_length(title) BETWEEN 5 AND 100 AND title = btrim(title)),
    description text NOT NULL CHECK (char_length(description) BETWEEN 10 AND 2000 AND description = btrim(description)),
    location text NOT NULL CHECK (char_length(location) BETWEEN 2 AND 120 AND location = btrim(location)),
    category_id uuid NOT NULL REFERENCES facilityflow.category(id),
    reported_urgency text NOT NULL CHECK (reported_urgency IN ('LOW', 'NORMAL', 'HIGH', 'EMERGENCY')),
    manager_priority text CHECK (manager_priority IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
    status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED', 'CANCELLED')),
    assigned_technician_id uuid REFERENCES facilityflow.user_account(id),
    assigned_at timestamptz,
    resolution_summary text CHECK (char_length(resolution_summary) BETWEEN 10 AND 2000 AND resolution_summary = btrim(resolution_summary)),
    completed_by uuid REFERENCES facilityflow.user_account(id),
    completed_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at),
    CHECK (status <> 'OPEN' OR (assigned_technician_id IS NULL AND assigned_at IS NULL)),
    CHECK (status NOT IN ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED', 'CLOSED')
        OR (assigned_technician_id IS NOT NULL AND manager_priority IS NOT NULL)),
    CHECK ((resolution_summary IS NULL AND completed_by IS NULL AND completed_at IS NULL)
        OR (resolution_summary IS NOT NULL AND completed_by IS NOT NULL AND completed_at IS NOT NULL)),
    CHECK (status NOT IN ('COMPLETED', 'CLOSED') OR resolution_summary IS NOT NULL)
);
CREATE INDEX maintenance_request_owner_created_idx ON facilityflow.maintenance_request (requester_id, created_at DESC, display_id);
CREATE INDEX maintenance_request_assignee_status_idx ON facilityflow.maintenance_request (assigned_technician_id, status);
CREATE INDEX maintenance_request_category_idx ON facilityflow.maintenance_request (category_id);
CREATE INDEX maintenance_request_recorded_by_idx ON facilityflow.maintenance_request (recorded_by);
CREATE INDEX maintenance_request_completed_by_idx ON facilityflow.maintenance_request (completed_by);
CREATE INDEX maintenance_request_status_updated_idx ON facilityflow.maintenance_request (status, updated_at);

CREATE TABLE facilityflow.requester_update (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL REFERENCES facilityflow.maintenance_request(id),
    author_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    note text NOT NULL CHECK (char_length(note) BETWEEN 1 AND 1000 AND note = btrim(note)),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX requester_update_request_time_idx ON facilityflow.requester_update (request_id, created_at, id);
CREATE INDEX requester_update_author_idx ON facilityflow.requester_update (author_id);

CREATE TABLE facilityflow.work_log (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL REFERENCES facilityflow.maintenance_request(id),
    author_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    note text NOT NULL CHECK (char_length(note) BETWEEN 1 AND 1000 AND note = btrim(note)),
    minutes integer NOT NULL CHECK (minutes BETWEEN 1 AND 1440),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX work_log_request_time_idx ON facilityflow.work_log (request_id, created_at, id);
CREATE INDEX work_log_author_idx ON facilityflow.work_log (author_id);

CREATE TABLE facilityflow.request_completion_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid NOT NULL REFERENCES facilityflow.maintenance_request(id),
    technician_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    resolution_summary text NOT NULL CHECK (char_length(resolution_summary) BETWEEN 10 AND 2000 AND resolution_summary = btrim(resolution_summary)),
    completed_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX completion_history_request_time_idx ON facilityflow.request_completion_history (request_id, completed_at, id);
CREATE INDEX completion_history_technician_idx ON facilityflow.request_completion_history (technician_id);

ALTER TABLE facilityflow.category ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.maintenance_request ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.requester_update ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.work_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.request_completion_history ENABLE ROW LEVEL SECURITY;
CREATE POLICY server_categories ON facilityflow.category TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_requests ON facilityflow.maintenance_request TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_requester_updates ON facilityflow.requester_update TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_work_logs ON facilityflow.work_log TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_completion_history ON facilityflow.request_completion_history TO facilityflow_app USING (true) WITH CHECK (true);

GRANT SELECT ON facilityflow.category, facilityflow.maintenance_request, facilityflow.requester_update,
    facilityflow.work_log, facilityflow.request_completion_history TO facilityflow_app;
GRANT INSERT (name) ON facilityflow.category TO facilityflow_app;
GRANT UPDATE (name, active, version, updated_at) ON facilityflow.category TO facilityflow_app;
GRANT INSERT (requester_id, recorded_by, title, description, location, category_id, reported_urgency)
    ON facilityflow.maintenance_request TO facilityflow_app;
GRANT UPDATE (title, description, location, category_id, reported_urgency, manager_priority, status,
    assigned_technician_id, assigned_at, resolution_summary, completed_by, completed_at, version, updated_at)
    ON facilityflow.maintenance_request TO facilityflow_app;
GRANT USAGE ON SEQUENCE facilityflow.request_display_number_seq TO facilityflow_app;
GRANT INSERT (request_id, author_id, note) ON facilityflow.requester_update TO facilityflow_app;
GRANT INSERT (request_id, author_id, note, minutes) ON facilityflow.work_log TO facilityflow_app;
GRANT INSERT (request_id, technician_id, resolution_summary) ON facilityflow.request_completion_history TO facilityflow_app;
-- REQ-SH-007 / REQ-TECH-006: no business DELETE; history has no UPDATE grants.
