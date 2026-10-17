# SoCLaaS gateway remediation summary

## Ordered remediation commits

1. `695eb07 fix(ai): recover readiness after negative cache`
2. `899501f fix(ai): enforce one end-to-end invocation deadline`
3. `d93b7da fix(requester-ai): enforce the Smart Report result contract`
4. `16d1279 fix(manager-ai): enforce the Triage result contract`
5. `0f60e5f security(ai): enforce request-specific output disclosure policy`
6. `0a0ca7b revert(ai): defer compressed provider responses`
7. `8305b0d fix(ai): preserve absolute deadlines through feature delegates`
8. `dd66032 docs(ai): add gateway requirement anchors`
9. `f610ba6 docs(ai): record final gateway test count`
10. `96f6cd9 docs(ai): finalize gateway review range`
11. `c6b1459 docs(ai): complete gateway handoff evidence`

The review baseline remains `e784d483f546fa60b01fd76107b8aa27b4359db0`;
the fresh-review range is `e784d48..HEAD` (ending at the final evidence commit).

## Requirements and evidence

- Readiness: AI-SEC-020, REQ-AI-004/005, NFR-REL-001. Focused lifecycle
  recovery/concurrent-joiner tests and full-suite pass.
- Deadline: AI-SEC-008/009, REQ-AI-005. The Technician service creates one
  absolute deadline at entry and passes it through readiness/context/provider;
  injected-clock regression proves no reset after context loading.
- Smart Report: REQ-RQ-014 and AI-SEC-010/011/016. Urgency vocabulary and the
  non-empty suggestion invariant are enforced at parser and typed-result seams.
- Triage: REQ-MGR-022/023 and AI-SEC-010/011/016. Priority vocabulary,
  unique/disjoint skill tags and mutation-free deterministic ranking are tested.
- Disclosure: AI-SEC-016/030 and REQ-AI-008. Server-only secret matching,
  NFC/case-normalized delimiters/canaries/template fragments and Technician
  identifiers fail closed without returning sensitive content.
- Compression: AI-SEC-010/019. Gzip negotiation/decompression was reverted;
  identity-only handling is restored because compressed wire scope is deferred
  by the current approved specification.

## Verification

Each remediation increment ran its focused Gradle test(s), `git diff --check`,
and `./gradlew.bat test --no-daemon`; every executed full suite completed with
`BUILD SUCCESSFUL`, 0 failures, 0 errors and 0 skipped. The branch baseline
count was 475 tests; the current suite has 476 tests and remains green.

## Blocked or unverified work

- Requester authorized Smart Report orchestration (REQ-RQ-012–017) and Manager
  authorized Triage orchestration (REQ-MGR-020–025) remain blocked until the
  required role-owner and teammate approvals are recorded and the intended HTTP
  seams are confirmed. No provider-only seam is represented as a role endpoint.
- The Work Plan multilingual, obfuscation-aware and negation-aware human-reviewed
  safety corpus/version required by AI-SEC-012 is not present; no substitute
  corpus was invented.
- Hosted SoCLaaS smoke testing, deployment separation, final Security Reviewer,
  teammate and fresh-verifier approvals remain pending. No provider credential
  was accessed.

## Evidence handling

The review handoff at `logs/handoffs/2026-10-17-soclaas-gateway-review-remediation-handoff.md`
is included as unmodified review context. Historical iteration logs are retained;
the 2I compressed-response log records an experiment that is no longer active.
