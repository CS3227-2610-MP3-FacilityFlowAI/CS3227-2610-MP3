-- REQ-MGR-018–025, REQ-AI-007–010, AI-SEC-008/017–021: storage, not AI actions.
CREATE TABLE facilityflow.skill_tag (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL CHECK (char_length(name) BETWEEN 2 AND 50 AND name = btrim(name) AND name ~ '[^[:space:]]'),
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP CHECK (updated_at >= created_at)
);
CREATE UNIQUE INDEX skill_tag_name_unique ON facilityflow.skill_tag (lower(name));
CREATE TABLE facilityflow.technician_skill (
    technician_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    skill_tag_id uuid NOT NULL REFERENCES facilityflow.skill_tag(id),
    PRIMARY KEY (technician_id, skill_tag_id)
);
CREATE INDEX technician_skill_tag_idx ON facilityflow.technician_skill (skill_tag_id);

-- Deliberately no raw prompt, output, advice or arbitrary metadata columns.
CREATE TABLE facilityflow.ai_invocation (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id uuid NOT NULL REFERENCES facilityflow.user_account(id),
    correlation_id uuid NOT NULL,
    feature text NOT NULL CHECK (feature IN ('SMART_REPORT', 'WORK_PLAN', 'TRIAGE')),
    model text CHECK (char_length(model) > 0),
    template_version text NOT NULL CHECK (char_length(template_version) > 0),
    status text NOT NULL CHECK (status IN ('SUCCEEDED', 'AI_UNAVAILABLE', 'AI_LIMITED',
        'AI_INVALID_REQUEST', 'AI_CONFIGURATION_ERROR', 'AI_TIMEOUT', 'AI_INVALID_OUTPUT')),
    latency_ms bigint NOT NULL CHECK (latency_ms >= 0),
    retry_count smallint NOT NULL CHECK (retry_count BETWEEN 0 AND 1),
    input_tokens bigint CHECK (input_tokens >= 0),
    output_tokens bigint CHECK (output_tokens >= 0),
    total_tokens bigint CHECK (total_tokens >= 0),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ai_invocation_actor_time_idx ON facilityflow.ai_invocation (actor_id, created_at);
CREATE INDEX ai_invocation_feature_time_idx ON facilityflow.ai_invocation (feature, created_at);
CREATE INDEX ai_invocation_correlation_idx ON facilityflow.ai_invocation (correlation_id);

-- Rolling call timestamps and expiring global slot leases, not fixed-window counters.
-- Gateway must lock/CAS these rows, prune timestamps, and renew/release leases.
CREATE TABLE facilityflow.rate_limit_bucket (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    scope text NOT NULL CHECK (scope IN ('ACCOUNT_RATE', 'GLOBAL_SLOT')),
    account_id uuid REFERENCES facilityflow.user_account(id),
    slot smallint CHECK (slot BETWEEN 1 AND 5),
    invocation_times timestamptz[] NOT NULL DEFAULT '{}',
    lease_expires_at timestamptz,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CHECK (array_position(invocation_times, NULL) IS NULL AND cardinality(invocation_times) <= 30),
    CHECK ((scope = 'ACCOUNT_RATE' AND account_id IS NOT NULL AND slot IS NULL AND lease_expires_at IS NULL)
        OR (scope = 'GLOBAL_SLOT' AND account_id IS NULL AND slot IS NOT NULL AND cardinality(invocation_times) = 0))
);
CREATE UNIQUE INDEX rate_limit_account_unique ON facilityflow.rate_limit_bucket (account_id) WHERE scope = 'ACCOUNT_RATE';
CREATE UNIQUE INDEX rate_limit_slot_unique ON facilityflow.rate_limit_bucket (slot) WHERE scope = 'GLOBAL_SLOT';
INSERT INTO facilityflow.rate_limit_bucket (scope, slot) SELECT 'GLOBAL_SLOT', generate_series(1, 5);

ALTER TABLE facilityflow.skill_tag ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.technician_skill ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.ai_invocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE facilityflow.rate_limit_bucket ENABLE ROW LEVEL SECURITY;
CREATE POLICY server_skills ON facilityflow.skill_tag TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_technician_skills ON facilityflow.technician_skill TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_ai_invocations ON facilityflow.ai_invocation TO facilityflow_app USING (true) WITH CHECK (true);
CREATE POLICY server_rate_limits ON facilityflow.rate_limit_bucket TO facilityflow_app USING (true) WITH CHECK (true);
GRANT SELECT ON facilityflow.skill_tag, facilityflow.technician_skill, facilityflow.ai_invocation,
    facilityflow.rate_limit_bucket TO facilityflow_app;
GRANT INSERT (name) ON facilityflow.skill_tag TO facilityflow_app;
GRANT UPDATE (name, active, version, updated_at) ON facilityflow.skill_tag TO facilityflow_app;
GRANT INSERT, DELETE ON facilityflow.technician_skill TO facilityflow_app;
GRANT INSERT (actor_id, correlation_id, feature, model, template_version, status, latency_ms,
    retry_count, input_tokens, output_tokens, total_tokens) ON facilityflow.ai_invocation TO facilityflow_app;
GRANT INSERT (scope, account_id) ON facilityflow.rate_limit_bucket TO facilityflow_app;
GRANT UPDATE (invocation_times, lease_expires_at, version) ON facilityflow.rate_limit_bucket TO facilityflow_app;
-- No deletion of catalogues or invocation evidence; skill links may be removed.
