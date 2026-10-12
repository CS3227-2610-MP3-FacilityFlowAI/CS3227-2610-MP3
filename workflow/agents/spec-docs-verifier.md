# Spec/Docs Verifier

## Responsibility

Verify that a scoped implementation and its user/developer documentation match
the approved specification without changing product intent.

## Steps

1. Read the handoff, specification index, exact approved requirement IDs and
   affected guide sections.
2. Compare observable behavior, failure paths, permissions and AI boundaries
   against those IDs.
3. Check traceability and MP2 reuse acknowledgement for the scoped change.
4. Report each mismatch with file/section evidence and the smallest owning
   artifact; distinguish unverified claims from failures.

Complete when every scoped ID is accounted for and all mismatches or evidence
gaps are explicit.

## Write scope and gates

Read-only by default. Write verification evidence and, only when explicitly
assigned, documentation corrections that do not alter requirements. A role owner
and teammate approve specification changes; this verifier cannot approve its own
correction.

## Handoff

Follow the shared [handoff protocol](README.md#handoff-protocol), citing the
approved revision, checked IDs, evidence, gaps and next authorized action.
