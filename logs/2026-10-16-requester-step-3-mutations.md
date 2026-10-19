# Requester step 3: eligible edits, cancellation and updates

- Date: 16 October 2026 (Asia/Singapore).
- Branch: `feat/requester-workflow`.
- Authorization: commit the verified step 2 changes, then implement step 3 and
  report the checkpoint. Step 2 committed as `9ff8a5b`; step 1 is `74949cd`.
- Requirements: approved v0.1 REQ-RQ-006–011, REQ-SH-002/006/010/011/013/014;
  PRODUCT-SPEC field rules and TECHNICAL-SPEC transaction/version rules.

## Implemented

- Owned OPEN report editing through the existing five-field RequestDraft,
  including Manager-recorded reports. The selected category is rechecked and
  locked active; protected fields are never accepted as client authority.
- Owned OPEN cancellation with a 5–500-code-point reason, explicit confirmation
  and explicit cancel decision. Keep request is the first/default submit action;
  a missing or non-cancel decision leaves the report unchanged. Cancellation
  preserves the record and exposes only its public reason in visible history.
- Append-only Requester Updates of 1–1,000 code points while OPEN, ASSIGNED,
  IN_PROGRESS or COMPLETED awaiting review. CLOSED/CANCELLED reject additions.
  Author and UTC time come from authenticated server identity/database defaults.
- Each operation runs within the existing transaction scope, rechecks persisted
  active account/role/session version, locks an owned Request and compares its
  state/version. Requester writes increment Request.version; the SQL write also
  asserts the expected version/state. Concurrent/repeated forms cannot overwrite
  newer work or append the same update twice.
- Edit/cancel/update state, version and audit writes commit together; audit
  failure rolls back every change, including inserted updates. Audit details
  exclude report/update free text. Only the explicitly public cancellation
  reason is added to its audit details.
- Shared create/edit form, cancellation/update page and detail actions. Central
  error handling retains submitted input and the original stale version with
  explicit reload guidance. It never swaps a fresh version into stale input.
- Pending-submit handling preserves named decision buttons in the native POST
  payload while blocking another pending submission. Checkbox styling and
  associated error/confirmation messages are included.
- Guides, traceability, plan and reuse records updated. No migrations,
  dependencies, AI endpoints or other role business operations added.

## Sources and scope

Validated the previous handoff against current specs, code, migrations/grants,
form bindings and tests. Applied Ponytail, Context7, PostgreSQL best practices
and the FacilityFlow handoff protocol. Context7 provided official Spring
immutable constructor-binding documentation. Reused the existing TransactionTemplate,
parameterized JDBC and Thymeleaf patterns. No additional MP2 source consulted
or copied; REUSE.md records concepts already present in the approved specs.

## Verification

- Checked/staged only step 2 artifacts and committed `9ff8a5b` at the user's
  request. No push/merge/deployment performed.
- Focused command: `gradlew.bat test --tests '*RequestDraftTest' --tests
  '*RequesterRequestIntegrationTest' --tests '*RequesterRequestControllerTest'
  bootJar`. BUILD SUCCESSFUL in 59s: 24 tests, zero failures/errors/skips.
- Genuine disposable PostgreSQL tests with the restricted app role cover owned
  OPEN edit, overposting, replay, explicit/default cancellation, validation,
  all six states for update eligibility, terminal write rejection, ownership,
  wrong-role/CSRF denials, post-load assignment/category changes, concurrent
  edits, cancel/update race and audit rollback/retry for all three mutations.
- Unicode reason/update boundaries and whitespace preservation are checked by
  RequestDraftTest. A direct-service regression additionally checks disabled
  accounts, invalidated sessions and wrong-role calls for every new mutation.
- `node src/test/js/request-form.test.cjs` passed pending/back-navigation and
  named-submitter preservation assertions.
- Full `gradlew.bat test bootJar`: BUILD SUCCESSFUL in 4m17s; 78 tests,
  zero failures/errors/skips, confirmed from build/test-results/test/TEST-*.xml.
  JaCoCo report and application JAR generated. No failed application tests or
  security findings occurred in this step; no fresh-fix verification was needed.
- `git diff --check` passed; no V1–V5 migration content changed from step 2.

## Remaining and next

Next is dashboard status counts/recent requests and own-list search/filtering,
then Smart Report Assistant integration. Manager/Technician services remain
assigned to their owners. Browser/keyboard/viewport, hosted persistence, full
cross-role lifecycle, performance/CI and human teammate review remain pending.
V5 hosted application remains unverified; this step adds no further migration.

Step 3 is uncommitted at this checkpoint. No hosted database access, external
messages, push, merge or deployment occurred. A teammate must review before
non-trivial merge; do not treat local automated results as release approval.
