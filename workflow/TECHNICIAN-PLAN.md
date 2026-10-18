# Local Technician Workflow Trial

Status: Technician-specific implementation workflow, configured 18 October 2026.
Original application baseline: 6b091532c40223a829e4aa1b70640b3a5c860413.
The user now requests committed configuration and separate iteration commits.
Each iteration requires a scope/agent/model/check preview and explicit user
permission. T0 was previewed and explicitly authorized after the configuration
commit; later iterations remain unapproved. Execution results belong under logs/.
Overlap between iterations requires authorization for both scopes. No push or
merge is authorized by this workflow.

Use [the Technician dispatch protocol](agents/technician-workflow.md) and approved requirements in
[ROLE-TECHNICIAN](specs/ROLE-TECHNICIAN.md), [PRODUCT-SPEC](specs/PRODUCT-SPEC.md)
and relevant [TECHNICAL-SPEC](specs/TECHNICAL-SPEC.md) sections. For AI work, also
read the role context/schema and security contracts. Preserve the recorded
pending human reviews of gateway revisions; do not report them approved.

## Iterations and ready tasks

User-selected model profiles: UI developer and Tester both use `gpt-6-luna` with
`high` reasoning effort, including planning assignments. Preview all other model
assignments per iteration. Model selection does not approve starting an iteration.

Each feature iteration uses backend (B), UI (U), and test-authoring (T) lanes after
contracts are agreed. One owner writes shared files. Tests can be authored early;
execution/review uses a fixed integrated candidate. Concrete task packets must
name exact paths before dispatch; directory labels below are responsibility only.

| Iteration | Scope and dependencies | Concurrent assignments | Exit evidence |
| --- | --- | --- | --- |
| T0 | Contracts; inspect current code/storage first | Concurrent read-only planning: backend (`gpt-6.1-sol`/medium), UI and Tester (both `gpt-6-luna`/high); coordinator reconciles methods/DTOs/routes, authorization, transactions, acceptance map and exact T1 ownership | Contract document, requirement-to-test map and T1 packets agree; no material ambiguity for T1; planning/evidence committed separately |
| T1 | Assigned dashboard, queue and detail; TECH-001-003/009; depends on T0 | B: scoped counts/search/filters/order/history reads; U: queue/detail and empty/reset states using fixtures; T: current assignment scope, priority/urgency/time/display-ID ordering, unknown times and history visibility | Usable assigned-only reads; TECH-A02 and sorting/filter/access tests pass |
| T2 | Start work and append logs; TECH-004-006/010-011; depends on T1 | B: persisted account/assignment/state/version checks and atomic business/audit writes; U: actions/forms and stale-state refresh; T: TECH-A01/A03/A06, note/minute boundaries, duplicate/stale/concurrent writes and audit-failure rollback | Valid start/log behavior; no unauthorized, duplicate or partial writes; append-only authorship retained |
| T3 | Completion; TECH-007-008/010-011; depends on T2 | B: log precondition, resolution validation, retained assignment/metadata/history and audit transaction; U: resolution form/errors/result; T: TECH-A04/A05/A06, missing log, invalid resolution, stale writes and rollback | IN_PROGRESS becomes COMPLETED only for valid authorized work; no automatic closure |
| T4 | Work Plan browser integration; TECH-012-017; depends on manual workflow | U: request/read-only display, prominent stop/escalation and safe failure/refresh; B: only demonstrated gaps in existing backend; T: TECH-AI01-04, eligibility, unsafe/injected output, timeout/rate limit and manual fallback | Advisory UI and manual fallback pass; existing context/safety/freshness controls retain evidence |
| T5 | Shared screens and acceptance; depends on integrated features | Assigned identity developer: own-password service/session behavior; U: shared navigation, password form, local dates, required fields/input preservation/pending submission and accessibility; T/review: browser lifecycle, forbidden actions and AI fallback | Scoped REQ-SH/NFR criteria covered, cross-role regressions pass, guides/traceability/reuse/evidence updated; human gates remain explicit |

The identity task may start after T0 when an existing slot is free and its
interfaces/ownership are fixed. Queue it separately; do not add a fourth worker.
It must respect the identity slice owner's accountability. Do not wait for it to
develop queue/work features. T4 UI preparation may overlap T3; manual-fallback
acceptance waits for integrated manual operations.

Shared requirements apply throughout, including persisted authorization, UTC,
safe forms/errors, transactions/audit and history visibility. T5 is final
acceptance, not permission to postpone these controls. Request/Account version
changes must remain compatible with existing Work Plan freshness checks. Reuse
existing storage and provider contracts; add migrations only for a demonstrated
gap, with named ownership and the existing review gate.

## Commit scope and evaluation

The configuration commit contains only AGENTS.md, the scoped pointer in
agents/README.md, agents/technician-workflow.md, this plan, the Technician task
packet template and configuration evidence. Shared role documents, the
specification index and the general handoff template retain their baseline
behavior. The user explicitly superseded the earlier uncommitted-trial policy.

T0 commits contracts, requirement-to-test mapping, T1 packets and actual agent
handoffs/evidence; it changes no application code, migrations or product behavior.
T1 and later iterations commit verified implementation and run evidence using
explicit paths. Inspect the entire staged diff before every commit and preserve
unrelated work. Record configuration changes separately when needed.

To discontinue this workflow, make a reviewed follow-up change to its scoped
pointers/documents. Preserve implementation commits, shared workflow guidance
and evidence. Do not reset history or overwrite teammate edits.

Measure per-iteration elapsed time, total tokens when exposed, repair cycles,
completed criteria and regressions. Compare equivalent accepted scope; mark
unavailable metrics explicitly. Configuration/link checks do not establish
runtime savings or successful multi-agent execution.
