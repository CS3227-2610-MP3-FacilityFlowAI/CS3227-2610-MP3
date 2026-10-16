# SoCLaaS gateway Iteration 2G — durable invocation telemetry

## Scope

Implemented the next gateway increment without changing the workflow specifications. The implementation adds durable `STARTED` and terminal lifecycle telemetry for the Technician Work Plan invocation path.

## Delivered

- Added the provider-neutral `AiInvocationTelemetry` seam with typed feature/status metadata, bounded retry and token fields, and a no-op adapter for direct feature tests.
- Added `JdbcAiInvocationTelemetry` for PostgreSQL `facilityflow.ai_invocation` inserts and exactly-once `STARTED`-to-terminal transitions.
- Wired telemetry into `TechnicianWorkPlanService` after admission and before readiness/context loading.
- Generates a server-side correlation ID and records the configured model plus `work-plan-v1` template version.
- Maps successful, unavailable, limited, configuration, timeout, invalid-request, invalid-output, and access-discarded outcomes to durable terminal statuses.
- Withholds a result and returns `AI_UNAVAILABLE` when terminal telemetry persistence fails; the `STARTED` row remains evidence.
- Emits only a sanitized operator warning containing the server-generated invocation UUID when terminal persistence fails.
- Added unit and isolated PostgreSQL coverage for ordering, start/terminal failures, token/latency metadata, raw-content absence, and duplicate terminal protection.

## Verification

- `./gradlew test --tests 'sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest' --no-daemon` — passed (8 tests).
- `./gradlew test --tests 'sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest' --tests 'sg.edu.nus.facilityflow.database.DatabaseMigrationTest' --no-daemon` — passed (28 tests; database coverage unchanged).
- `./gradlew test --tests 'sg.edu.nus.facilityflow.FacilityFlowApplicationTest' --no-daemon` — passed (Spring wiring check).

No workflow specification or traceability file was changed for this increment, per request.
