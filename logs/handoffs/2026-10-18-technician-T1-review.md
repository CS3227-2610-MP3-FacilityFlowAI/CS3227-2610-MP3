# Technician T1 independent review

- Source agent: independent Security Reviewer / Spec Verifier, gpt-6.1-sol, high.
- Destination agent: T1 coordinator and assigned backend/test owners.
- Task/goal: independently review assigned-only T1 reads and frozen UI; recheck corrected source and executed candidate evidence.
- Approved requirement IDs: REQ-TECH-001–003/009, REQ-SH-002/008/009/010/013, NFR-SEC-001; T1 contracts' exact session-version, read-session lifetime/actor/snapshot boundaries and safe failures.
- Approved specification revision/evidence: approved v0.1 role/shared/technical specifications; `logs/technician/T1-DISPATCH.md` records this iteration's user approval. No later iteration authorized.
- Authorized scope/permissions: read-only production review; write this handoff and unique OS-temporary handoff only. No implementation/test edits, Git mutation, delegation, builds or test execution.
- Relevant input artifacts/provenance: `AGENTS.md`, `workflow/agents/README.md`, `workflow/agents/security-reviewer.md`, `workflow/agents/technician-workflow.md`, `workflow/technician/CONTRACTS.md`, `workflow/technician/ACCEPTANCE-MAP.md`, `workflow/specs/ROLE-TECHNICIAN.md`, relevant PRODUCT/TECHNICAL/AI-SECURITY/INTEGRATION sections, `logs/technician/T1-BACKEND-CANDIDATE.json`.
- Assumptions: coordinator has frozen stated production inputs; schema constraints in V1/V2 continue to apply. Repository callback misuse findings concern its promised capability boundary; current service does not perform either sequence.
- Unresolved questions: no open actionable T1 findings. Final passing candidate/evidence independently checked below; browser/performance/deployment and specific edge-path evidence limits remain explicit. Earlier pending statuses below describe review history and are superseded by final verification.
- Files changed: this record and OS-temporary handoff only.
- Commands/tools: scoped PowerShell `Get-Content`, `rg`, read-only `git diff`; canonical UTF-8/LF SHA-256 comparison of all five backend candidate files (all matched); `apply_patch` for evidence. No database, network, build or tests invoked.

## Findings

### T1-REV-001 [P2] Preserve actor binding after failed reauthorization

- Requirement: REQ-SH-002, REQ-TECH-001/009 and T1 actor-bound read-session contract.
- Location: `src/main/java/sg/edu/nus/facilityflow/technician/JdbcTechnicianQueryRepository.java`, original candidate lines 106–110.
- Reproduction: in one `readSnapshot` callback authorize valid Technician A; call `requireCurrentTechnician(B)` for valid Technician B and catch the denial; call it again for B. The first denied attempt clears `authorizedActor`; the second sees no previous actor and succeeds. Calling counts/queue/detail for B then succeeds. A failed same-actor stale-version/null reauthorization followed by B has the same effect.
- Expected result: preserve the original actor binding independently of whether current authorization is valid, or invalidate the entire session after failure. A caught failed reauthorization must not enable another actor.
- Evidence/status: **closed, verified**. Assigned owner corrected repository; independent fresh source recheck confirms persistent `boundActor`, authorization revocation on failure, B rejection afterward and original A reauthorization. Final candidate's PostgreSQL tests `deniedActorSwitchCannotAuthorizeASecondTechnicianOnRetry` and `staleReauthenticationFailureCannotUnlockAnotherActor` both executed and passed with no failures/errors/skips; independently inspected XML and matching source hashes.

### T1-REV-002 [P2] Reject suspended snapshot capabilities

- Requirement: REQ-SH-002, REQ-TECH-001/009 and T1 same-snapshot/connection contract.
- Location: `src/main/java/sg/edu/nus/facilityflow/technician/JdbcTechnicianQueryRepository.java`, original candidate lines 61/85–88 (REQUIRES_NEW and lifetime guard).
- Reproduction: outer `readSnapshot` authorizes A; a second connection deactivates A or changes assignment; nested `readSnapshot(inner -> outer.queue(A, filter))` runs on the same callback thread. The outer capability remains open, but Spring suspends its transaction for REQUIRES_NEW. `NamedParameterJdbcTemplate` obtains the inner transaction's bound connection, so the outer authorization protects a query against a different/newer snapshot. The same applies to counts, categories and detail/history.
- Expected result: deny use of an outer capability while its transaction is suspended, or pin its reads to the original transaction/connection. On restoring the outer transaction, its original snapshot may resume.
- Evidence/status: **closed, verified**. Assigned owner corrected repository; independent fresh source recheck confirms actual manager DataSource resource capture and identity comparison before authorization/every read. Final candidate's PostgreSQL test `nestedSnapshotUsesItsOwnActorAndOuterSessionResumesOnlyAfterward` executed and passed with no failures/errors/skips, proving suspended outer denial, inner access and outer resumption. Proxy DataSource unwrapping remains source-reviewed rather than a separate executed edge test.

## Corrected production review identity

Canonical UTF-8/LF SHA-256 after coordinator refroze both corrections (initial `T1-BACKEND-CANDIDATE.json` intentionally describes original reviewed files):

| File | SHA-256 |
| --- | --- |
| `technician/JdbcTechnicianQueryRepository.java` | `95d7a94c9fe3c25c1abfbb871dd02254812cc68fad62b5f9b5a362f15d71234d` |
| `web/TechnicianQueueController.java` | `1c242a9fca500456046cf4574e3a8359a58e54411d75f6fa027d8db26b1a2aab` |
| `web/DashboardController.java` | `ec1f3e2ffc0a92de0b685662f516854bfef814430b2123d2468dda46aecdacdf` |
| `templates/technician/queue.html` | `74ea1888159ed140514ca39da90a0d5887e928bdf9af260b442dbfa506e8f172` |
| `templates/technician/request-detail.html` | `f0a5c23cb44a04216f3cc5abbc1e0cf01ac52082eebb75aafd00cb8b09c12e24` |
| `templates/technician/fragments/request-rows.html` | `5bf92bf0656c9cb9d2a8885b054e11b89f73d6010f267e3253017334c58d234d` |
| `templates/technician/fragments/history.html` | `d0a70ec6d0495db7d971edfb385485917f4a14a5c7f1febe8a03a0a4a4f8a298` |
| `static/css/technician.css` | `8a950c27369b40ac1a130699574b5f64dd7abb60c121997e35d49e4f4235bef7` |

Java paths are under `src/main/java/sg/edu/nus/facilityflow/`; resource paths are under `src/main/resources/`. Other four backend hashes match original candidate. No further production findings after corrected source review.

## Other reviewed behavior and limits

- Frozen backend: persisted active TECHNICIAN plus exact nonnegative session version checked before Request reads; SQL predicates scope counts/categories/queue/detail and both histories; retained states supported; history order creation time/immutable ID; literal `!`/`%`/`_` escaping and parameter binding; explicit enum ranking/null assignment time; immutable list copies; no persistence entities/internal Request IDs, SQL causes, mutations or secret material exposed. No additional actionable backend findings identified by static review.
- Frozen UI/shared production reviewed after coordinator notification: new controller, four templates, stylesheet and existing DashboardController wiring. No additional actionable findings identified. Failed filter parsing preserves raw values with escaped output and avoids service execution. Whole-read failure lacks results; detail error message/status are generic. Dates use Asia/Singapore and explicit zone. No manual-write or Work Plan controls.
- Independent static recheck of prefreeze UI findings: U01 category iteration is guarded by a parent `queue != null` block and remaining results use null guards; U02 inline CSS has been replaced by local `/css/technician.css`, allowed by unchanged CSP and existing CSS route permission. Runtime rendering remains unverified by this reviewer.
- Not tested by reviewer: builds/JUnit/PostgreSQL execution, browser/keyboard rendering, performance, live database, deployment, later T2–T5 workflows or existing AI provider behavior. Tests were still being authored during initial production review and are not pass evidence.
- Security considerations/unresolved risks: both capability findings closed on independently inspected source plus passing executed PostgreSQL regressions; no remaining actionable T1 finding. Evidence limits below are not replaced by this conclusion.
- Suggestions accepted/rejected: both T1-REV-001 and T1-REV-002 corrected by assigned backend owner and independently rechecked statically. No specifications changed or exceptions granted.
- Human approval required and reason: teammate review before non-trivial merge remains required by AGENTS.md; this review grants no merge, push or deployment approval.
- Human reviewer/outcome/evidence: pending.
- Next authorized action: coordinator completes T1 documentation/exact-path local commit/report; teammate review remains required before any non-trivial merge. T2 and later require separate authorization.
- Status: verified; T1 review complete, no open actionable findings.

## Initial integrated candidate and test adequacy review

- Independently checked `logs/technician/T1-CANDIDATE.json` initial candidate (baseline `172eaff`, manifest identifier `d363c47de8f7a8d88809cbe08bce127a1371f248d57f2f97a036b8037b7417fd`): all 17 production/test canonical UTF-8/LF file hashes matched at inspection. No production changes since accepted corrections.
- Read frozen PostgreSQL finding regressions. `deniedActorSwitchCannotAuthorizeASecondTechnicianOnRetry` reproduces A-to-B caught denial/retry and denied reads after failure; `staleReauthenticationFailureCannotUnlockAnotherActor` covers same-actor stale failure followed by B; both verify original A can reauthorize. These adequately target REV001.
- `nestedSnapshotUsesItsOwnActorAndOuterSessionResumesOnlyAfterward` rejects outer counts/queue while nested transaction is bound, verifies independently authorized inner access and restored outer access. This adequately targets REV002's reported route. Source inspection additionally checks guard coverage before reauthorization and every read; proxy DataSource unwrapping is currently source-only evidence.
- Existing PostgreSQL snapshot test changes assignment/account on independent connection after snapshot authorization and asserts original scoped counts/categories/detail/queue persist within snapshot, then new reads reject inactive account and reflect reassignment. Histories exercise original authors, same-time immutable-ID ordering, current assignment change and former-assignee denial. Inactive referenced category is present in fixtures/order expectations.
- `SecurityIntegrationTest` populated queue/detail tests exercise real Thymeleaf rendering, escaped script/tag canaries, prior inactive Technician authorship, local time and local stylesheet; malformed filter 400 renders preserved escaped search plus field errors without counts; malformed/unknown detail 404 renders safe message. The 404 method's name includes unassigned but no existing unassigned row is inserted there; assigned-object isolation is covered separately by PostgreSQL/service tests. Queue/detail 503 currently have controller-model tests rather than real-template rendering. Cross-thread, callback-failure closure and proxy-DataSource edge paths lack executed regressions at this stage; these are evidence limits, not new production defects.
- Static test review found helper type/signature compilation blockers (`request` argument order, `readWith` Long restriction, `queueAs(...).size()` on QueueView), independently matching coordinator-reported first scoped `compileTestJava` failure in `build/T1-scoped.log`; production compiled per coordinator report. Also reported all-digit UUID uppercase fixture (cannot become noncanonical) and assignedRequest INSERT's 12 placeholders for 13 supplied parameters. Coordinator routed all repairs to Tester; no production/test changes by reviewer. Initial runtime failure is not a pass. Test files are moving until coordinator refreezes.
- Next review: compare corrected frozen test files/manifest, verify assertions preserve intended requirements, inspect coordinator runtime evidence, then update findings status. No builds/tests run by reviewer.

## Fresh review of test-only repairs

- Coordinator refroze corrected tests. Independently checked title-first `request` helper, generic `<T> T readWith`, `QueueView.rows().size()`, exactly 13 fixture placeholders for 13 parameters, and UUID fixture containing hex `e` so uppercase changes syntax. Corrections preserve actor-failure/nested/snapshot/history/filter assertions and their expected outcomes. Clear for rerun; this is source review, not compilation evidence.
- Only three test files changed relative to the first integrated manifest; all 14 other production/test inputs remained identical. No production finding reopened.
- Refreshed `T1-CANDIDATE.json` identifier `abf7a2cfc67d41849754d328e7a1532ec0e9ab623080687cb5145672d5201b7f`: independently recomputed all 17 canonical UTF-8/LF hashes, zero mismatches.
- Corrected test SHA-256: repository test `ff86ea32fa49b42c96d31826d5e0d7aa1415abd2a217d622b6894eb7c899b832`; fixture `67b4c927a5b07dc80785322f88f7996361ff53df6857396b07f3c53c8e4dd235`; controller test `4361d179251cda0443d3406b68f3fe331d1cd4e5f61a6966ab4b5b983221bb66`.
- Remaining next action: coordinator reruns scoped Gradle tests and integrated `check`; reviewer inspects recorded runtime evidence before closing security findings. No tests/builds run by reviewer.

## Fixture close exception correction

- Inspected `build/T1-scoped-rerun.log`: the second scoped attempt progressed past initial 28 test errors but failed compilation at `EmbeddedPostgres.close()` because its IOException was not declared; this was the sole reported compiler error. Runtime tests had not started.
- Fresh independent source recheck: fixture now imports `java.io.IOException`, declares `close() throws IOException` and retains the direct `postgres.close()` call. JUnit `@AfterEach` caller already declares `Exception`. No assertions or behavior changed; clear for candidate hash refresh and rerun. Production remains unchanged. This is not executed passing evidence.

## Fresh review of fixture seed privileges and scoped runtime evidence

- Independently parsed coordinator-executed JUnit XML under `build/test-results/test/` and compared `logs/technician/T1-SCOPED-RESULT.json`: 43 tests executed, 34 passed, 9 failed. Service 5/5, controller 9/9 and authenticated SecurityIntegration 18/18 passed; PostgreSQL repository 11 executed, 9 failed in synthetic Request setup with `permission denied for table maintenance_request`, 2 passed. No skipped/error counts. The actor-failure/nested regressions are not yet passing evidence because their seed failed.
- Fresh independent source check of just-refrozen correction: only fixture `assignedRequest` insertion changes from `app.queryForObject` to `admin.queryForObject`, allowing synthetic assigned/completed state setup. Category lookup and Request ID lookup remain app-role reads. Repository under test explicitly constructs `TechnicianQueryFixtures.dataSource(db.postgres, "facilityflow_app")`; system-under-test reads retain restricted application privileges. Read-only `git diff` for migrations and bootstrap grants is empty. All four assertion-bearing test classes retain previous canonical hashes; no assertion weakened.
- Independently recomputed all 17 file hashes in current frozen manifest `453e4fea1443005c7302225908140394a19fb53b0d784a94419560afd9e6cd03`: zero mismatches. Current fixture canonical SHA-256 `937276a424ecc315f576d8907c2db5d178af16c6e9afb98e51e7f11904d53e00`. Production unchanged from accepted source review.
- Disposition: clear for coordinator's comprehensive Gradle `check` on this candidate. Reviewer executes no builds/tests. Final closure of REV001/REV002 awaits passing corrected PostgreSQL regressions plus integrated result; browser/performance/deployment and untested edge-path limits above remain explicit.

## Final independent verification and verdict

- Inspected `build/T1-check.log`: coordinator command `./gradlew.bat --offline --no-daemon --console=plain check` executed `test`, `jacocoTestReport` and `check`, ending `BUILD SUCCESSFUL in 5m 33s`.
- Independently parsed all current `build/test-results/test/TEST-*.xml`: **547 tests, 38 suites, 0 failures, 0 errors, 0 skipped**, agreeing with durable `logs/technician/T1-RESULT.json`.
- Independently confirmed all scoped 43 passed: PostgreSQL repository 11, service 5, controller 9, authenticated SecurityIntegration 18. Named caught different-actor retry, same-actor stale failure and suspended nested-session regressions are present with zero failure/error/skipped nodes. Persisted role/session, same repeatable snapshot, literal search/AND filters, rank/time/display-ID order, unknown category, safe operational failure and reassigned history tests also passed.
- Authenticated actual Thymeleaf cases for populated queue/detail, escaped stored canaries, prior inactive author, local date/time, stylesheet link, invalid-filter 400 and safe detail 404 passed. Direct controller tests cover whole-read generic 503 with no protected results. This confirms U01/U02 corrections in the tested rendered paths, while browser interaction and actual 503 template rendering remain outside executed scope.
- Recomputed all 17 canonical UTF-8/LF candidate hashes after execution: **zero mismatches** against `453e4fea1443005c7302225908140394a19fb53b0d784a94419560afd9e6cd03`; reviewed production/test inputs equal executed candidate's recorded inputs.
- **Verdict: PASS for scoped T1 review. Close T1-REV-001 and T1-REV-002 as verified. No remaining actionable T1 findings.** Reviewer did not author fixes or execute tests/builds; independent inspection uses coordinator's execution artifacts plus fresh source/manifest review.
- Material limits: no Chrome/keyboard/visual acceptance, performance or deployment smoke; no live database. Cross-thread misuse, callback-exception closure and TransactionAwareDataSourceProxy edge paths were source-reviewed without separate runtime regressions. Queue/detail 503 have controller-model evidence rather than full-template rendering. Later T2–T5 behavior remains outside this iteration. These limits do not invalidate the corrected findings' specific passing regressions.
- Human teammate merge review remains pending. This verdict grants no push, merge, production or later-iteration authorization.
