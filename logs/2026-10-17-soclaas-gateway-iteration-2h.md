# SoCLaaS gateway Iteration 2H — bounded provider retry

## Scope

Implemented provider retry and deadline execution without changing workflow specifications.

## Delivered

- Added one automatic retry for network failures and HTTP 503 responses after 250 ms.
- Added one automatic retry for HTTP 429 only when the adapter supplies a valid 0–2 second `Retry-After` and at least five seconds remain in the original deadline.
- Reused the original absolute deadline for every attempt; no retry can extend the request lifetime.
- Preserved cancellation, lease/deadline fencing, and late-result suppression across retry scheduling.
- Carried retry count and provider token usage through the server-side invocation handle for terminal telemetry without exposing operational metadata in the user response.
- Added deterministic provider-core tests for 503 retry, approved/rejected 429 retry, deadline ceiling, and cancellation during the retry window.

## Verification

- `./gradlew test --tests 'sg.edu.nus.facilityflow.ai.provider.SocLlmProviderCoreTest' --no-daemon` — passed.
- Combined provider-core, HTTPS adapter, Technician Work Plan, and PostgreSQL migration tests — passed.

No workflow specification or traceability file was changed for this increment.
