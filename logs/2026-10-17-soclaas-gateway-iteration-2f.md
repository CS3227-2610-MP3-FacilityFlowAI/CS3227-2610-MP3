# SoCLaaS gateway Iteration 2F

Date: 17 October 2026 (Asia/Singapore)

## Scope

Implemented durable distributed admission behind the existing
`AiInvocationCoordinator` seam. PostgreSQL `CURRENT_TIMESTAMP` drives locked
Account rolling windows. Five seeded global slots use a random owner UUID and a
monotonic version fence, with 45-second expiry and 15-second renewal. Release
and renewal require the exact owner/version pair; lease loss withholds a late
successful Work Plan as `AI_UNAVAILABLE`.

Migration `V5__ai_gateway_coordination.sql` adds the lifecycle status values and
lease owner column/grants. Retry execution and durable invocation telemetry are
intentionally deferred to the next increment.

## Verification

- `DatabaseMigrationTest`: isolated PostgreSQL migration, durable admission,
  configured max-in-flight and stale-release cases passed.
- Focused coordinator, Technician and migration tests passed.
- Full `./gradlew.bat test --no-daemon`: `BUILD SUCCESSFUL` (3m28s).
- Fresh Security Reviewer re-review: GO after fixes for durable max-in-flight,
  lease-validity propagation, renewal failure handling and inert startup.

No secrets or raw provider data are recorded here.
