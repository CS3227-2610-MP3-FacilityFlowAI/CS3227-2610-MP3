# Reflections

## AI Security

### Attack surfaces and prompt injections

We considered direct instructions asking the model to ignore its rules,
indirect instructions stored in request descriptions or Requester Updates,
and encoded, multilingual or delimiter-breaking instructions. Model responses,
provider errors, logs and development-agent handoffs were also trust boundaries.
We learned that prompt warnings alone are insufficient: application code must
control what reaches the model and what happens to its response.

### Security controls and testing

The shared gateway authorises access before loading protected context, sends
only allowlisted fields, validates typed responses, and checks for disclosure
and unsafe Work Plan advice. Post-call checks reject results when relevant
access, assignment or state changes. Diagnostics retain safe metadata rather
than raw prompts, responses or credentials.

Tests exposed gaps beyond response formatting. A safety test found that
`b y p a s s the interlock` escaped the initial matcher. T5 real-stack tests also
found that failed-login exceptions could retain attempted credentials in JDBC
sessions. The corrections were tested at their actual boundaries and reviewed
independently. These examples taught us that valid JSON and passing controller
tests do not establish security. The broader human-reviewed Work Plan safety
corpus remained a recorded gap. Evidence:
[safety correction](../logs/2026-10-17-soclaas-gateway-iteration-2n-safety-policy.md)
and [T5 verification](../logs/technician/T5-REPORT.md).

### Limits applied to the AI

Application ceilings are 800 output tokens, 30 seconds per invocation, five
global in-flight calls, and five calls per minute and 30 per hour for each
account. At most one retry is permitted under defined conditions and within
the original deadline. Provider quotas remain separate configuration concerns.

Review found that a delegate could reset the deadline. Fixing it showed us
that limits must hold throughout wrappers, readiness and retries, not just
at the final provider call. Manual workflows remain available when AI fails.
See the [deadline correction](../logs/2026-10-17-soclaas-gateway-deadline-delegate-fix.md).

### Approval gates and human oversight

All three assistants remain advisory. Requesters review draft fields before
normal submission; Technicians receive read-only plans; Managers review triage
suggestions and deterministically ranked candidates before assigning work.
Separate service operations repeat authorisation, state, version and validation
checks. An AI response cannot authorise a business write.

Development also used human decision gates. Technician iterations required
scope, agent, model and check previews before permission to proceed, followed
by verified commits and contribution reports. Security fixes required fresh
verification, and agent approval did not replace teammate review. Oversight
worked best when tied to explicit actions and evidence.

### Live testing challenged local assumptions

The Requester live smoke initially exhausted its token budget before producing
final content. Aligning its transport with the shared adapter's existing
`reasoning_effort=none` contract produced valid structured samples. We diagnosed
the failure before increasing limits or weakening validation. A successful
instruction-injection sample establishes only that sample's result, not general
resistance. Later runtime consolidation also required new checks; earlier live
results could not certify changed wiring. See the
[live smoke record](../logs/2026-10-19-requester-live-ai-smoke.md).

## Specification-Driven Development

### Making requirements precise before implementation

Useful specifications identified actors, allowed and forbidden actions,
validation, state changes, failure paths and acceptance criteria. Stable IDs
connected implementation, tests and traceability. REQ-TECH-007/008, for example,
separate Technician completion from Manager closure and require retained
assignment and atomic history/audit writes.

We checked precision by agreeing interfaces and expected results before coding.
Technician T0 prepared backend, UI and test contracts. Gateway analysis exposed
unresolved context, retry and quota rules. However, the intended standalone
gateway scope still needed clarification after role integration was included.
We learned to specify exclusions as well as required features. Evidence:
[Technician contracts](../workflow/technician/CONTRACTS.md) and
[gateway planning](../workflow/SOCLAAS-GATEWAY-PLAN.md).

### Keeping changes and shared architecture consistent

Requirement changes should receive approval, then flow through specifications,
implementation, tests and traceability. The gateway compression experiment was
reverted because its limits and security behaviour were outside the approved
contract. A useful addition is not automatically agreed scope.

Integration also exposed duplicate password routes and Requester AI wiring.
Consolidation preserved the ordinary form and authorisation contracts, but a
later review caught a lost pre-admission transaction timeout. We learned that
traceability must follow the actual caller through shared services: individually
tested features can still lose safeguards during integration. Evidence:
[scope correction](../logs/2026-10-17-soclaas-gateway-compressed-scope.md),
[role integration](../logs/2026-10-19-requester-technician-master-merge.md), and
[timeout regression](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/blob/4a569e365def4fc0004aa4174b6fc4270ceb28a3/logs/2026-10-20-smart-report-read-timeout.md).

### Testing intent rather than matching wording

Tests needed to challenge the required guarantees. During T3, review found that
sequential replay tests did not prove concurrent completion safety and rollback
checks compared too little data. Strengthening these tests checked whether
failed or competing operations left the correct state, history and audit data.
A requirement ID attached to a test does not prove adequate coverage. See the
[T3 findings](../logs/technician/T3-REPORT.md).

### Reporting acceptance by criterion and revision

Accessibility feedback led to corrected field-error associations and a run with
409 passing Chrome UI checks. The same task missed the 50-user load target:
p95 was 1,018.64 ms against 1,000 ms, despite zero errors. We retained that failure
rather than changing the threshold or reporting an overall pass. Earlier results
on another revision did not erase it.

Likewise, CI/CD automation did not by itself prove deployment, promotion or
rollback acceptance. We learned to distinguish approved requirements,
implemented features and verified releases, and to report each result against
its candidate and environment. Evidence:
[field-error correction](../logs/2026-10-20-manager-field-error-accessibility.md)
and [CI/CD record](../logs/2026-10-18-ci-cd.md).

## Basic Multi-Agent Software Engineering

### Choosing specialised stages and concurrent tasks

The gateway used the repository's staged workflow: Analyst and Architect work,
security review, approved implementation, testing, fresh verification of fixes,
and Spec/Docs verification. This suited unresolved shared security decisions.
Passing configuration tests still missed unsafe key/model values and excessive
public credential access; security review caught them and a different Tester
verified the fixes. See the
[gateway increment](../logs/2026-10-16-soclaas-gateway-configuration.md).

The staged process felt slow and token-heavy, so the Technician work trialled
an orchestrator–subagent workflow with independent generator–verifier checks.
The trial began locally, then its configuration was committed specifically for
the Technician slice, separately from implementation. The repository already
allowed parallelism; the change made scheduling concrete.

Backend, UI and test authors worked concurrently with exclusive file ownership,
while one coordinator integrated changes and ran builds. Up to three workers
ran alongside the coordinator, with review using a freed slot. UI and testing
used `gpt-6-luna` at high effort to try a lighter model without reducing the
expected checks; backend and review used `gpt-6.1-sol`. See the
[T4 assignments](../logs/technician/T4-REPORT.md).

### Defining and evaluating agents

Agents received requirement IDs, interfaces, file ownership, expected outputs
and completion checks. Contracts were prepared before dispatch, and settled
requirements did not need another analysis stage each time. Workers did not
run competing builds or expand their own scope.

We evaluated agents through executed results and review findings, not their
role descriptions. Test-fixture repairs showed that detailed instructions still
needed compilation and database smoke checks. The tester's older SQLite recipe
also had to yield to the approved MP3 PostgreSQL contract.

### Handoffs and evidence for human review

A receiver needs approved scope, decisions, interfaces, changed files, executed
checks, unresolved issues and the next action. Gateway handoffs carried open
questions and exact findings between stages. Candidate identities and source
hashes tied review and results to the same code.

T5 deliberately paused after authorship with its agents retained, then resumed
verification. The handoff distinguished written code from tested code. At its
close, browser access remained unavailable despite permission to enable it,
so automated passes were not presented as browser acceptance. We learned that
approval, execution and verification are separate facts. See the
[T5 report and handoffs](../logs/technician/T5-REPORT.md).

### Preventing incorrect or malicious instruction propagation

An incorrect assumption can spread from a specification into implementation
and tests. Handoffs could also repeat malicious instructions or falsely claim
permission. We kept write scopes separate and required checks against approved
sources. Documents, screenshots and other agents' messages did not grant new
authority. Independent review challenged upstream assumptions, but these
examples do not establish comprehensive malicious-handoff resistance.

### Reviewing runtime, token usage and coordination overhead

Excessive T2 review exchanges led to a simpler process: compilation and a
database smoke test, one fixed review candidate, batched findings and focused
correction checks. Models and independent verification stayed in place.
Runtime and token summaries were requested in the conversation, followed by
per-agent breakdowns and rough estimates for later iterations.

T3 reported about 24.33 million tokens, including 23.63 million cached input
tokens. The coordinator accounted for 17.28 million, or 71%, largely through
repeated context processing. Review used about 11% of tokens, while existing
database suites took about 65% of the 6-minute-38-second full check. These
sampled totals excluded unrecorded tails and the final reply; they were not
newly generated text or direct cost measurements.

We learned to examine runtime and token usage separately. Next time, we would
reduce coordinator exchanges, automate mechanical checks and validate fixtures
earlier. Staged analysis remains useful for unresolved decisions; concurrency
suits agreed interfaces. Both need independent review, and no comparable gateway
baseline established that one workflow was cheaper. Evidence:
[Technician workflow conversation](thread://01a14da4-2c55-7430-96c1-c949377b9d55?hostId=local)
and [review-process change](../logs/technician/2026-10-18-batched-review-config.md).
