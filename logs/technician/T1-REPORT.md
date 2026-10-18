# Technician T1 Report

Status: T1 locally verified; full 547-test check passed, no failures/skips.
Teammate/browser/hosted-release verification remains pending.

## Scope and authority

- User approved the next iteration after T0: REQ-TECH-001-003/009 and applicable
  shared read authorization, search/filter, UTC/local display and safe UI rules.
- Baseline: 172eaff on technician-workflow; starting tree/index clean.
- Start: 18 October 2026, 15:00:51 Asia/Singapore (07:00:51 UTC).
- Full-check result recorded at 15:28:43 Asia/Singapore: 27m52s from dispatch
  start, including authoring/review, test repairs and execution, excluding final
  packaging/document checks/commit. Per-agent exact times were unavailable.
- [Dispatch](T1-DISPATCH.md) supersedes the old prepared packets' unapproved
  status for T1 only. T2 and later remain unapproved.
- The final commit containing this report identifies the result; resolve with
  `git log -1 -- logs/technician/T1-REPORT.md`. No push or merge is authorized.

## Actual agents and ownership

| Agent | Model / effort | Work and result |
| --- | --- | --- |
| Backend Developer (reused T0-B context) | gpt-6.1-sol / medium | Five new query/model/exception/repository files; consistent scoped account/Request reads; corrected two independently found capability-boundary issues. |
| UI Developer (reused T0-U context) | gpt-6-luna / high | New MVC controller, four templates and approved external CSS; corrected missing-queue rendering and CSP compatibility before freeze. |
| Tester (fresh cs3227_unit_test agent) | gpt-6-luna / high | Three test classes plus isolated PostgreSQL fixture; added actor-binding/nested-snapshot regressions and corrected test compilation/provisioning defects. |
| Independent Security/Spec Reviewer (fresh context) | gpt-6.1-sol / high | Found two P2 session-capability issues; independently checked source corrections, test-only repairs, UI trust boundaries and frozen manifests. Runtime closure recorded below. |
| Coordinator | Parent model retained | Shared landing-route wiring; existing authentication fixture plus real Thymeleaf/MVC regressions; integration, one test runner, guides/traceability/reuse, manifests, Git and report. |

Authoring ran concurrently with at most three workers; review started after a
slot freed and overlapped remaining test/UI work. Leaf agents did not delegate,
mutate Git or run competing builds. Backend/UI reused focused planning context;
the personal test skill's requirement-based method used the approved MP3
PostgreSQL contract rather than its MP2 SQLite recipe.

Agent evidence:
[backend](../handoffs/2026-10-18-technician-T1-backend.md),
[UI](../handoffs/2026-10-18-technician-T1-ui.md),
[Tester](../handoffs/2026-10-18-technician-T1-tests.md),
[reviewer](../handoffs/2026-10-18-technician-T1-review.md).
Each supplied its required OS-temp handoff as well.

## Implemented behavior

- `/technician` and `/technician/requests`: combined assigned-work count cards,
  queue, literal case-insensitive/trimmed search, AND status/category/priority
  filters, deterministic priority/urgency/time/ID order and distinct empty states.
- Detail reads only currently assigned Requests, including retained terminal
  assignments. Requester Updates and Work Logs preserve prior authors and
  deterministic order. No private audit data, password/account entity or
  Request/Account internal ID reaches the view.
- Persisted active/role/exact nonnegative session checks, counts, options, rows
  and histories share a read-only repeatable snapshot. Read capabilities are
  thread/lifetime/transaction-resource confined and remain bound to their first
  successfully authenticated actor across caught failures.
- Invalid filters render 400 with submitted values and field errors, never a
  broader query. Invalid/unknown/unassigned details share a safe 404; operational
  failures are generic 503 outcomes. User text is escaped; timestamps include
  Singapore local time/zone. External CSS respects the existing CSP.
- Shared Requester/Manager role routes and existing AI paths remain unchanged.
  No work-start/log/completion/password/AI UI controls, migration, dependency
  change, live database operation or deployment is part of T1.

## Findings and correction evidence

| ID | Finding | Correction and verification |
| --- | --- | --- |
| T1-U01 | Error view could evaluate categories on a missing queue | Guarded parent block; submitted values preserved without protected reload; independent structural review and authenticated 400 rendering. |
| T1-U02 | Inline CSS would be blocked by current CSP | External /css/technician.css, no CSP relaxation; source/style and authenticated HTML checks. |
| T1-REV-001 (P2) | Failed reauthorization could erase original actor binding | Separate persistent boundActor and revocable authorization; independent fresh source review; PostgreSQL caught-failure regressions in final check. |
| T1-REV-002 (P2) | Suspended outer capability could query a nested transaction's connection | Bound transaction resource identity (manager's actual DataSource); independent source review and nested-snapshot regression in final check. |
| T1-COMP-001 | Test helper types/order, view accessor, placeholder count and uppercase UUID fixture errors | Test-only corrections, same behavioral assertions, fresh independent review; production unaffected. |
| T1-COMP-002 | Fixture close declared SQLException rather than IOException | Correct checked API contract/import; independent recheck; test compilation subsequently passed. |
| T1-FIXTURE-001 | Assigned-state seed INSERT exceeded app-role INSERT grants | Privileged fixture setup uses admin; repository stays facilityflow_app, production grants unchanged. Replanned fixture responsibility separation after repeated setup failures; fresh review before full rerun. |

## Commands and outcomes

All commands use the existing wrapper with `--offline --no-daemon --console=plain`.
The scoped command selects TechnicianQueryServiceTest,
JdbcTechnicianQueryRepositoryTest, TechnicianQueueControllerTest and
SecurityIntegrationTest using four `--tests` options.

| Attempt | Command / outcome |
| --- | --- |
| Scoped 1 | `test --tests ...`: production compiled; compileTestJava failed with 28 helper/type errors; no tests executed. Local log build/T1-scoped.log. |
| Scoped 2 | Same command after reviewed repairs: sole fixture IOException declaration compilation error; no tests executed. build/T1-scoped-rerun.log. |
| Scoped 3 | Same command: 43 executed, 34 passed, 9 privileged fixture INSERT failures; no skips/errors outside reported failures. build/T1-scoped-final.log; durable [scoped result](T1-SCOPED-RESULT.json). |
| Packaging | `./gradlew.bat --offline --no-daemon --console=plain bootJar`: BUILD SUCCESSFUL in 10s; executable application archive built. build/T1-package.log. |
| Comprehensive corrected candidate | `./gradlew.bat --offline --no-daemon --console=plain check`: BUILD SUCCESSFUL in 5m33s; 547 tests in 38 classes, zero failures/errors/skips; test, jacocoTestReport and check completed. Covers all corrected scoped cases and existing regressions in one pass. build/T1-check.log; durable [result](T1-RESULT.json). |

The [final candidate](T1-CANDIDATE.json) identifies 17 canonical UTF-8/LF
application/test inputs after repairs. Earlier candidate manifests identify
the superseded backend, compile and privileged-fixture states; none represents
a passing final result. All 17 final source/test hashes remained unchanged
during the successful check; digest
`453e4fea1443005c7302225908140394a19fb53b0d784a94419560afd9e6cd03`.

Final scoped class counts inside the full passing run: JdbcTechnicianQueryRepositoryTest
11, TechnicianQueryServiceTest 5, TechnicianQueueControllerTest 9,
SecurityIntegrationTest 18 (43 total). Real Thymeleaf tests cover populated
queue/detail/history/local timestamps, malformed filters without a queue and
safe 404 pages. Both security-finding regression sequences passed and were
independently verified by the reviewer, closing REV001/REV002 with no remaining
actionable T1 findings. Generic 503 is verified at controller-model level;
cross-thread, callback-exception closure and proxy-DataSource edge paths were
source-reviewed without separate executed regressions.

Final checks: all 17 frozen input hashes match; local Markdown links resolve;
whitespace checks pass; exact changed-path inventory contains only the approved
T1 inputs, affected guides/traceability/reuse and iteration evidence. No build,
dependency, migration, AI or workflow configuration changes. Staged blobs are
checked against the frozen input manifest before commit.

## Limits and workflow evaluation

- Per-agent token/cached-token counts and exact individual wall times were not
  exposed: unavailable. No measured token-cost or latency saving is claimed.
- Existing H2 authentication/MVC tests verify rendered pages and role routing;
  disposable PostgreSQL tests verify SQL, migrations, roles, snapshots and
  retained history. Latest-Chrome/accessibility/performance/hosted checks remain
  separate T5/release evidence, not claimed from MVC assertions.
- Three test-setup correction rounds were needed before the final full check.
  Serial build ownership prevented checkout/build races, but API/fixture mistakes
  delayed execution. Per-test embedded PostgreSQL startup is a significant part
  of the scoped run's 3m3s duration. A future workflow experiment could validate
  fixture compilation/one least-privilege smoke case earlier; no configuration
  change or unmeasured improvement is inferred here.
- Source/tests/resources are new MP3 work using existing MP3 seams; no MP2 source
  was consulted/copied. Relative to baseline 172eaff, application Java changes
  are +599/-3 lines, resources +217/-0, and tests/fixtures +1,028/-0. These
  counts include new files and shared wiring/fixture edits; documentation and
  process evidence are excluded from these implementation totals.
- T1 commit is local evidence, not teammate or production approval. Existing
  gateway/human review gates remain pending where previously documented.

## Next gate

Report this iteration and preview T2 start-work/append-only Work Logs before
requesting explicit permission. T2 has not started; its command/audit acceptance
is not established by the read-only T1 checks.
