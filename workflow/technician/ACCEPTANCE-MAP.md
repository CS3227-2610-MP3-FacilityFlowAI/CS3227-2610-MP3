# Technician acceptance map (T0)

Source: approved `workflow/specs/ROLE-TECHNICIAN.md`, shared lifecycle/limits in
`PRODUCT-SPEC.md`, test layers in `TECHNICAL-SPEC.md`, iteration boundaries in
`workflow/TECHNICIAN-PLAN.md`, and integrated T1 decisions in
`workflow/technician/CONTRACTS.md` and its three task packets. T1 test names and
scope are reconciled with those contracts; later iteration names remain
proposals. No application tests were authored or run during this planning task.

| Requirement | Iteration / proposed test location | Normal, forbidden, boundary and failure coverage; layer |
|---|---|---|
| REQ-TECH-001 | T1 `technician/TechnicianQueryServiceTest`, `technician/JdbcTechnicianQueryRepositoryTest`, `web/TechnicianQueueControllerTest` | Assigned-only reads, retained CLOSED/CANCELLED assignments; three unfiltered dashboard counts (ASSIGNED, IN_PROGRESS, COMPLETED awaiting review) plus `assignedTotal` across all assigned states. Cross-assignee denied; no work vs zero matches distinct; persisted assignment/account/session snapshot changes checked. Unit + isolated PostgreSQL + MVC. |
| REQ-TECH-002 | T1 same three query/controller tests | Search ID/title/location and status/category/priority AND filters/reset; trimmed case-insensitive literal substring, `%`, `_`, escape-character are literal; blank and zero matches. Valid unknown category UUID returns zero rows; malformed category/enum gives 400 and must not broaden results. Unit + PostgreSQL + MVC. |
| REQ-TECH-003 | T1 query service/repository tests | Priority Critical→Low, urgency Emergency→Low, then assignment time oldest-first (null last only within equal preceding priority/urgency keys), display ID tie-break; deterministic equal ties and migrated unknown time. PostgreSQL ordering integration + unit fixtures. |
| REQ-TECH-004 | T2 `technician/TechnicianWorkServiceTest`, `technician/JdbcTechnicianWorkRepositoryTest` | Assigned active Technician starts ASSIGNED, one audit; wrong role/assignee/state, repeated or stale version rejected; state/audit atomicity. Unit + PostgreSQL transaction. |
| REQ-TECH-005 | T2 same service/repository tests | Current assignee appends only in IN_PROGRESS; former/wrong assignee and ASSIGNED/terminal state denied; reassignment race rejected. Unit + PostgreSQL concurrency. |
| REQ-TECH-006 | T2 `technician/WorkLogValidationTest`, repository tests | Append preserves text, author and UTC time; 1/1000-code-point note and 1/1440 minutes accepted; blank/1001/0/1441 rejected; prior row immutable; audit failure rolls back. Unit + PostgreSQL. |
| REQ-TECH-007 | T3 `technician/TechnicianCompletionServiceTest`, repository tests | Valid log + valid summary completes; no log, blank/oversize summary, wrong actor/state/stale version rejected; no partial state/audit. Unit + PostgreSQL rollback. |
| REQ-TECH-008 | T3 completion repository/service tests | Retains assignee and completion metadata/history for Manager review; does not close; duplicate/stale completion creates no extra history/event. Unit + PostgreSQL. |
| REQ-TECH-009 | T1 same query/service, repository and controller tests (no separate history module) | Current assignee sees Requester Updates and all Work Logs on that assigned Request, including logs by prior Technicians; unrelated Request details/logs remain hidden. Reassignment grants current Request access without changing historical author names. Test generic same 404 for malformed/unknown/unassigned detail; operational whole-read failure is generic 503, never partial/empty success. Unit + PostgreSQL + MVC. |
| REQ-TECH-010 | T2 and T3 service/repository tests | Reassignment between read and write rejects former Technician safely and returns refreshed current state; simultaneous writes, no mutation/audit duplication. Unit + PostgreSQL concurrency/MVC stale response. |
| REQ-TECH-011 | T2/T3 `web/TechnicianForbiddenActionsTest`, service tests | Direct POST attempts to assign, reprioritize, edit original fields, cancel, close, reopen or set arbitrary status denied; no state/audit change. MVC security + service boundary. |
| REQ-TECH-012 | T4 existing `technician/TechnicianWorkPlanServiceTest`; extend `ai/technician/WorkPlanAuthorizationBoundaryTest` | Active currently assigned Technician on ASSIGNED/IN_PROGRESS permitted; inactive/wrong role/unassigned/COMPLETED/CLOSED/CANCELLED denied before provider call. Unit + MVC. |
| REQ-TECH-013 | T4 existing `ai/provider/SocLlmWorkPlanAssistantTest`; add `technician/JdbcTechnicianWorkPlanRepositoryTest` | Exact allowlist and latest ten Updates ordered created-time DESC, immutable ID DESC then sent oldest-first; exclude logs/IDs/accounts/audit/playbook; boundary 0/10/11, ties, oversize rejected without truncation. Unit + isolated PostgreSQL. |
| REQ-TECH-014 | T4 existing `ai/provider/SocLlmWorkPlanAssistantTest`, `ai/provider/WorkPlanSafetyPolicyTest` | Typed exact schema rendered as read-only advice; malformed/extra/missing fields rejected, no partial display. Unit + MVC. |
| REQ-TECH-015 | T4 `web/TechnicianWorkPlanControllerTest`, `technician/TechnicianWorkPlanServiceTest` | Request plan creates no state/log/tool reservation; advice selection remains client-side only; direct mutation cannot be triggered by AI endpoint. MVC + service boundary. |
| REQ-TECH-016 | T4 existing safety tests plus `web/TechnicianWorkPlanControllerTest` | Prominent concrete stop/escalation shown; missing/empty/generic or unsafe safety rejected with no plan and manual fallback. Unit + MVC. |
| REQ-TECH-017 | T4 existing `technician/TechnicianWorkPlanServiceTest`; extend `web/TechnicianWorkPlanControllerTest` | Provider failure, timeout, rate limit, safe rejection preserve manual actions and persist no partial plan/business change; changed active/role/assignment/state/version discards result as `DISCARDED_ACCESS_CHANGED`; verify safe refresh. Unit + MVC. |

## Existing evidence and gaps

`TechnicianWorkPlanServiceTest` already covers role/invalid-ID denial before
loading, stale eligibility/session snapshot, limit, deadline and telemetry
failure. `SocLlmWorkPlanAssistantTest` covers latest-ten ordering, context
exclusions, typed response, malformed/unsafe output and provider unavailability;
`WorkPlanSafetyPolicyTest` exercises dangerous, obfuscated and multilingual
content plus concrete stop guidance. `WorkPlanAuthorizationBoundaryTest`,
`AiAccessGuardTest`, `SecurityIntegrationTest` and provider adversarial tests
add auth/security/failure evidence. These do not establish new Technician
manual queue, lifecycle, append-only, completion, reassignment, history, or
Technician page/fallback behavior. `SecurityIntegrationTest` currently uses
H2/PostgreSQL mode; SQL/transaction acceptance for new Technician persistence
must use the existing disposable embedded PostgreSQL harness pattern in
`database/DatabaseMigrationTest`, with synthetic fixtures and no live DB/SQLite.

## T1 test fixture and later commands

T1 should use the three contract-owned classes only:
`TechnicianQueryServiceTest`, `JdbcTechnicianQueryRepositoryTest`, and
`TechnicianQueueControllerTest`; history cases live in these tests. Seed at
least six assigned Requests across two Technicians, statuses, priorities,
urgencies and categories: equal-priority/urgency records with different times,
equal-time rows with distinct display IDs, and one legacy null assignment time.
Assert full expected order and that null time sorts after known time only when
the preceding priority/urgency ranks tie. Also assert one current-assignee
Request containing a prior Technician's Work Log; retain an assigned CLOSED
and CANCELLED row; verify state counts versus all-state `assignedTotal`;
exercise literal wildcard search, valid unknown category (zero rows) and
malformed filter (400). Use deterministic UTC data and isolated embedded
PostgreSQL; never use live DB, SQLite or wall-clock ordering. Service/repository
tests should prove account active/role/exact session-version check and queue,
counts, categories, details and histories share one read-only repeatable
snapshot; controller tests prove generic 404 and whole-read 503 behavior.

Run after T1 implementation/contracts settle: `./gradlew.bat test --tests
"sg.edu.nus.facilityflow.technician.TechnicianQueryServiceTest" --tests
"sg.edu.nus.facilityflow.technician.JdbcTechnicianQueryRepositoryTest" --tests
"sg.edu.nus.facilityflow.web.TechnicianQueueControllerTest"`. Run
`./gradlew.bat test --tests
"sg.edu.nus.facilityflow.database.DatabaseMigrationTest"` when migration
coverage is needed. At T4 also run `./gradlew.bat test --tests
"sg.edu.nus.facilityflow.ai.technician.*" --tests
"sg.edu.nus.facilityflow.ai.provider.*WorkPlan*Test"`. Coordinator runs the
integrated `./gradlew.bat check` on a frozen candidate. Commands are proposed,
not execution evidence.
