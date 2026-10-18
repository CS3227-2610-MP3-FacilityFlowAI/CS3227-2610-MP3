# Technician T1-B implementation handoff

- Source agent: T1-B Developer, gpt-6.1-sol / medium.
- Destination agent: coordinator/designated runner, Tester and independent reviewer.
- Task/goal: assigned dashboard/queue/detail/history backend reads.
- Approved requirement IDs: REQ-TECH-001–003/009, applicable REQ-SH-002/008–010/013.
- Approved specification revision/evidence: [T1 dispatch](../technician/T1-DISPATCH.md) records explicit user approval; [packet](../technician/T1-backend-task.md); [contracts](../../workflow/technician/CONTRACTS.md). Historical packet approval text is superseded by dispatch.
- Authorized scope/permissions: five named new backend files plus this durable/OS-temp handoff only; no shared identity/AI/schema edits, builds/tests, Git mutation or delegation. Preserve concurrent workers.
- Relevant input artifacts/provenance: CONTRACTS, Technician/Product/Technical specs, Developer role, identity principal/authorization and persisted filter seams, V1/V2/V4 schema inspected during T0; re-read dispatch/contracts/packet and build.gradle during T1.
- Files changed: under `src/main/java/sg/edu/nus/facilityflow/technician/`: `TechnicianQueryService.java`, `TechnicianQueryRepository.java`, `JdbcTechnicianQueryRepository.java`, `TechnicianReadModels.java`, `TechnicianQueryException.java`; this handoff and OS-temp `facilityflow-technician-T1-backend.md`.
- Implementation: service role snapshot followed by persisted exact active/TECHNICIAN/nonnegative session-version check; runtime-closed, thread-confined and authorized-actor-bound ReadSession. JDBC owns one same-DataSource REQUIRES_NEW, read-only REPEATABLE_READ transaction per callback. Counts, assignedTotal, categories, queue, detail and child histories use that snapshot/connection; histories repeat current-assignment predicates. All retained assigned states remain visible. Literal ILIKE search escapes !/%/_, AND filters bind parameters, priority/urgency explicit ranks precede assignedAt NULLS LAST/display ID. Counts ignore filters. DTO lists are defensive immutable copies; no Request/Account internal IDs or account entities exposed. Missing/malformed/unassigned detail has one safe failure vocabulary; persistence/transaction failures map to UNAVAILABLE without raw causes. Reads perform no audit/business mutations.
- Constructor/API handoff: `TechnicianQueryService(IdentityAuthorization, TechnicianQueryRepository)`; `JdbcTechnicianQueryRepository(DataSource)`; `TechnicianQueryException(Code)` and `code()`. All service/read-session/nested DTO signatures match CONTRACTS. QueueFilter normalizes null q to empty and strips outer whitespace. Session capability rejects access before authorization, actor substitution, cross-thread use and use after callback completion/failure.
- Assumptions: web owns parsing invalid raw filters and HTTP 400/404/503 mappings. Spring JDBC binds the repository's same-source operations to its explicit transaction. Test providers must supply realistic nonnegative CurrentAccountSnapshot versions; legacy -1 fallback intentionally denies.
- Commands/tools: Get-Content dispatch/packet/contracts/Developer role/build and source inputs; apply_patch for five owned files; Get-Item and rg source inspection for signatures, scope, isolation/order/session predicates. No builds/tests/database commands executed.
- Verification performed/results/evidence: static source read-through completed, files/API published to coordinator/UI/Tester. Runtime compilation, isolated PostgreSQL/MVC/unit tests and independent review pending designated runner. Do not treat this handoff as passing runtime evidence.
- Security considerations/unresolved risks: snapshot represents persisted state consistently as of its initial account query; future mutation behavior is outside T1. No known blocking issue from static inspection. Operational read exceptions have generic public text and no SQL-bearing causes.
- Suggestions accepted/rejected: exact approved contracts implemented; existing AI repository remains unchanged; no migration/dependency or generic role refactor introduced.
- Human approval required and reason: no new approval needed for scoped T1 verification/fixes; T2 and later remain separately gated. Teammate review still required before non-trivial merge.
- Human reviewer/outcome/evidence: pending.
- Next authorized action: coordinator freeze; designated single runner compiles/tests integrated candidate; independent reviewer checks authorization/snapshot/error boundaries; worker can repair assigned-file findings when directed.
- Status: ready for review; five backend files frozen awaiting runner/findings.

## T1-REV-001 correction

- Independent review found that failed reauthorization cleared the only actor binding, allowing a later retry to switch actor inside one callback.
- Changed only `JdbcTechnicianQueryRepository.java`: preserve first successful `boundActor` independently of revocable `authorizedActor`. Every reauthorization attempt clears current authorization; a different actor is always rejected after initial binding, even following a caught null/stale/different-actor failure. Reads remain denied following any failed attempt until the original actor successfully reauthorizes. Callback close still permanently rejects all operations.
- Verification: inspected patched guard/state transitions only; no tests/builds executed. Tester should cover A→B failure→B failure and A→null/stale failure→B failure, denied reads after failure, then successful original-A reauthorization. Fresh independent review and runtime regression execution remain pending.
- Status: corrected backend repository refrozen for designated runner/reviewer.

## T1-REV-002 correction

- Independent review found a nested REQUIRES_NEW callback could invoke a suspended outer ReadSession and resolve JDBC operations against the inner transaction's connection.
- Changed only `JdbcTechnicianQueryRepository.java`: retain the transaction manager's actual DataSource (including its TransactionAwareDataSourceProxy unwrapping), capture the exact bound resource identity at Session construction, and check that identity in requireOpen before authorization or data operations. A suspended outer capability is rejected while an inner resource is bound; it may resume when the original resource is restored. Thread/lifetime and T1-REV-001 binding checks remain.
- Verification: static review of guard coverage and resource identity logic only; no tests/builds. Fresh reviewer and designated runner should cover nested outer-session reads/reauthorization denied inside inner callback, inner authorized reads, outer resumption, and proxy DataSource behavior.
- Status: both reviewer corrections applied; backend repository refrozen for fresh review and runtime checks.
