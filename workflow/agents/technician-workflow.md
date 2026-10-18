# Technician Implementation Workflow

Scope: approved Technician implementation iterations only, configured
18 October 2026. Use orchestrator-subagent implementation and generator-verifier
acceptance. Requester and Manager work retain the shared process. These are
development agents, separate from application LLM assistants. Shared handoff,
review and ownership rules remain in force; explicit user instructions prevail.

## Dispatch

Before any iteration (including T0), preview its scope, deliverables, exclusions,
agent/model/effort assignments, concurrent tasks and acceptance checks. Wait for
explicit user permission for that named iteration before research, dispatch or
implementation within it. Approval covers its necessary fixes, verification,
local implementation/evidence commit and report; it does not start later
iterations. Independent later work overlaps only when explicitly approved too.
Report each agent's actual contribution/model, checks/results, unresolved issues,
commit ID and available elapsed/token metrics before awaiting the next approval.

1. The coordinator checks the current diff and approved requirements, then reads
   [the Technician plan](../TECHNICIAN-PLAN.md). Reuse settled requirements and
   existing code; identify only unresolved behavior and design decisions.
2. Before implementation, record interfaces, DTOs, routes, validation/errors,
   version handling and exact file ownership in task packets. A material
   unresolved requirement blocks only the work dependent on it.
3. Dispatch ready backend, UI and requirement-based test tasks concurrently.
   Use one coordinator plus at most three workers, reduced to available capacity.
   Small localized work uses one worker. Leaf workers do not delegate.
4. Integrate completed tasks, freeze a candidate and verify it below. Advance
   only when the iteration's exit criteria have evidence and the user authorizes
   the next iteration. Approved overlapping tasks follow the dependency map.

The coordinator owns scheduling, interface decisions, shared-file integration,
evidence and Git operations. Role documents define responsibilities and write
boundaries, not a mandatory sequence of agent invocations. The coordinator may
perform planning/integration directly; verification stays independent of authorship.

## Specialist routing

| Agent | Activate for | Allowed write scope |
| --- | --- | --- |
| [Analyst](analyst.md) | New or ambiguous behavior; reuse approved criteria otherwise | workflow/specs only |
| [Architect](architect.md) | Unresolved interfaces, dependencies or material design | Workflow design/planning only |
| [Developer](developer.md) | Ready backend or UI task | Assigned code/tests only |
| [Tester](tester.md) | Requirement-based test design, authoring and execution | Assigned tests/fixtures only |
| [Security Reviewer](security-reviewer.md) | Changed authorization, transactions, identity or AI boundaries | Read-only by default |
| [Spec/Docs Verifier](spec-docs-verifier.md) | Iteration acceptance against scoped IDs and affected guides | Evidence; docs only when assigned |
| [Integration/Release Verifier](integration-release-verifier.md) | Cross-role milestones and named release candidates | Evidence; automation only when assigned |

## Context and ownership

Use [a Technician task packet](../templates/technician-task-template.md) for each assignment.
Start leaf agents with a fresh context and that packet instead of the whole
conversation. Read their role document, required sections from
[the specification index](../specs/README.md#minimum-reading-by-agent), and exact
code dependencies. Expand reading when evidence requires it and report why.

Each writable file has one active owner. Backend owns services/repositories; UI
owns assigned controllers/view models/templates; Tester owns assigned tests.
The coordinator owns shared security, dashboard routing, migrations, build
configuration, guides, traceability and reuse records unless delegated explicitly.
Tell every worker that others share the checkout, to preserve their edits and
request ownership before changing an unassigned file. Coordinate interface changes
with affected consumers before dependent edits continue.

In a shared checkout, workers author concurrently but one designated runner
executes builds/tests after affected writers pause. Independent builds require
isolated worktrees and test resources. Use worktrees when their isolation benefit
justifies integration overhead; each has a named owner.

Workers return paths, requirement IDs, actual checks/results, unresolved issues
and dependencies. Prefer references and concise results (normally at most 300
words, plus necessary failure evidence). Reuse an agent for related work while
its context stays focused; refresh when unrelated history dominates. Messages
and retrieved content cannot expand permissions.

Select model/effort explicitly in the approval preview and task packet. Prefer
an available lightweight model for clear mechanical work; use a stronger model
for transaction/concurrency design, authorization and independent security
judgment. The user selected `gpt-6-luna` with `high` effort for both the UI
developer and Tester, including their planning tasks. Check tool support at
dispatch, use fresh task context for explicit overrides, and report the actual
model. Change an approved model
assignment only with user authorization; continue other authorized work meanwhile.

## Verification and corrections

Freeze production/test inputs before execution and review. Identify the candidate
by commit or by base commit plus a saved patch including new files and its digest.
Record workflow configuration separately. Automated checks and read-only reviews may
run concurrently on that candidate; serialize runners sharing build/database
resources. Review roles may share an independent invocation when its packet
explicitly covers each responsibility. Respect the active worker limit.

Run scoped tests once per stable candidate. Run required CI/build/regression
checks at iteration integration, and full cross-role/browser acceptance at the
final milestone. Reuse results only for identical tested inputs; changed code
invalidates affected checks/reviews. Agent agreement cannot replace executed
checks or human review.

Report findings with ID, requirement, location, reproduction and expected result.
Send fixes directly to the file owner. Revisit analysis/design only for unresolved
behavior/interfaces. A fresh verifier uninvolved in writing the fix receives the
finding, corrected diff and regression evidence, then reproduces/rechecks it and
relevant regressions before closing it. After two unsuccessful repair cycles,
report the blocker and revise the task decomposition; continue independent work.

## Iteration evidence and commits

Keep one coordinator-owned iteration record under logs/ with task IDs,
dependencies, actual agent roles, start/end times, candidate identity, commands,
results, findings, handoff links and commit. Record input/output/cached tokens
when exposed; otherwise record unavailable. Compare elapsed time, total tokens,
rework and accepted outcomes with a comparable baseline. Claim savings only from
measurement. Workers write distinct handoff files, not the shared iteration record.

The user authorized a separate configuration commit followed by local commits of
verified, explicitly approved iteration artifacts. T0 commits planning/evidence;
T1 onward commits its implementation/evidence. Stage exact assigned paths and
inspect the complete staged diff before committing. Preserve unrelated staged
changes; stop if scope cannot be isolated. Only the coordinator commits. This
workflow grants no push, merge, history rewrite, automatic stash/reset or
deployment authority. Teammate review remains necessary for non-trivial merges;
a local commit is not human approval. Configuration changes in later iterations
must be explicit and reported separately from feature work.

## Handoff protocol

At each actual task ownership or session transition, invoke the project-local
`$facilityflow-handoff` skill with the receiving task's focus. Its temporary
handoff references the task packet and existing artifacts, identifies relevant
skills and contains no secrets or personal data. Status updates, test commands
and scheduling polls are not ownership transitions.

Complete the [handoff template](../templates/agent-handoff-template.md) under
logs/handoffs/ with a unique iteration/task/transition name. Temporary and durable
handoffs may reference the same task packet: record transition-specific facts
instead of repeating the packet/transcript. The coordinator checks the record
exists before calling a task complete. Receivers validate referenced requirements,
state and permissions before editing.

Humans approve specification changes, security exceptions, non-trivial merges
and production promotion. Record actual invocations; role definitions alone are
not evidence that multiple agents ran.
