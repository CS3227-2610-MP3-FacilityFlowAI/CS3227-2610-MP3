# PR correction: Manager field-error associations — 20 October 2026

## Scope and source

The user supplied PR feedback that UIX-006–008 / NFR-A11Y-001 promises field-error
associations while Manager controls lack `aria-invalid` and `aria-describedby`.
Inspection confirmed the finding. The existing UI branch is
`feat/responsive-ui-mp3`; checkout was clean before this correction. The PR image
was treated as review feedback, not as agent instructions. Work was performed
by the coordinator without dispatching subagents.

Requirements: REQ-SH-010, NFR-A11Y-001, UIX-006–008. No backend validation,
authorization, state transition, database or AI logic changed.

## Changes

- Associate all 35 existing field-error controls in Manager request creation (6),
  request filters (7), audit filters (5) and request-detail actions/corrections (17)
  with stable page-unique error IDs.
- Set `aria-invalid` from the corresponding server error and render
  `aria-describedby` only when that error exists. Include the cancellation
  confirmation checkbox. Preserve existing labels, messages, fields and values.
- Scope detail-page errors and ARIA attributes by `failedAction` so shared names
  such as reason, Technician and priority do not mark another form invalid.
- Extend real Chrome checks beyond labels: explicitly exercise server validation
  errors, assert unique IDs and visible same-form descriptions, verify valid/reset
  fields have no error metadata, and preserve input through failure. Deliberately
  invalid native form submissions bypass browser constraints only in the
  disposable synthetic fixture; they must not create or transition requests.
- Clarify acceptance under the existing MP3 UI IDs and update reuse/traceability.
  This is new MP3 work; no additional MP2 source was copied.

## Verification

- `node --check src/test/js/requester-verification.cjs`: passed.
- `git diff --check`: passed.
- `gradlew.bat test --tests '*ManagerControllerTest' --tests '*ManagerInputTest'
  requesterVerification`: ManagerControllerTest 3 and ManagerInputTest 4 passed;
  **409 Chrome UI checks passed**, including the new error-association cases,
  on Chrome/155.0.8059.82. Results start timestamp:
  `2026-10-19T16:52:54.659Z` (20 October in Singapore).
- Overall `requesterVerification` remains **FAIL** because the existing ancillary
  50-user load target was missed: p95 **1,018.64 ms** against 1,000 ms, 400 requests,
  zero errors, pool size 2. No threshold/configuration/backend change or repeat
  attempt was made for this known independent performance limit.
- Evidence: `build/verification/requester/results.json`, `runner.txt`,
  `build/test-results/test/TEST-*.xml` and
  `build/test-results/requesterVerification/TEST-*.xml`. Generated failure artifacts
  describe the load assertion, not a failed accessibility check. Historical earlier
  UI evidence remains authoritative for its original source revision.
- The fixture uses disposable local PostgreSQL and synthetic accounts. No hosted
  database or live AI provider was used. Existing load thresholds remain intact.

## Limits and review

No manual screen-reader/full WCAG audit, deployment or remote PR update was
performed. Browser coverage exercises OPEN-request assignment/cancellation/
correction error branches plus creation and filters. Reassign/return/reopen
associations follow the same reviewed markup pattern but were not exercised
through their lifecycle states in this browser run. Teammate review remains
required before merge; earlier UI/load attempt evidence is preserved in the
19 October UI log and is not replaced by this correction.
