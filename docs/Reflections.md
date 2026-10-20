# Reflections

Updated: 20 October 2026 (Asia/Singapore).

This reflection covers AI-assisted development, Specification-Driven Development
(SDD), and Basic Multi-Agent Software Engineering in FacilityFlow AI. It draws on
the implemented Requester, Technician and Facilities Manager workflows, shared
identity and AI services, responsive interface, and CI/CD automation. Linked
records describe results at their own source revisions; they do not establish
that every branch, deployment or acceptance criterion is complete.

## AI Reflection

### AI was useful when its output was reviewable

We used development agents for implementation, test authoring, integration and
documentation. Their useful output was a change that could be inspected and
checked, rather than an assurance that it worked. Technician T5, for example,
produced password-change services, shared pages and tests. Real-stack verification
then found that a failed-login exception could retain an attempted credential in
a JDBC session. The correction used a fixed credential-free failure redirect and
cleared old exception attributes. Session-byte regressions checked the actual
persistence boundary, and an independent reviewer examined the correction.

We learned that generated security code needs tests against the framework and
storage behavior it actually uses. A controller test with a service double would
not have established that credentials stayed out of persisted sessions. AI can
contribute useful implementation and mistakes in the same iteration; human
accountability and independent review remain necessary. Evidence:
[T5 report](../logs/technician/T5-REPORT.md), including NFR-SEC-002 and the
distinction between automated verification and human merge approval.

### We kept AI optional and authority in ordinary services

This principle shaped all three application assistants:

- **Requester Smart Report:** users review suggestions and accept fields
  individually into the ordinary form. Nothing is saved until normal submission,
  which still checks ownership, OPEN state, version and fields (REQ-RQ-012–017).
- **Technician Work Plan:** diagnostic steps, safety precautions and stop/escalation
  conditions are read-only advice. Start, Work Log and completion actions require
  separate manual submissions; a Technician cannot close a case
  (REQ-TECH-004–008, REQ-TECH-012–017).
- **Manager Triage:** the model suggests category, priority and skill tags.
  `TriageCandidateRanker` filters active candidates by required skills and orders
  them by optional matches, workload, username and Account ID. The Manager chooses
  final values and a Technician, then submits an ordinary authorized operation
  (REQ-MGR-020–025).

We learned that a review label alone is insufficient. The service boundary must
make an AI response incapable of authorizing a write. Manager triage rechecks the
Request and catalogue/Technician snapshot when the human submits, because a
plausible recommendation may already be stale. Manual forms remain usable when
AI fails. Evidence: [shared Requester runtime](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/blob/5fc005964d9f6851b6c375c1e6cc64c80cf3ddbd/logs/2026-10-19-requester-shared-runtime.md),
[Work Plan implementation](../logs/technician/T4-REPORT.md), and
[Manager lifecycle and administration](../logs/2026-10-18-manager-lifecycle-administration.md).

### Live behavior exposed a gap that local checks missed

The 19 October Requester live smoke initially returned `AI_INVALID_OUTPUT` even
though connectivity worked. Structural diagnostics showed a response exhausting
the 800-token budget before final content. A control using the shared adapter's
existing `reasoning_effort=none` produced a valid structured answer. Aligning the
Requester transport with that contract, with an exact-payload regression,
allowed ordinary, missing-detail and untrusted-instruction samples to succeed.
An authenticated endpoint continuation checked login, CSRF and role rejection,
metadata-only invocation records, and no business-record creation.

We learned to diagnose the boundary before increasing limits or weakening output
validation. A successful injection sample establishes only that sample's result;
it does not establish general injection resistance or safe maintenance advice.
Those live checks exercised the then-separate Requester runtime. Subsequent
consolidation routed the UI through the shared runtime and removed the duplicate
gateway. Its automated verification passed; the later timeout-fix record notes a
user-reported successful live retest rather than an independently executed one.
Earlier live evidence cannot automatically certify new wiring. Evidence:
[live smoke and corrections](../logs/2026-10-19-requester-live-ai-smoke.md),
[consolidation](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/blob/5fc005964d9f6851b6c375c1e6cc64c80cf3ddbd/logs/2026-10-19-requester-shared-runtime.md), and
[timeout correction](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/blob/4a569e365def4fc0004aa4174b6fc4270ceb28a3/logs/2026-10-20-smart-report-read-timeout.md).

### AI security needs several independent controls

Prompts, Requester Updates and model responses are untrusted. The shared boundary
uses authorization before protected context loading, allowlisted context, typed
output validation, disclosure checks, server-side admission, deadlines and
metadata-only telemetry. Post-call freshness checks withhold advice when relevant
access or Request state changes. Browser code also discards late results after
edits, manual submissions or navigation. These address different failure modes:
valid JSON can still disclose sensitive text, and a valid plan can belong to an
obsolete assignment (AI-SEC-001–005, 008–017, 021, 028–030).

The lesson was to make failure safe and visible without losing manual drafts.
Controls also need honest limits: the human-reviewed multilingual and
obfuscation-aware Work Plan safety corpus required by AI-SEC-012 remains a
recorded gap. Existing policy checks do not certify physical maintenance
procedures. See the [AI security specification](../workflow/specs/AI-SECURITY-SPEC.md),
[gateway remediation history](../logs/2026-10-17-soclaas-gateway-remediation-summary.md)
and later [T4 evidence](../logs/technician/T4-REPORT.md).

## Specification-Driven Development (SDD)

### Stable requirements gave implementation and review a common reference

The approved specifications separated shared behavior, role workflows,
integration contracts and security controls. Stable IDs let implementation,
tests and [traceability](../workflow/TRACEABILITY.md) refer to the same behavior.
REQ-TECH-007/008 distinguish Technician completion from Manager closure, while
REQ-MGR-007/008 define review, return and reopening. This supported named
operations and retained completion history instead of a generic operation that
accepts arbitrary status changes. Manager writes lock and reread the Request,
reject stale versions and commit state plus audit together.

Specifications were most useful when they described forbidden outcomes and
failure paths. Preventing a former Technician from writing after reassignment,
preserving history after reopening, and requiring a replacement when removing a
category guided service and persistence checks beyond the interface. Evidence:
[approved baseline](../logs/2026-10-12-specification-approval.md),
[Technician requirements](../workflow/specs/ROLE-TECHNICIAN.md), and
[Manager implementation](../logs/2026-10-18-manager-lifecycle-administration.md).

### Integration showed that matching features is not enough

Requester and Technician work both touched shared password, navigation and
dashboard behavior. Integration retained one canonical password/About route and
service, preserved role route ownership, and moved Requester password regressions
into real PostgreSQL integration tests. This preserved intended behavior while
removing overlapping web implementations (REQ-SH-002/004/006/010/012/014).

The later Smart Report review found a similar issue: the UI worked through a
separate gateway even though a shared runtime existed. Consolidation removed
the duplication while retaining the form contract, combined-input limit,
edit-version guard and shared authorization/admission/telemetry checks. A later
review caught a lost five-second pre-admission transaction timeout; the fix
restored it and tested rollback with no shared AI invocation on timeout.

We learned that traceability must follow the actual caller through the service
boundary. Individually tested paths can still violate the intended architecture
or lose safeguards during consolidation. Shared interfaces and route ownership
should be agreed before parallel implementation. Evidence:
[role integration](../logs/2026-10-19-requester-technician-master-merge.md),
[runtime correction](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/blob/5fc005964d9f6851b6c375c1e6cc64c80cf3ddbd/logs/2026-10-19-requester-shared-runtime.md), and
[timeout regression](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/blob/4a569e365def4fc0004aa4174b6fc4270ceb28a3/logs/2026-10-20-smart-report-read-timeout.md).

### Acceptance criteria made incomplete results visible

The adaptive UI work mapped supplied MP2 UI concepts to MP3 web requirements and
recorded provenance in [REUSE](../workflow/REUSE.md). Browser execution found
that absent Manager error-map keys could break rendering. Later PR feedback
identified missing field-error associations promised by UIX-006–008 and
NFR-A11Y-001. The correction associated 35 controls with error IDs and scoped
detail-page errors to the failed form. Its run passed 409 Chrome UI checks.

However, the same task failed its independent 50-user load target: p95 was
1,018.64 ms against 1,000 ms, with zero errors. We retained that failure rather
than reporting an overall pass or changing the threshold. An earlier shared-runtime
revision had passed its local load run; that does not erase a later result on
another candidate. We learned to report acceptance by criterion, revision and
environment. Automated accessibility checks also do not establish full WCAG
compliance. Evidence: [UI iteration](../logs/2026-10-19-responsive-ui.md) and
[field-error correction](../logs/2026-10-20-manager-field-error-accessibility.md).

The same distinction applies to deployment. CI/CD automation and a recorded
GitHub container build exist, but Preview/Production deployment, promotion,
rollback and hosted smoke acceptance remain unverified in the
[CI/CD record](../logs/2026-10-18-ci-cd.md). Approved requirements, implemented
features and verified releases are separate milestones.

## Basic Multi-Agent Software Engineering

### Explicit work boundaries made parallel authorship practical

Development agents are separate from the three application assistants. We used
bounded task packets and handoffs rather than installing an autonomous
orchestration framework. Actual Technician T4 execution split backend context
and freshness work, UI rendering/lifecycle work, and test authoring among three
concurrent workers. The backend used gpt-6.1-sol/medium; UI and tester used
gpt-6-luna/high. A coordinator owned integration, shared evidence and test
execution; a separate gpt-6.1-sol/high reviewer checked the candidate.

Exclusive file ownership limited writer conflicts. Workers did not run builds,
perform Git operations or delegate further. Test design found that Java UUID
ordering could disagree with PostgreSQL for equal-time IDs across the high bit.
The backend scope was explicitly extended to fix the ordering, and a real
PostgreSQL regression checked it. This illustrates the value of a tester working
from the requirement and storage behavior rather than repeating implementation
assumptions. Evidence: [T4 dispatch](../logs/technician/T4-DISPATCH.md) and
[T4 report](../logs/technician/T4-REPORT.md).

### Handoffs and fresh review mattered more than agent count

T5 was paused after authorship and later resumed. Handoffs preserved changed
paths, authority, untested state and next steps; the pause was not reported as a
verified implementation. After resumption, tests exposed fixture problems and
the credential-retention issue described above. Independent review also requested
documentation corrections where guides still described the historical pause
after automated checks had passed.

We learned that a receiver needs exact requirements, file ownership, candidate
identity, executed results and unresolved gates. A role definition does not prove
that a role ran, and a read-only reviewer must not be credited with tests run by
the coordinator. Source hashes and candidate/result records helped tie review to
tested files. Agent approval does not replace teammate approval for a non-trivial
merge. Evidence: [T5 report and handoffs](../logs/technician/T5-REPORT.md) and
the [shared protocol](../workflow/agents/README.md) (AI-SEC-024–027).

### Agent instructions also need a trust boundary

The T4 tester's older SQLite-oriented recipe did not override the approved MP3
PostgreSQL contract. Similarly, the responsive UI iteration treated an external
MP2 document as reference material, and the accessibility correction treated a
PR screenshot as feedback rather than executable agent instructions. These are
examples of checking source authority; they do not prove resistance to every
malicious instruction-propagation attack.

Parallel work also introduced coordination and integration costs. Shared
password routes and duplicate Requester AI wiring required later consolidation.
We would retain focused parallel backend/UI/test work where interfaces are clear,
agree shared ownership earlier, and keep integration and independent verification
explicit. More agents alone would not have prevented those gaps. The defensible
result is the reviewed artifact and its evidence, with human decisions and
remaining limitations recorded.
