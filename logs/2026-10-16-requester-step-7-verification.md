# Requester step 7: local integration, security and usability verification

- Date/timezone: 16 October 2026, Asia/Singapore.
- AI/tool/role: Codex Developer; fresh scoped verifier `verify_requester_checks`.
- Task/intention: commit step 6, then implement section 9 of REQUESTER-PLAN.
- Branch/baseline: `feat/requester-workflow`; step 6 committed as `7cc20eb`.
- Authority: approved v0.1 ROLE-REQUESTER, PRODUCT, INTEGRATION, AI-SECURITY and
  TECHNICAL specs. REQ-RQ-001–017, RQ-A01–07, RQ-AI01–03, Requester AIS cases,
  NFR-PERF-001, NFR-A11Y-001/002, NFR-REL-001 and AI-SEC-027.
- Changes: explicit opt-in Gradle browser/load task; standard-library Node/CDP
  runner; application/session restart regression; 12-case versioned synthetic
  AI evaluation corpus. No application source, migration or runtime default changes.

## Environment and commands

Windows, Java 25, Gradle 9.1.0, disposable PostgreSQL 17.7.0 using restricted
application role and V1–V5 migrations; Node 24.19.0; real headless desktop Chrome
155.0.8059.41. Latest-stable status is unconfirmed, so NFR-A11Y-002 is partial.
Only synthetic data, loopback HTTP and fake AI/local provider servers were used.
The explicit browser task alone disables Secure cookies for its loopback HTTP;
normal security tests and runtime defaults retain Secure cookies.

Commands executed:

```powershell
$env:GRADLE_USER_HOME = 'C:/Users/jjwr2/.gradle'
.\gradlew.bat test bootJar
.\gradlew.bat requesterVerification
.\gradlew.bat requesterVerification -PrequesterPoolSize=5
```

`test bootJar` passed in **6m 10s: 114 tests, zero failures/errors/skips**.
The new corpus and graceful restart tests passed (0.115 s and 7.089 s).
Both Node form/review self-checks, runner syntax and `git diff --check` passed.
The opt-in task deliberately remains failing when the local p95 gate fails. Chrome/Node
are not prerequisites of ordinary `test` or CI. Reports/screenshots/profile data
are ignored under `build/verification/requester/`; JUnit XML/HTML remain under
`build/test-results/` and `build/reports/tests/` for their respective tasks.

## Executed browser and load evidence

The corrected pool-5 run passed **67 browser assertions**: login failure/success,
role redirect/counts, retained server validation errors, separate AI review,
keyboard field acceptance/focus, discard, ordinary create/edit/update/cancel,
Keep as default, no-match guidance, foreign-content exclusion, password change
and logout. Labels and page-level overflow were checked on dashboard, create,
detail, update, cancel, list and password at 760×600, 1024×700 and 1440×900.
Login was checked at 1024×700. This does not establish exhaustive keyboard,
screen-reader, zoom or WCAG compliance; edit shares the create template but was
not separately checked at all three viewports. Body/link/error colors on white
have calculated ratios 16.29, 8.19 and 8.15. The verifier visually inspected the
760×600 create screenshot without observing clipping.

Each load run authenticated **50 distinct Requesters** and issued 400 HTTP GETs
with 50 concurrent workers. Each worker fetched dashboard, list, OPEN filter,
own detail, search, About, password and dashboard sequentially. Login, AI and
two warm-up requests per user were excluded. Every measured response was 200.
Timings include loopback network and full body consumption, whereas the spec
excludes client network; these measurements do not isolate server processing.

| Local configuration | Requests/errors | p50 | p95 | Maximum | Local ≤1,000 ms gate |
| --- | --- | --- | --- | --- | --- |
| Default development pool 2 | 400 / 0 | 986 ms | 1,733 ms | 2,156 ms | FAIL |
| Test-only diagnostic pool 5 | 400 / 0 | 913 ms | 1,467 ms | 2,417 ms | FAIL |
| Independent final retest, default pool 2 | 400 / 0 | 832 ms | 1,413 ms | 1,752 ms | FAIL |

Baseline `load-baseline-pool-2.json` and diagnostic
`load-diagnostic-pool-5.json` preserve both runs locally. Whole-run 20 ms Hikari
sampling for pool 5 found peak active 5 and waiting 44. This suggests connection
contention; it is not an isolated root-cause analysis or timed-load-only metric.
No pool default, authorization query, security check or target was weakened.
ADR-004 deliberately specifies a two-connection development pool. Production
capacity and server-side measurement require the persistence/deployment owner.
NFR-PERF-001 remains unverified with a failing conservative local gate.

Fresh verifier reran the final harness independently in 2m 11s: all 67 browser
assertions passed again, followed by the unchanged p95 failure. Evidence is
`final-retest-pool-2.json` and task JUnit XML (one test, one load-gate failure).
Whole-run default-pool peaks were active 2, waiting 47. The variation between
pool-2 runs also prevents attributing improvements to pool size alone.

The Java post-run database count/status assertions follow the successful-process
assertion and were not reached after the load failure. Browser confirmation of
saved state passed; do not claim those separate final SQL assertions passed.

## Requester acceptance evidence map

Methods below are in `RequesterRequestIntegrationTest` unless stated otherwise.
Existing tests were retained and rerun with the normal suite; browser evidence
supplements their transaction/security assertions.

| Cases | Deterministic evidence | Practical limit |
| --- | --- | --- |
| RQ-A01/A02 | `reqRq002005CreateListDetailIgnoresForgedAuthorityAndEncodesOutput`, `reqRq002011FieldErrorsRetainInputAndInactiveCategoryCreatesNothing`, concurrent submission; Chrome create/error | Local PostgreSQL only |
| RQ-A03 | `reqRq009OwnScopeIncludesManagerRecordedAndHidesPrivateHistory`, foreign/nonexistent and wrong-role tests; Chrome foreign canary | No hosted check |
| RQ-A04 | `reqRq006OwnOpenEditIsAuditedAndDoesNotChangeOwnershipOrAcceptStaleReplay`, assignment/category races; Chrome edit | Assigned state is a controlled fixture |
| RQ-A05 | `reqRq007CancellationRequiresExplicitDecisionConfirmationAndReasonThenRejectsLaterWrites`; Chrome Keep/explicit cancel | Local workflow |
| RQ-A06 | audit rollback, duplicate submission, mutation rollback and concurrent edit/cancel/update tests | No crash/backup recovery claim |
| RQ-A07 | manager-recorded ownership/history fixtures and owner mutation rights | No Manager service exists to exercise end to end |
| RQ-AI01 | `reqRq015016ReviewUiAndExplicitOrdinarySubmissionRecheckStateRegardlessOfAiFlags`, DOM self-checks; Chrome field acceptance/discard/ordinary create | Fake model |
| RQ-AI02 | `SmartReportGatewayTest` and `smart-report-evaluation-v1.json` | Deterministic trust/schema checks; no live model immunity/usefulness claim |
| RQ-AI03 / NFR-REL-001 | disabled/malformed/timeout/rate-limit zero-write tests; DOM manual-input/failure/late-response tests | Browser used fake success; failure coverage is HTTP/DOM |
| REQ-RQ-011 persistence | `reqRq011Rel001SavedReportAndJdbcSessionSurviveApplicationRestart` | Graceful app restart on same PostgreSQL; not a database restart/restore |

## Requester AI security record

- Asset/trust boundary: authenticated browser → role/object service → allowlisted
  AI context → untrusted typed output → explicit ordinary human submission.
- Actor/preconditions: synthetic Requester, foreign Requester, Manager/Technician
  denial fixtures; disposable database; local fake transport/provider only.
- Inputs/steps: versioned corpus has normal, ambiguous, boundary, direct,
  current-form indirect, delimiter, encoded/multilingual, hidden/private canary,
  malformed, markup and invalid-category cases. No sensitive real data.
- Allowed result: typed suggestion or safe rejection with no automatic mutation.
- Forbidden result: protected context, provider charge on denied call, executable
  output, broader authority, raw sensitive logging or discarded manual input.
- Control: normal corpus draft and ordinary manual submission.

| AIS cases | Executed local evidence | Remaining qualification |
| --- | --- | --- |
| T01–03 | versioned gateway corpus and system/user-message separation assertions | Payload transport/schema behavior, not live-model attack resistance; stored cross-role data are excluded from Requester context |
| T04–05 | HTTP/service foreign-object, wrong-role, inactive/session-version denials and no-provider assertions | Requester slice only |
| T06–08 | gateway/schema input/output boundaries, markup, invalid category; DOM text-only rendering | Technician/Manager enums belong to their owners |
| T10–11 | rolling quotas/global lease tests; gateway/transport retry, status and deadline tests | No deployed multi-instance measurement |
| T13 | forged authority/accepted/result/status/priority and stale ordinary-submit denials | Full cross-role flow pending |
| T14 | metadata/canary/secret exclusions in gateway/transport/database tests; sanitized runner artifacts | Formal human whole-project logs/handoff audit pending |
| T15 | Not executed as a formal poisoned-handoff scenario | Process case remains pending; role documents alone are not evidence |
| T16–17 | in-flight access/catalogue recheck; CSRF/limit denial before catalogue/context/provider | Local fake inference |
| T18 | system-only/private canary and disclosure-marker rejection | Known markers only; semantic leakage assessment deferred |

T09/T12 are Technician/Manager features, not completed by these tests.

## Corrections, review and unresolved work

The first two opt-in attempts failed due to test automation: Enter events lacked
text, then an interaction raced deferred script readiness. Corrected native key
events and document/button readiness gates; product behavior/assertions were
not relaxed. Subsequent runs reached all browser checks and failed only load.
Fresh verifier `verify_requester_checks` inspected the corrections, current 67
checks, screenshot, isolation, corpus and restart design. It found no production
security blocker. Its harness finding (sampler cleanup on process-start failure)
was fixed by moving process creation inside `try/finally`. The verifier confirmed
cleanup scope and independently executed the updated harness as recorded above.

Accepted suggestions: standard Node/CDP, opt-in task, synthetic versioned corpus,
restart coverage, preserve failed measurements and whole-run queue diagnostics.
Rejected: changing runtime pool defaults to hide a failure, relaxing the target,
claiming fixture transitions complete cross-role integration, or treating fake
model output as a live usefulness/security evaluation.

Step 7 exit is **not met**: local load gate, full Manager/Technician lifecycle,
latest-stable confirmation, remaining manual accessibility and teammate review
are open. Live SoCLaaS is explicitly deferred by the user; hosted/CI/deployment,
backup/restore and production capacity remain unverified. No push/merge/deploy,
hosted writes, secret retrieval or external messages occurred.

Human reviewer/outcome: pending. Next planned checkpoint is section 10
documentation/reflection/reuse; it must retain these open verification gates.
