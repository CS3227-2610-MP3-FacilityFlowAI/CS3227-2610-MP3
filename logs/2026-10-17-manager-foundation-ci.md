# Manager foundation and CI session

- Date/timezone: 17 October 2026, Asia/Singapore.
- AI/tool/role: Codex Developer; fresh read-only verifier `verify_manager_foundation`; Impeccable UI mechanical audit.
- Task/intention: begin the independent Facilities Manager slice and pull-request/master CI from `origin/master`, without modifying the Requester branch.
- Branch/baseline: local `feat/manager-workflow` from `origin/master` at `e784d48`.
- Approved requirements: REQ-MGR-001–003/010–011; REQ-SH-002/006/008–010/014; NFR-DEP-001 and NFR-DOC-001 partially.
- Changed scope: CI verification workflow; Manager overview, deterministic Request queue, filters, on-behalf creation, audit viewer, server-side authorization/validation/JDBC persistence; templates; tests; guides, traceability and reuse evidence. No migration was added; V5 remains reserved for the separate Requester branch.

## Security and transaction decisions

The service rejects a non-Manager principal and the repository rechecks the persisted Account ID, username, active state and FACILITIES_MANAGER role under a database lock. On-behalf creation lists only active Requesters, rechecks the Requester and Category inside the write transaction, derives the Recording Actor from the signed-in Manager, and commits the Request plus Audit Event atomically. SQL uses named parameters. Audit views project no details JSON and leave non-Request associations null.

The browser form retains submitted values after validation and JDBC/transaction failure and shows safe retry guidance without database details. If form options cannot reload, retained fallback Requester and Category choices preserve the submitted IDs so the required selections remain retryable, alongside a bounded availability message. Lifecycle mutation, account/catalogue/skill administration, Triage Assistant and deployed controls are outside this first slice.

## Verification and corrections

Initial `./gradlew test --no-daemon` stopped at compilation because a count-query lambda matched two JDBC overloads. The helper was changed to an explicit row mapper.

The next full run executed 55 tests and found two test-fixture defects: one expected five validation errors where six fields were invalid, and an injected audit constraint tried to validate an existing matching row. The expectation was corrected and the PostgreSQL constraint was made `NOT VALID` so it rejects only subsequent writes. Focused Manager tests then passed.

The first clean CI-equivalent run, `./gradlew clean check bootJar --no-daemon`, passed in 1m14s and produced the bootable JAR. The fresh verifier confirmed the earlier corrections, then found missing field messages, non-distinct empty/zero-result states, inactive Technician history filters, and missing persisted stale-principal evidence. These were fixed with explicit status/priority errors, separate dataset/no-match flags, all-Technician historical filter options, and inactive/role-changed persisted-account tests. Controller tests were added for validation, storage and option-load failure retention.

The final timestamp hardening formats Request and audit instants explicitly in `Asia/Singapore`; the regression check converts `2026-10-17T00:30:00Z` to a local 08:30 display and includes the zone label. A first attempt at a final full run overlapped the independent verifier and failed only while both Gradle processes wrote the same XML result files. The run was repeated alone.

Final primary verification: `./gradlew clean check bootJar --no-daemon` passed in 1m7s with all nine tasks executed; JaCoCo ran and the bootable JAR was produced. Independent post-fix verification forced a non-cached focused run: 12 Manager/controller tests passed with zero failures, errors or skips. `git diff --check` passed. The Impeccable mechanical detector returned no findings for the changed Manager templates and JavaScript. This is not a browser, accessibility or visual-layout claim.

## CI and remaining work

`.github/workflows/ci.yml` uses read-only repository permissions, validates the Gradle wrapper, runs `clean check bootJar`, executes dependency-free JavaScript tests when present, and uploads reports/JAR artifacts. It has not executed on GitHub yet. Preview/Production deployment, environment separation, health/smoke, human promotion, rollback and backup/restore remain pending; no hosted access, push, merge or deployment occurred.

MP2 Manager behavior is concept-only reuse. No JavaFX or SQLite source was copied. The action-filter and missing-request-association grade findings were converted into new PostgreSQL regression tests.

Human reviewer/outcome: pending. Teammate review is required before merge.
