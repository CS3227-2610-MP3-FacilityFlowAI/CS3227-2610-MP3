# Technician Implementation Contracts

Status: T0 design, 18 October 2026. Approved product behavior remains in
[ROLE-TECHNICIAN](../specs/ROLE-TECHNICIAN.md),
[PRODUCT-SPEC](../specs/PRODUCT-SPEC.md) and
[TECHNICAL-SPEC](../specs/TECHNICAL-SPEC.md). Interface names, route aliases and
view composition below are implementation choices, not new product requirements.
T0 plans only; T1 requires separate user approval. Existing gateway review gates
remain pending. Authority and actual agents: [T0 dispatch](../../logs/technician/T0-DISPATCH.md).

## T1 use-case boundary

Add five backend files under `src/main/java/sg/edu/nus/facilityflow/technician/`:
`TechnicianQueryService.java`, `TechnicianQueryRepository.java`,
`JdbcTechnicianQueryRepository.java`, `TechnicianReadModels.java`,
`TechnicianQueryException.java`. Keep read records/enums nested in
`TechnicianReadModels`. The web layer calls the service/models only, never SQL.

```java
// TechnicianQueryService: no browser-supplied actor/account identifier.
QueueView queue(QueueFilter filter);
RequestDetail detail(String displayId);

// TechnicianQueryRepository: explicit transaction-scoped read context.
<T> T readSnapshot(Function<ReadSession, T> reader);
// ReadSession, valid only inside the callback:
void requireCurrentTechnician(CurrentAccountSnapshot actor);
DashboardCounts counts(UUID actorId);
long assignedTotal(UUID actorId);
List<CategoryOption> categories(UUID actorId);
List<QueueRow> queue(UUID actorId, QueueFilter filter);
Optional<RequestDetail> detail(UUID actorId, String displayId);
```

Use a read-only REPEATABLE_READ transaction for each service call; all account,
assignment, count, category and child-history queries use that same snapshot and
connection. `ReadSession` is invalid outside the callback, including after
failure. The service captures `IdentityAuthorization.requireRoleSnapshot` for
TECHNICIAN and rechecks persisted account active state, role and exact session
version inside the snapshot before reading Request data. A principal check alone
is insufficient: the current provider reads the principal, while the web filter
performs the existing database refresh. Direct service calls need the same
persisted protection. Runtime snapshots use valid nonnegative session versions;
no negative-version bypass. Test providers supply realistic synthetic snapshots.

## T1 data contract

The records below are immutable; list components are non-null defensive copies.
Optional filter fields and optional persisted metadata may be null as indicated.
No Account entity, password hash, audit payload or Request/Account internal ID
crosses into a view. Category UUIDs are allowed only as stable filter identifiers.

```java
QueueFilter(String q, RequestStatus status, UUID categoryId, ManagerPriority priority);
DashboardCounts(long assigned, long inProgress, long completed);
CategoryOption(UUID id, String name);
QueueRow(String displayId, String title, String location, UUID categoryId,
         String category, RequestStatus status, ManagerPriority priority,
         ReportedUrgency urgency, Instant assignedAt);
QueueView(QueueFilter filter, DashboardCounts counts, List<CategoryOption> categories,
          long assignedTotal, List<QueueRow> rows);
RequesterUpdateView(String note, String authorDisplayName, Instant createdAt);
WorkLogView(String note, int minutes, String authorDisplayName, Instant createdAt);
RequestDetail(QueueRow request, String description, Instant createdAt, Instant updatedAt,
              long version, String resolutionSummary, Instant completedAt,
              List<RequesterUpdateView> requesterUpdates, List<WorkLogView> workLogs);
```

`RequestStatus`: OPEN, ASSIGNED, IN_PROGRESS, COMPLETED, CLOSED, CANCELLED.
`ManagerPriority`: LOW, MEDIUM, HIGH, CRITICAL. `ReportedUrgency`: LOW, NORMAL,
HIGH, EMERGENCY. Enum values match existing V2 storage; labels are presentation.
`q` is normalized to an outer-trimmed non-null string (blank means no search).
`assignedAt`, `priority`, `resolutionSummary` and `completedAt` tolerate existing
nullable data. Version and Instant values are persisted values, not UI-generated.

## T1 query semantics and failures

- Every row/detail/history query is scoped to the currently assigned Account.
  Retained COMPLETED/CLOSED/CANCELLED assignments remain readable. Do not add a
  default three-state restriction. Dashboard counts distinguish ASSIGNED,
  IN_PROGRESS and COMPLETED-awaiting-review across the entire assigned scope,
  independent of filters. `assignedTotal` includes all scoped states.
- Search is a case-insensitive literal substring across display ID, title and
  location. Bind parameters and escape SQL LIKE wildcards (`%`, `_` and the
  selected escape character). Apply optional status/category/priority filters
  with AND. Category options are distinct categories in the assigned scope,
  including inactive categories still referenced there. A valid unknown category
  UUID gives zero matches. There is no new Untriaged filter or pagination in T1.
- Default order: priority Critical to Low (null last), urgency Emergency to Low,
  assignedAt oldest first with unknown times last within the preceding sort
  keys, then display ID ascending. Use explicit enum ranks. A null-time Critical
  request still precedes a known-time Low-priority request.
- Detail includes original Request facts, current completion metadata, all its
  Requester Updates and Work Logs with original author display names. Sort each
  history list by creation time then immutable ID ascending. Current assignees
  may read prior technicians' logs on this Request. No private Manager/audit data
  or invented status-event feed is exposed. Historical completion lists can be
  added with T3 if required; T1 does not create that additional read API.
- `TechnicianQueryException.Code`: INVALID_FILTER, NOT_AVAILABLE, UNAVAILABLE.
  Invalid filter enum/UUID gives HTTP 400, specific field errors and preserved
  submitted values, never an unfiltered fallback. No protected results are
  rendered for that failed query. Invalid display-ID syntax, unknown and
  unassigned details all give the same safe HTTP 404/message. Operational read
  failure gives a generic 503 and a retry path, not an empty-success view.
  Identity exceptions retain existing login/403 behavior. Read paths never mutate
  business/audit records. Domain errors contain no SQL or inaccessible fields.

## T1 web contract and ownership

`TechnicianQueueController.java` owns GET `/technician/requests` and GET
`/technician/requests/{displayId}`. `/technician` stays HTTP 200 and renders the
same combined dashboard/queue view with an empty filter; the coordinator wires
this existing shared route through `DashboardController` to avoid duplicate
mapping and preserve Requester/Manager routes. No separate dashboard template.

Queue parameters: optional `q`, `status`, `category`, `priority`; the public
`category` parameter binds a category UUID. Enum query values use their storage
tokens. Queue attributes: `title`, `username`, `displayName`, `role`, `queue`,
`queryErrors`, `submittedFilters`, `displayZone`. Detail substitutes `detail`
for `queue`. Reuse escaped identity headers; `displayZone` is Asia/Singapore for
unambiguous local date/time/zone display while DTO Instants remain UTC. For bad
filters, preserve escaped raw form values and show no rows/counts from a broader
query. `assignedTotal == 0` means no assigned work; positive total with no rows
means no matches. The reset link is `/technician/requests` with no parameters.

UI owns new templates `technician/queue.html`, `technician/request-detail.html`,
`technician/fragments/request-rows.html`, `technician/fragments/history.html`
under `src/main/resources/templates/`, and the new controller only. Queue shows
the three count cards, labeled filters, result count/reset and distinct empty
states. Detail separates facts, Requester Updates and Work Logs; null metadata
has an explicit label. Free text is escaped, whitespace preserved, and controls
are keyboard accessible. Back-to-queue may use a fixed safe route; preserving
filters is optional T1 polish, never an unchecked return URL.

Coordinator alone owns edits to existing `DashboardController.java`,
`SecurityConfiguration.java` if genuinely needed, existing shared security tests,
guides, TRACEABILITY and REUSE. Keep the Requester/Manager placeholder template
unchanged. No password/about dead links, manual-write controls or Work Plan
controls are added in T1; shared navigation completeness is a T5 acceptance item.

Tester owns three new T1 files: `technician/TechnicianQueryServiceTest.java`,
`technician/JdbcTechnicianQueryRepositoryTest.java`,
`web/TechnicianQueueControllerTest.java` under `src/test/java/sg/edu/nus/facilityflow/`.
If a reusable fixture is needed, its only reserved extra path is
`technician/TechnicianQueryFixtures.java`. History cases live in these tests,
not separate history service/repository modules. Build/dependency changes need
coordinator ownership; existing JUnit/Spring/embedded PostgreSQL dependencies
are sufficient starting points. See [acceptance map](ACCEPTANCE-MAP.md).

## Later iteration boundaries

T2/T3 command use cases will re-read/lock persisted account and Request in one
transaction, enforce current assignment/state/expected version, then append
business/history/audit changes atomically. Start is ASSIGNED to IN_PROGRESS;
log is IN_PROGRESS only, note 1-1000 trimmed code points and 1-1440 whole minutes;
complete is IN_PROGRESS to COMPLETED with an existing log and a 10-2000-code-point
resolution. Every successful command increments Request.version, including log
append, so in-flight Work Plan output becomes stale. Duplicate/stale commands
write nothing. Completion retains assignment and appends completion history.
Audit contains only safe structured metadata. A former assignee gets safe
refresh guidance, never an automatic reread disclosing the now-unassigned record.
Do not expose generic status setters or role-forbidden operations.

T4 consumes the existing typed Work Plan endpoint/schema; AI remains read-only,
with prominent safety/stop sections, post-call freshness checks and manual
fallback. No new provider contract/playbook retrieval is planned. T5 adds shared
identity/navigation/browser acceptance with separately approved ownership.
No schema migration, dependency upgrade or general role refactor is justified by
T0. Existing V2/V4 data support T1; demonstrate a gap before changing storage.

## Reconciliation and acceptance

Accepted backend snapshot/version/ordering design. Reconciled UI proposals to
one dashboard/queue view, all retained assigned states, assigned-scope category
options, exact backend DTO names and no private audit feed/Untriaged filter.
Consolidated proposed history tests into the three T1 files. These are design
resolutions consistent with the approved rules, not additional product behavior.
No blocking T1 product ambiguity was found; runtime behavior remains unverified.

Ready packets: [backend](../../logs/technician/T1-backend-task.md),
[UI](../../logs/technician/T1-ui-task.md), [tests](../../logs/technician/T1-tests-task.md).
Only their future dispatch, not their preparation in T0, requires T1 approval.
