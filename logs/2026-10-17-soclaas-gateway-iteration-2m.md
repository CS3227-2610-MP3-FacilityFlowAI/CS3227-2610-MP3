# SoC LLM gateway iteration 2M — deterministic Manager candidate ranking

## Scope

- Implemented `REQ-MGR-023` after the typed Manager Triage result exists.
- Filters inactive Technicians and requires every AI-suggested required skill tag.
- Orders candidates by optional skill matches descending, workload ascending,
  case-insensitive username, then immutable Account ID.
- Returns an explicit manual/accepted-tag adjustment explanation when there is
  no matching candidate.

## Safety boundary

The ranker is an immutable, mutation-free application seam. It does not assign a
Technician, change a Request, write persistence, or treat an AI recommendation as
an authorization decision. The Manager must still choose and submit the ordinary
authorized assignment operation.

## Verification

- Focused `TriageCandidateRankerTest` covers filtering, ordering, tie-breaks,
  no-candidate guidance, immutability, and invalid workload rejection.
- Provider regression tests remain the next verification step after this change.

Requirement IDs: REQ-MGR-023, AI-SEC-010/013/016. Commands/tools: focused
`gradlew.bat test --tests sg.edu.nus.facilityflow.manager.TriageCandidateRankerTest
--no-daemon` passed for this increment; the later Triage provider-contract
increment also passed the combined ranker/provider command. `git diff --check`
passed. Security evidence: the ranker is mutation-free and accepts only
validated typed tags; duplicate tags are rejected before ranking. Human review:
Manager role-owner, teammate, Security Reviewer and fresh-verifier review remain
pending. Corrections/risks: no assignment, Request mutation or authorization
decision was added; no provider credentials or raw request data were accessed.
