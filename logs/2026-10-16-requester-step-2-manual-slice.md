# Requester step 2: create, own list and detail

- Date: 16 October 2026 (Asia/Singapore).
- Branch: `feat/requester-workflow`.
- Authorization: user requested committing step 1, then continuing to step 2.
  Step 1 committed as `74949cd`; no push, merge or deployment requested.
- Requirements: approved v0.1 REQ-RQ-002/004/005/009–011; partial REQ-RQ-001;
  REQ-SH-002/006/008–010/012–014 and NFR-SEC-001/002; TECHNICAL-SPEC readiness.

## Implemented scope

- Immutable five-field RequestDraft with server-side Unicode code-point limits,
  outer trimming, retained internal line breaks, active-category/urgency rules
  and field-specific errors. Overposted owner/status/priority/IDs are ignored.
- Requester use-case and repository interfaces, transaction-scoped service and
  parameterized PostgreSQL adapter. Each operation rechecks persisted active
  state, role and session version. Shared/exclusive account locks protect reads
  and creation; active categories are locked before insertion.
- Atomic OPEN Request creation with server-derived owner/Recording Actor,
  database display ID, one audit event and one durable submission record.
  Account-level serialization plus a per-owner submission ID prevents duplicate
  creates across instances and browser sessions. Replays fail safely; rollback
  leaves the command available for retry. Failed command tokens do not become
  authorizations, and no submitted authority field changes server identity.
- Own list defaults to newest creation time, then display ID ascending. Detail
  is owner-filtered before lookup/history; Manager-recorded reports appear for
  their owner. SQL projects only report fields, approved status/edit events,
  public reasons and Requester Updates. No Work Logs/private Manager events/raw
  audit details are returned in the page records. Foreign and nonexistent IDs
  have the same safe 404 response.
- Create/list/detail templates, safe output encoding, local UTC-derived Singapore
  times, accessible labels/errors/focus styles and pending-submit handling.
  Validation, storage and late account-access failures retain submitted input.
- About page and shared workspace/password/About/logout header. Role dashboards
  remain placeholders except Requester links; dashboard counts/search/filtering
  and mutations beyond creation are later steps.
- V5 appends submission storage with RLS and minimal grants, plus a read-only
  version/success view of Flyway history. Runtime startup checks versions 1–5
  without DDL; failed/missing/future versions stop startup. Migration-time Flyway
  validation remains responsible for checksums. Original V1–V4 were not edited.

## Sources and tools

Read current specs, step 1 handoff, schema/grants, existing security/session
code, affected fixtures and migration tests. Applied Ponytail, Context7,
PostgreSQL best practices and FacilityFlow handoff instructions. Context7 supplied
official Spring constructor-binding and Flyway validation documentation; no
provider endpoint, secret or hosted data was queried.

Inspected retained MP2 RequesterDashboardView.showDetail at verified baseline
9819b857d880134c1fc1ffef8a8bcd5d6e536f43 to confirm report-field presentation and
exclusion of assignment/completion metadata. Reused behavior/concepts only;
Spring/Thymeleaf/JDBC implementation is new. REUSE.md records the classification.

## Verification

- Reviewed/staged the previously verified step 1 scope; committed `74949cd`.
- Focused `gradlew.bat test --tests '*RequestDraftTest' --tests
  '*RequesterRequestIntegrationTest' --tests '*DatabaseMigrationTest' --tests
  '*SecurityIntegrationTest' bootJar`: BUILD SUCCESSFUL in 1m23s; 47 tests,
  zero failures/errors/skips. Used disposable PostgreSQL and restricted app role;
  existing H2 suite remains authentication-only with a marked readiness fixture.
- Tests cover create/list/detail, field boundaries and whitespace, overposting,
  ownership/privacy, Manager-recorded reports, status history/updates, XSS
  encoding, role/CSRF denials, duplicate replay, eight concurrent identical
  submissions, rollback/retry, stale/disabled/role-changed accounts, inactive and
  nonexistent categories, ordering ties, display-number exhaustion, read-only
  readiness permissions and unknown/failed schema versions.
- Added controller recovery tests after self-review to verify retained input and
  safe guidance when account access changes during save/error recovery. No
  failed test was weakened or removed.
- Full `gradlew.bat test bootJar`: BUILD SUCCESSFUL in 3m22s; 68 tests, zero
  failures/errors/skips, checked from build/test-results/test/TEST-*.xml. JaCoCo
  generated. No failing application tests occurred in this step. A final CSS
  long-heading wrap adjustment made during the run was packaged by a subsequent
  successful `gradlew.bat bootJar` (3s); no Java/SQL/JS change followed the full run.
- `node src/test/js/request-form.test.cjs`: passed pending-button prevention and
  back-navigation recovery assertions. Uses standard Node APIs, no dependency.
- `git diff --check` passed before final records.

## Remaining and next

V5 has not been applied to a hosted database. Apply it with the explicit migration
identity/profile before starting this revision online. Browser/keyboard/viewport,
50-user performance, hosted persistence checks and human teammate review remain
pending. The next implementation step is owned OPEN edits/cancellation and
Requester Updates on non-terminal reports, including state/version races and
atomic audit. Dashboard/search/filtering and AI follow afterward.

Step 2 remains uncommitted at this checkpoint; no push/merge/deployment or
external messages performed. Shared Manager/Technician business services remain
with their assigned owners.
