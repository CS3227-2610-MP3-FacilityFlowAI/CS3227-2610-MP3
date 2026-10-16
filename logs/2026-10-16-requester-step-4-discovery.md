# Requester step 4: dashboard and discovery

Date: 16 October 2026 (Asia/Singapore). Branch: `feat/requester-workflow`.

## Authorization and sources

The user requested committing step 3, then continuing step 4 of the ordered
Requester implementation. Step 3 was committed as `8d00fe6`
(`feat(requester): add versioned edits cancellation and updates (REQ-RQ-006/008)`).
Step 4 remains uncommitted at this checkpoint. No push, merge, hosted database
change or deployment was performed.

Approved specification v0.1: `workflow/specs/ROLE-REQUESTER.md`,
`PRODUCT-SPEC.md` and `TECHNICAL-SPEC.md`; sequenced in
`workflow/REQUESTER-PLAN.md` section 6. Scope: REQ-RQ-001/003/004/009,
REQ-SH-008/009/010/012, preserving shared authorization and privacy rules.

## Implemented

- Own dashboard with all six status counts (including zero), status-filter links
  and five newest reports, using the same stable ordering as the list.
- Immutable `RequestFilters`, retained GET filter form and owner-scoped queries.
  Search is trimmed, case-insensitive literal substring matching on display ID,
  title or location. Status/category/date filters combine with AND. Inactive
  categories remain discoverable without becoming valid create/edit choices.
- Inclusive Asia/Singapore creation dates become a half-open UTC range. Invalid
  dates, ranges, statuses and category selections produce field errors/HTTP 422,
  retain values and never fall back to an unfiltered result.
- Visible filters, one-action reset, separate no-requests/no-matches guidance,
  escaped user text, shared request-table fragment and responsive filter/count grids.
- Updated guides, traceability, progress and reuse evidence. No production
  migrations or dependencies changed; existing V5 is still required at startup.

All workspace use cases revalidate persisted Requester state/session inside the
existing transaction. Queries derive owner identity server-side; the search OR
group cannot escape the ownership predicate. Bound search values do not become
SQL wildcard syntax. Existing mutation locks, state/version checks and audit
atomicity are unchanged. No AI or other-role operation was added.

## Verification and correction

`gradlew.bat test --tests '*RequestFiltersTest' --tests
'*RequesterRequestIntegrationTest' --tests '*SecurityIntegrationTest' bootJar`
initially ran 42 tests with three authentication-suite failures. All 24 genuine
PostgreSQL Requester tests and three filter tests passed. The real dashboard now
executes PostgreSQL `FOR SHARE`, which the authentication-only H2 fixture cannot
execute.

Corrected the authentication test initializer to register a primary
`RequesterRequests` dashboard stub only for that test application. Login,
password/audit services, security filters, persisted accounts and JDBC sessions
remain real. Production locking was preserved; attempted H2 workspace tables
were removed. PostgreSQL integration tests still exercise actual dashboard/list
queries and service authorization under the restricted `facilityflow_app` role.

The focused command rerun passed **42 tests, zero failures/errors/skips** and
`bootJar`, BUILD SUCCESSFUL. Fresh read-only verifier `/root/verify_discovery`
inspected the correction, connected callers, ownership grouping, bound literal
search, dates, inactive categories, invalid-filter behavior, escaping and fresh
XML. It reported no blocking findings and independently confirmed all 42 tests.
See `logs/handoffs/2026-10-16-requester-step-4-verification.md`.

Full `gradlew.bat test bootJar` passed **86 tests, zero failures/errors/skips**,
BUILD SUCCESSFUL in 4m18s. JAR packaging and JaCoCo report tasks succeeded.
Counts were read from all 12 `build/test-results/test/TEST-*.xml` suites.

New checks cover mixed-case/literal punctuation and injection-like searches,
forged owner inputs, all counts, foreign/newer reports, recent ordering, inclusive
midnight boundaries, one-sided/combined filters, inactive historical categories,
reset, distinct empty states, strict dates/UUIDs and retained escaped errors.
Existing creation, mutation, audit, role/session/CSRF and concurrency regressions
remain in the suite. No JavaScript changed this step; the earlier Node check was
not repeated. `git diff --check` passed during final documentation preparation.

## Remaining work and next step

The manual Requester slice is implemented locally. Smart Report Assistant
REQ-RQ-012–017, shared gateway integration and release evidence remain pending.
Next is the Smart Report service against a fake gateway (plan section 7), within
the existing ownership and advisory-only boundary. No live credentials assumed.

Browser/keyboard/viewport/accessibility, hosted V4/V5/application, full cross-role
integration and performance checks remain unverified. A human teammate must
review non-trivial work before merge. Local tests do not establish deployment
readiness or satisfy the full non-functional requirements.

Skills used: Ponytail for minimal implementation, Context7 for Spring MVC binding,
PostgreSQL best practices for query authoring and `$facilityflow-handoff` for
verification and continuation records. No MP2 source consulted/copied this step;
new implementation and retained concepts are classified in `workflow/REUSE.md`.
