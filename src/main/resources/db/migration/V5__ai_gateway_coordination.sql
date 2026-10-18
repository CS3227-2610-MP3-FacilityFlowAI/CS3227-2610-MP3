-- Iteration 2F: durable lifecycle statuses and owner-fenced global leases.
ALTER TABLE facilityflow.ai_invocation
    DROP CONSTRAINT IF EXISTS ai_invocation_status_check;
ALTER TABLE facilityflow.ai_invocation
    ADD CONSTRAINT ai_invocation_status_check CHECK (status IN (
        'STARTED', 'SUCCEEDED', 'AI_UNAVAILABLE', 'AI_LIMITED',
        'AI_INVALID_REQUEST', 'AI_CONFIGURATION_ERROR', 'AI_TIMEOUT',
        'AI_INVALID_OUTPUT', 'DISCARDED_ACCESS_CHANGED'));

ALTER TABLE facilityflow.rate_limit_bucket
    ADD COLUMN lease_owner uuid;
ALTER TABLE facilityflow.rate_limit_bucket
    ADD CONSTRAINT rate_limit_bucket_lease_owner_check
        CHECK (scope = 'GLOBAL_SLOT' OR lease_owner IS NULL);

GRANT UPDATE (status, latency_ms, retry_count, input_tokens, output_tokens, total_tokens)
    ON facilityflow.ai_invocation TO facilityflow_app;
GRANT UPDATE (lease_owner, lease_expires_at, version, invocation_times)
    ON facilityflow.rate_limit_bucket TO facilityflow_app;
