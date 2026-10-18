# Technician T2 report

Status: locally verified; full 585-test check and executable packaging passed.
Final independent closure recorded before commit; teammate/browser/hosted review pending.

## Scope and authority

User approved the next iteration after T1 preview and requested preparation again.
Baseline `57098d9` on technician-workflow; starting tree/index clean. Implements
REQ-TECH-004-006/010-011 and applicable shared auth/audit/forms rules. T3 remains
unapproved. [Dispatch](T2-DISPATCH.md) records preparation timestamp and models;
[contract](T2-CONTRACT.md) settles APIs, routes, errors, ownership, validation,
transactions and tests before workers. Actual schema/grants inspection found no
migration/dependency change necessary. No push/merge/deployment or live DB.

## Actual agents

| Agent | Model / effort | Actual work |
| --- | --- | --- |
| Backend | gpt-6.1-sol / medium | Four explicit command service/repository/error files; locked persisted authorization and atomic state/version/history/safe audit. |
| UI | gpt-6-luna / high | Command controller, state-gated versioned forms, CSS and external pending-submit script; corrected two input-preservation findings. |
| Tester (cs3227_unit_test) | gpt-6-luna / high | Three service/repository/controller test classes plus isolated PostgreSQL fixture; transaction, authorization, race, validation and rollback evidence. |
| Independent reviewer | gpt-6.1-sol / high | Combined security/spec/standards/test adequacy; found R01-R06, independently rechecked author corrections and execution evidence; final verdict recorded in own handoff. |
| Coordinator | Parent model retained | Pre-dispatch schema/API/task/handoff preparation, actual authenticated MVC/Thymeleaf regressions, JS event check, sole runner, freeze manifests/patches, guides/traceability/reuse, Git/report. |

From the dispatch artifact at 08:40:41 UTC (16:40:41 Singapore) to full result
recorded 09:12:42 UTC (17:12:42 Singapore): 32m01s. This excludes preceding
initial repository/spec preparation and final documentation/Git closure; exact
individual agent wall times were unavailable.

Three writers started concurrently with fresh focused task context; reviewer
started when backend slot freed and overlapped remaining UI/test work. No leaf
agents delegated or ran competing builds. Personal test skill used approved MP3
PostgreSQL contract in place of its older MP2 SQLite recipe. Each transition has
OS-temp and durable handoff evidence; final specialist links below.

[Backend handoff](../handoffs/2026-10-18-technician-T2-backend.md),
[UI handoff](../handoffs/2026-10-18-technician-T2-ui.md),
[Tester handoff](../handoffs/2026-10-18-technician-T2-tests.md),
[reviewer handoff](../handoffs/2026-10-18-technician-T2-review.md).

## Implemented behavior

- Assigned Technicians explicitly start ASSIGNED work; only IN_PROGRESS accepts
  append-only Work Logs. Each successful command increments Request.version once,
  updates persisted time and atomically records one safe Audit Event.
- Service identity comes from the authenticated snapshot. Repository locks and
  rechecks persisted active/TECHNICIAN/exact session version, current assignment,
  expected state and nonnegative Request version in the same transaction.
  Same-version replay/concurrent commands have one winner; stale former assignees
  cannot mutate reassigned work. Failed audit rolls back all writes.
- Notes accept 1-1000 trimmed Unicode code points, preserving internal whitespace;
  minutes accept 1-1440 whole minutes. Database-generated history ID/time and
  server-side author cannot be supplied or changed by a browser.
- State-gated forms use CSRF and hidden versions; success 303 opens persisted
  details. Errors 400/409/404/503 preserve escaped input and use assigned-only
  fresh reads; user-owned draft remains visible when record access/state changes.
  External JS blocks duplicate pending submission; server versions independently
  prevent duplicate writes. No generic status/assignment/edit/close action.
- No completion, password, AI/browser feature, other-role behavior, schema,
  dependency, shared security or workflow configuration change is included.

## Findings and preparation corrections

| ID | Finding and correction | Verification status |
| --- | --- | --- |
| T2-R01 | Fresh state/access failure hid unsaved Work Log outside eligible form. UI now retains escaped user-owned draft outside protected detail without command form. | Independent source recheck and early authenticated rendered regression passed; all corresponding rendered cases passed in the full unified run. |
| T2-R02 | HTML number field sanitized malformed submitted raw minutes. UI uses text with numeric inputmode/pattern and server range validation. | Independent source recheck and early authenticated rendered regression passed; all corresponding rendered cases passed in the full unified run. |
| T2-R03 | Audit fault trigger could contaminate class-scoped test fixture. Tester adds finally and reset cleanup. | Independently source-verified; scoped PostgreSQL rollback/cleanup cases passed; full unified run passed; independent source/runtime recheck recorded. |
| T2-R06 | Audit-key assertions called nonexistent PostgreSQL jsonb_object_length. Tester replaced only helper SQL with lateral jsonb_object_keys count, preserving expected exact keys/counts. | Independent source and focused 2-test runtime verification passed; full unified run passed; independent source/runtime recheck recorded. |
| T2-R05 | Two redirect tests omitted checked-exception declarations; one two-submission test expected one reload. Tester corrected signatures and exact interaction count without changing production/assertion intent. | Independently verified; controller 8 tests passed in scoped run after repair; full unified run passed; independent source/runtime recheck recorded. |
| T2-R04 | Initial transaction test was tautological; later separate DataSource probe did not test transaction suspension. Shared exact DataSource, outer SERIALIZABLE rollback and temporary command-isolation trigger now assert observable behavior; missing race/state matrix added. | Independently source-verified; scoped state/transaction/race cases passed; full unified run passed; independent source/runtime recheck recorded. |

Other pre-execution test corrections: synthetic new assignments need V4
assigned_at; class-scoped fixture resets existing accounts rather than deleting
referenced history; reviewer caught log-state cases calling start helper and
nullable UUID CASE typing. These were source-preparation corrections, not failed
executed test runs. No production grant or assertion weakened.

## Executed checks

All Gradle commands use existing wrapper `--offline --no-daemon --console=plain`.

| Check | Executed result |
| --- | --- |
| Early compile/fixture smoke | `test --tests sg.edu.nus.facilityflow.technician.JdbcTechnicianCommandRepositoryTest.startPersistsStateVersionAndSafeAudit --tests sg.edu.nus.facilityflow.technician.TechnicianCommandServiceTest`: BUILD SUCCESSFUL in 43s. Eight tests (service7 + PostgreSQL1), zero failures/errors/skips. Production and test compilation passed. [Smoke result](T2-SMOKE-RESULT.json), [13-input snapshot](T2-SMOKE-CANDIDATE.json); all hashes unchanged during execution. Local raw log build/T2-smoke.log. |
| Early authenticated MVC overlap | `test -x compileTestJava --tests sg.edu.nus.facilityflow.web.SecurityIntegrationTest`: BUILD SUCCESSFUL in 41s,26 tests, 0 failures/errors/skips. Used identical already compiled 9 production/root-test inputs from passed smoke; unrelated test-source authoring continued without test compilation. [MVC result](T2-MVC-RESULT.json), [input provenance](T2-MVC-CANDIDATE.json), build/T2-mvc.log. All 9 hashes unchanged. |
| Final scoped first attempt | Four T2 scoped classes selected; production/resources up-to-date; compileTestJava failed on two checked exceptions in new controller tests (BUILD FAILED in 16s), no tests executed. [Compile result](T2-COMPILE-RESULT.json), [superseded snapshot](T2-CANDIDATE-before-controller-fix.json), build/T2-scoped.log. Independent reviewer verified minimal test-only repair before rerun. |
| Corrected scoped run | BUILD FAILED in 1m26s:56 executed, 54 passed, 2 failed audit-key test SQL; service 7/controller 8/Security 26 all passed, PostgreSQL 13/15 passed including rollback, locks, concurrent writes and lifecycle matrix. [Scoped result](T2-SCOPED-RESULT.json), [superseded snapshot](T2-CANDIDATE-before-audit-test-fix.json), build/T2-scoped-rerun.log. |
| Focused audit correction | Two audit tests passed,0 failures/errors/skips; BUILD SUCCESSFUL in 37s. [Focused result](T2-AUDIT-RESULT.json), final [candidate](T2-CANDIDATE.json), build/T2-audit-rerun.log. All 14 hashes unchanged. |
| Comprehensive final candidate | `./gradlew.bat --offline --no-daemon --console=plain check bootJar`: BUILD SUCCESSFUL in 6m23s; 585 tests across 41 suites, zero failures/errors/skips; test, jacocoTestReport, check and bootJar completed. All scoped 56 cases passed in one run. [Full result](T2-RESULT.json), build/T2-check.log. All 14 input hashes unchanged. |
| External script | `node src/test/js/technician-commands.test.cjs`: PASS pending duplicate blocked, independent forms, retained values, pageshow reset and no-form page. Node v24.20.0; actual script executed with a small DOM event model. |

The smoke and final snapshots have saved new-file-inclusive patches under
build/ with SHA256 recorded; final patch reverse-apply check passed. Final 14
canonical UTF8/LF input digest:
`60bd42eeccf00d0a85cbeb3772ad2efe0ea10ff11fc7e28eeb5dd6b4e1f6e0b2`.
After two failed expanded attempts (compile then test-SQL), the coordinator
narrowed correction verification to the two actual audit failures before the
full unified check, preserving assertions and production. Raw build output stays
local. The containing final commit will retain all source,
tests and durable evidence; resolve its ID with `git log -1 -- logs/technician/T2-REPORT.md`.

## Evidence limits and workflow evaluation

- Browser/keyboard/visual/performance/hosted acceptance remains T5/release work.
  Authenticated MVC uses a command-service double and real assigned-query and
  Thymeleaf rendering; PostgreSQL tests prove actual transaction behavior. The
  modeled JavaScript check is not supported-browser acceptance.
- Per-agent exact elapsed/token/cached-token counts are unavailable. No measured
  token-cost or latency savings claimed. Different T1/T2 scopes are not a valid
  controlled timing comparison. T2 reduces fixture startup duplication using
  one isolated PostgreSQL server per command test class with per-test reset.
- MP3 requirements/current seams only; no MP2 source consulted/copied. Final
  changes relative to 57098d9: application Java +453/-0 lines, resources +85/-0,
  tests/fixtures +1,200/-0. New files and existing MP3 edits included; docs and
  process evidence excluded from implementation counts.
- Teammate non-trivial merge review remains pending. Agent/local test success
  does not grant merge or deployment approval.

## Final closure checks

All 14 canonical source/test hashes match executed inputs; saved patch reverse
check passed. Local changed-Markdown links resolve and whitespace/scope checks
pass. Exact staged inputs are checked again against the manifest before commit;
no build/dependency/migration/shared security/AI/workflow configuration changes.
Shared traceability rows distinguish verified Technician evidence from pending
other-role/browser/release obligations. No unrelated work was present or staged.

## Next gate

Report T2 with local commit/results/per-agent contributions. Preview T3 completion
before implementation; do not start T3 without explicit user authorization.
