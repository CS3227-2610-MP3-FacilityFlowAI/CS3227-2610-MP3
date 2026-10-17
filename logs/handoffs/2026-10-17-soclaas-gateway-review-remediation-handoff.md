# SoCLaaS Gateway Review and Remediation Handoff

- Source agent: Codex review session.
- Destination agent: FacilityFlow shared/AI Developer, followed by a fresh Tester and Security Reviewer.
- Task/goal: Bring the `soc-llm-gateway` branch into conformance with the approved gateway, role, and AI-security contracts. Make each remediation as a focused increment in a separate commit; include its focused tests and traceability/evidence update in that commit.
- Approved requirement IDs: REQ-AI-001–010; AI-SEC-001–030; REQ-RQ-012–017; REQ-TECH-012–017; REQ-MGR-020–025; NFR-SEC-002; NFR-REL-001; NFR-DOC-001. Approval status still needs verification as described below.
- Approved specification revision/evidence: `workflow/specs/README.md`; `workflow/specs/INTEGRATION-SPEC.md`; `workflow/specs/AI-SECURITY-SPEC.md`; the AI sections of `workflow/specs/ROLE-REQUESTER.md`, `ROLE-TECHNICIAN.md`, and `ROLE-MANAGER.md`; `workflow/TRACEABILITY.md`. Review fixed point: `master` at `e784d483f546fa60b01fd76107b8aa27b4359db0`; reviewed head: `701842ccc07c833d1a76eeb0fd9f2eb05edcdb37`.
- Authorized scope/permissions: Inspect and change only the gateway, its three role orchestration seams, focused tests, migrations if an approved contract requires them, and companion documentation/evidence. Do not merge, deploy, access provider credentials, weaken tests, invent approval evidence, or broaden role/business authority.
- Relevant input artifacts/provenance: `AGENTS.md`; `workflow/SOCLAAS-GATEWAY-PLAN.md` (planning context only); `docs/DeveloperGuide.md`; `workflow/REUSE.md`; `workflow/agents/README.md`; commits in `master..soc-llm-gateway`; and the specifications listed above. `docs/agents/issue-tracker.md` is absent, so issue-link lookup was not available.
- Assumptions: `master` is the intended fixed point because `soc-llm-gateway` is a clean feature branch whose merge-base is `e784d48`. The current source tree was reviewed without modifying implementation files.
- Unresolved questions: (1) where role-owner, teammate, and Security Reviewer approval for the v0.2 gateway revision is recorded; (2) whether compressed responses should be removed now or first specified and approved; (3) the approved multilingual/obfuscation/negation safety corpus and its version; (4) the intended Requester and Manager HTTP/form seams needed to implement role-safe orchestration.
- Files changed: This handoff only. A compact temporary handoff is also stored in the operating-system temporary directory.
- Commands/tools: `git diff master...HEAD`; `git log master..HEAD --oneline`; targeted source/spec searches; two-axis standards/spec review; `./gradlew.bat test` using the existing Gradle cache.
- Verification performed/results/evidence: Full suite passed on 17 October 2026: 475 tests, 0 failures, 0 errors, 0 skipped; Gradle `BUILD SUCCESSFUL` in 3m48s. Passing current tests does not cover the gaps below.
- Security considerations/unresolved risks: Direct role adapters bypass authorization/admission/freshness; one failed startup readiness check can make the Work Plan path unavailable indefinitely; the absolute deadline starts after pre-provider work; request-specific disclosure checks and the required Work Plan safety policy are incomplete.
- Suggestions accepted/rejected: Accepted: remediate in dependency-ordered commits and retain fail-closed behavior. Rejected: treating the green suite as proof of compliance; inventing approvals; silently accepting compressed-response scope creep.
- Human approval required and reason: Required before merge for the pending specification reviews, non-trivial remediation PR, any compressed-response contract change, the safety corpus, and production promotion (AI-SEC-024/027 and repository rules).
- Human reviewer/outcome/evidence: pending.
- Next authorized action: Confirm approval evidence, then implement the commit sequence below. A fresh verifier must rerun focused and full tests after fixes.
- Status: ready for review; implementation is not specification-complete.

## Review outcome

The branch is mechanically healthy but does **not** yet meet the complete gateway contract. The full suite is green, configuration/provider-core/HTTPS/admission/telemetry building blocks are substantial, and the Technician slice has a real authorization-first service. However, critical integration and validation requirements remain missing or incorrect.

### Standards findings

1. **Hard — approval evidence is incomplete.** `workflow/specs/README.md` requires role-owner and teammate approval before implementation. `workflow/TRACEABILITY.md` still says review is pending for Smart Report, Triage, telemetry, and retry work. Do not claim or manufacture approval; obtain and cite it before merge.
2. **Hard — companion artifacts lag Iterations 2G–2M.** Repository rules require the same change to update traceability, affected guides, reuse records, and durable handoff evidence. The later commits/logs omit some or all of these.
3. **Hard — `docs/DeveloperGuide.md` contradicts the branch.** It still describes configuration-only behavior and several gateway capabilities as unimplemented.
4. **Hard — stable requirement IDs are missing from principal implementation/evidence.** Add concise references to the governing requirements in implementation comments or companion evidence where they materially aid traceability; do not add noisy IDs to every line.
5. **Judgement — duplicated output validation/failure mapping.** `SocLlmSmartReportAssistant`, `SocLlmTriageAssistant`, and `SocLlmWorkPlanAssistant` repeat failure mapping and JSON text/list validation. Extract a small package-private shared policy only where the rules are truly common; retain feature-specific schemas.
6. **Judgement — primitive domain strings.** Triage and Work Plan contexts carry urgency, priority, and status as free-form strings. Prefer existing domain enums/value types at the role boundary so invalid vocabulary cannot cross the gateway seam.

### Specification findings

1. **P0 — Requester and Manager lack role-safe orchestration.** REQ-AI-005 and the role specs require authorization/object eligibility, lazy allowlisted context loading, shared admission/lease handling, durable STARTED/terminal telemetry, and post-call freshness checks. Spring currently publishes direct `SmartReportAssistant` and `TriageAssistant` provider delegates in `AiProviderLifecycleConfiguration`; there is no equivalent Requester/Manager service, repository, or controller seam.
2. **P1 — the single deadline begins too late.** AI-SEC-008 requires one absolute deadline from use-case entry through eligibility, admission, context, readiness, provider/retry, validation, freshness, and terminal telemetry. `TechnicianWorkPlanService` completes early work before `SocLlmWorkPlanAssistant` creates a fresh timeout.
3. **P1 — readiness recovery is bypassed.** AI-SEC-020 requires first use after the 60-second NEGATIVE cache to start/join a new generation. `SocLlmProviderRuntimeFactory.Runtime` always returns the initial startup readiness stage; `TechnicianWorkPlanService` rejects NEGATIVE before `core.complete` can trigger refresh.
4. **P1 — typed schemas accept forbidden values.** Smart Report does not enforce the urgency enum or the requirement for at least one draft/suggestion/question. Triage does not enforce its priority enum, unique tag lists, or disjoint required/optional tags. Duplicate optional tags can inflate deterministic candidate ranking.
5. **P1 — disclosure checks are not request-specific.** AI-SEC-030 requires fail-closed comparison against server secrets, prompt fragments/canaries, unauthorized-context canaries/content, and forbidden identifiers. Current adapters only apply generic regexes and cannot receive server-assembled deny material.
6. **P1 — Work Plan safety policy is incomplete.** AI-SEC-012 requires a versioned, human-reviewed multilingual, obfuscation-aware, and negation-aware deterministic policy. The current implementation is an unversioned English regex set.
7. **Scope creep — compressed response handling is not approved.** The authoritative integration/security specs explicitly defer compressed-wire support, while the branch advertises gzip and imposes a new 1 MiB compressed-wire cap. Remove it, or update and approve the specification before retaining it.

## Required commit sequence

Do not squash these into one change. Each commit must be independently reviewable and keep the build green. Include focused tests and the relevant `workflow/TRACEABILITY.md`/session evidence update with each behavior change.

1. **`fix(ai): recover readiness after negative cache`**
   - Stop returning the permanently completed startup readiness stage for later use.
   - Make an invocation after NEGATIVE-cache expiry enter the provider core's generation-aware refresh path while preserving startup-safe asynchronous readiness.
   - Add deterministic no-sleep tests for startup failure, pre-expiry denial, post-expiry refresh, concurrent joiners, and closure.

2. **`fix(ai): enforce one end-to-end invocation deadline`**
   - Create the absolute deadline at role use-case entry and propagate it through admission, telemetry, readiness, context load, provider/retry, validation, freshness, and terminal persistence.
   - Do not reset the budget in a feature adapter. Ensure timeout/cancellation releases the permit/lease and records the correct terminal status without returning content.
   - Add injected-clock/future tests for slow readiness, context, retry, freshness, and telemetry paths.

3. **`fix(requester-ai): enforce the Smart Report result contract`**
   - Accept only the specified urgency vocabulary.
   - Require at least one non-empty draft field, suggestion, or missing-information question.
   - Preserve exact-field, size, normalization, markup/control, and catalogue checks.
   - Add boundary/adversarial tests before changing the parser.

4. **`fix(manager-ai): enforce the Triage result contract`**
   - Accept only Low/Medium/High/Critical priority.
   - Require each tag list to be unique and required/optional tags to be disjoint.
   - Add tests proving duplicates cannot alter candidate ranking; defensively deduplicate or reject at the typed-result boundary according to the approved contract.

5. **`security(ai): enforce request-specific output disclosure policy`**
   - Introduce a narrow server-assembled deny-material value that is not serialized into the prompt as an instruction.
   - Apply exact and NFC/case-normalized checks for secrets, fixed delimiters/canaries, protected template fragments, unauthorized canaries/content, and forbidden identifiers across all three features.
   - Add unique-canary tests for each source and prove rejected content is absent from results, exceptions, logs, and telemetry.

6. **`feat(requester-ai): add authorized Smart Report orchestration`**
   - Add the Requester service/repository/controller seam for create and owned-OPEN edit modes.
   - Order checks as authentication/role/object/input -> admission/lease -> STARTED telemetry -> lazy context -> provider -> role/object/version/catalogue freshness -> terminal telemetry -> no-store response.
   - Prove wrong-role/cross-object/oversize/CSRF/limit failures load no protected context and make no provider call.

7. **`feat(manager-ai): add authorized Triage orchestration`**
   - Add Manager service/repository/controller orchestration and connect valid results to the deterministic ranker without mutating a Request or assigning a Technician.
   - Recheck Account/role, Request state/version, and category/skill catalogue freshness before display; discard stale output.
   - Cover no-candidate, inactive/insufficient-skill Technician, ordering, no-store, CSRF, admission, telemetry, and stale-result cases.

8. **`security(technician-ai): implement the approved Work Plan safety corpus`**
   - Blocked until the human-reviewed corpus/version is cited.
   - Replace regex-only evidence with the approved deterministic policy and adversarial cases for multilingual, obfuscated, and negated unsafe advice. Preserve fail-closed behavior.

9. **Choose exactly one compressed-response increment.**
   - Preferred while the current spec governs: **`revert(ai): defer compressed provider responses`** and restore identity-only headers/handling/tests.
   - Alternative only after an approved spec revision: **`feat(ai): implement approved compressed response bounds`** with documented wire/decompressed caps, encoding-chain policy, cleanup, telemetry, and abuse tests.

10. **`docs(ai): reconcile gateway implementation evidence`**
    - Update `docs/DeveloperGuide.md`, `workflow/TRACEABILITY.md`, `workflow/REUSE.md`, iteration logs, and durable handoffs to match the final code and actual reviews.
    - Cite exact commits/tests. Preserve pending labels where evidence or human approval is still absent.

## Verification gate for the receiving agent

For every increment, run the narrow test class(es) first, then `./gradlew.bat test`. The final candidate must again report zero failures and must add explicit coverage for each gap above. A fresh verifier and Security Reviewer must inspect the exact final commit range; the current 475-test pass is baseline evidence only, not acceptance of the remediation.
