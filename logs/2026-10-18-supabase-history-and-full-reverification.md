# Supabase V5 history and full local reverification

- Date: 18 October 2026, Asia/Singapore.
- Request: check shared Supabase V5 history and rerun all checks/tests.
- Source revision: f8a2b49 on feat/requester-workflow.
- Scope/IDs: REQ-RQ-001-017, NFR-PERF-001, NFR-A11Y-001/002,
  NFR-SEC-001/002, NFR-DOC-001, TECHNICAL-SPEC schema compatibility.
- Full execution: `gradlew --offline --no-daemon check bootJar
  requesterVerification --rerun-tasks` succeeded in 5m44s, all nine tasks executed.
  Standard Java suite:585 tests, zero failures/errors/skips. Opt-in verification:
  one integration test, zero failures/errors/skips. JaCoCo/check/JAR succeeded.
- JavaScript: `node --test src/test/js/request-form.test.cjs
  src/test/js/smart-report.test.cjs` passed both files; Node24.19.0.
- Browser/load: Chrome155.0.8059.82, 68 combined checks, three viewports
  (760x600,1024x700,1440x900). Fifty distinct authenticated synthetic users,
  400 requests, zero errors, default pool2; p50=341.52ms, p95=520.15ms,
  max=660.25ms. The local one-second gate passes on this rerun; previous
  step7 failures remain historical evidence. This is loopback end-to-end
  measurement, not deployed/server-only performance or latest-browser certification.
- Generated evidence: build/full-reverification.log; build/test-results/test;
  build/test-results/requesterVerification; build/verification/requester/results.json,
  runner.txt, screenshots and pool-metrics.json. Ignored artifacts not staged.
- Hosted check uses an ignored throwaway Java/JDBC helper and cached YAML/PG
  libraries. Configuration/credentials stay in memory; only safe booleans,
  SQLSTATE/category and intended migration metadata may be printed. TLS verify-full
  and server-enforced read-only transactions required; no migration/repair/grants.
- Local YAML uses top-level environment-style keys, supported after checker
  adaptation. Configured CA path is missing; previously downloaded Supabase CA
  was available and used for this check without changing local settings.
- Supabase rejected the configured login with28P01 (authentication failed), before
  any history query. No migration rows were retrieved, and V5 identity remains
  unverified. User chose to correct credentials locally and notify when ready;
  no repeated password guesses or privilege expansion attempted.
- Normal app identity may lack history SELECT by design; a migration/owner role
  or SQL Editor metadata result is required if that permission is unavailable.
- No application code, hosted data, local secret file, commit, push or deployment
  changed. Unrelated local-configuration-template log preserved.

## Retry after user saved updated configuration

The configured certificate file now exists and strict TLS checking remains enabled.
The saved login is still rejected by Supabase with SQLSTATE28P01 (authentication
failed), before migration metadata can be read. No history rows retrieved and no
hosted writes performed. Further password guessing/retries were not attempted.
Previously completed local checks are unchanged; no source changes justify rerun.

## Successful login; restricted history access

After the next user-requested retry, strict-TLS connection succeeded and the
read-only transaction was verified. Direct Flyway history SELECT was denied42501;
no privilege grants or alternate-password guesses attempted. Permitted metadata
query succeeded read-only: schema_version view absent, request_submission absent,
rate_limit_bucket.lease_owner present. These structures are consistent with the
master gatewayV5 and absent RequesterV6 objects, but do not establish exact applied
version/script/checksum history. Exact history still needs migration/owner role or
user-supplied SQL Editor metadata. No hosted writes performed. Local tests unchanged.

## Hosted history confirmed by user SQL Editor screenshot

The user supplied SQL Editor results for the exact read-only history query on
18 October2026. Five rows, all success=true:

- 1: V1__identity_audit_sessions.sql
- 2: V2__maintenance_records.sql
- 3: V3__skills_ai_storage.sql
- 4: V4__require_assignment_timestamp.sql
- 5: V5__ai_gateway_coordination.sql

This resolves the hosted V5 script-identity question: it is master's gatewayV5,
not the former RequesterV5. Merged RequesterV6 is absent/pending. Evidence is a
user-supplied screenshot, not a privileged JDBC query by this agent; checksums
were not included and still require normal Flyway validation when migrating.
No hosted write/migration/repair/reinitialization performed. All local checks
remain as recorded above; no additional rerun needed for metadata evidence only.

## Request to implement/apply V6

V6 implementation and readiness integration already exist in f8a2b49 and passed
the full local recheck. User requested the next V6 step. Private configuration
inspection (presence flags only) found application credentials but no dedicated
SPRING_FLYWAY_USER/PASSWORD; runtime identity is not the migration role. Requested
that user save migration credentials locally, retaining app credentials unchanged.

Prepared an ignored runner for the existing packaged migration profile: strict
TLS for both datasources, read-only preflight verifying masterV1-V5 history,
Flyway validation/target6/clean disabled/no baseline, AI disabled, and read-only
postcheck. No hosted DDL/repair/seeding was executed while credentials are absent.
Do not commit credentials or ignored helper. Deployment awaits user's readiness
confirmation; existing application code and prior test results are unchanged.

## Migration credential retry after user saved configuration

Dedicated migration credentials are now present and the runtime remains the app
role. The strict-TLS read-only migration preflight failed with SQLSTATE 28P01
(authentication failed). Flyway was not started; V6 was not applied and no hosted
writes occurred. User must correct the migration role credentials locally; do not
share passwords in chat. Existing local verification remains unchanged.

## V6 applied and verified on development Supabase

After the next user-requested retry, strict-TLS migration login and read-only
preflight succeeded, confirming the expected V1-V5 scripts. The existing packaged
database-migrate profile completed with Flyway validation enabled, target 6,
clean disabled, baseline disabled, AI disabled and demo initialization disabled.
Read-only migration-role postcheck confirmed six successful versions including
V6__request_submission_and_readiness.sql. A separate read-only application-role
check confirmed request_submission and schema_version exist, the gateway lease
column remains present, and visible versions 1-6 all report success=true.

REQ-RQ-002/011 and REQ-SH-010/014 storage/readiness deployment is now verified.
No reseeding, history repair, or business-data test writes were performed. Prior
585-test, browser/load and Node results remain the local integration evidence;
they were not repeated for a configuration-only migration retry. Hosted browser
end-to-end testing remains separate. No credentials were printed or committed;
no commit or push was requested/performed for this retry.
