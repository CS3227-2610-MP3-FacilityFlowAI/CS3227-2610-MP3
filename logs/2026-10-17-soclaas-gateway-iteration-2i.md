# SoCLaaS gateway Iteration 2I — bounded compressed responses

## Scope

Implemented the compressed-response transport increment without changing the
workflow specifications.

## Delivered

- Advertises `gzip, identity` while retaining deterministic request construction.
- Accepts exactly one response encoding token: absent/`identity` or `gzip`.
- Streams gzip responses through a one-megabyte compressed-wire budget before
  the existing 256 KiB post-decompression decoder.
- Preserves body ownership: rejected, malformed, oversized, cancelled and
  decoder-consumed response bodies are closed on every path.
- Maps malformed compressed data to the existing safe `BODY_UNREADABLE`
  failure and compressed-wire/decompressed overflow to `BODY_TOO_LARGE`.
- Adds deterministic adapter coverage for gzip success, malformed gzip, wire
  budget enforcement and unsupported encodings.

## Verification

- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.provider.SocLlmHttpsProviderAdapterTest' --no-daemon` — passed.
- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.ai.provider.*' --no-daemon` — passed.
- `gradlew.bat test --tests 'sg.edu.nus.facilityflow.technician.TechnicianWorkPlanServiceTest' --tests 'sg.edu.nus.facilityflow.database.DatabaseMigrationTest' --no-daemon` — passed.

The full Gradle suite was started but did not finish within the available
verification window; no failure result was produced. No workflow specification
or traceability file was changed for this increment.
