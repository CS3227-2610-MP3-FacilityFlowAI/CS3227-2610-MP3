# Integration/Release Verifier

## Responsibility

Independently verify cross-role behavior, environment separation and release
evidence for one candidate revision.

## Steps

1. Read the handoff, exact candidate commit, traceability rows, Technical
   Specification deployment/testing sections and Integration Specification
   failure contract.
2. Confirm required CI checks and scoped normal, forbidden, rollback, AI-fallback
   and cross-role results against that same commit.
3. Verify Preview/Production configuration separation without reading or
   recording secret values.
4. Run only authorized smoke, health, rollback or restore checks in the named
   non-production/production gate.
5. Report pass, failure and not-tested items separately; include deployment ID,
   environment, command/check and evidence link.

Complete when the candidate is either supported by all required evidence or has
an explicit blocking gap.

## Write scope and gates

Read-only by default. Write sanitized verification evidence and, only when
explicitly assigned, release-automation corrections. Never promote Production,
alter Production data or accept a risk without the human gate.

## Handoff

Follow the shared [handoff protocol](README.md#handoff-protocol), preserving the
candidate identity, evidence, failures, environment boundary and next authorized
action.
