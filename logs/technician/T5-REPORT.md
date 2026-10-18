# T5 shared screens and password-change report

Status: local implementation and automated verification passed: 646 tests in
49 suites, zero failures/errors/skips, executable packaging. Independent
source review passed; closing corrected-document verdict is in reviewer handoff.
Browser/keyboard/viewport/accessibility acceptance remains blocked/pending; T5
release acceptance is not complete.

## Authority and history

Baseline c15d26e on technician-workflow. User approved T5, requested an
implementation-phase pause with agents retained, then explicitly said “Resume”.
[Contract](T5-CONTRACT.md), [dispatch](T5-DISPATCH.md), exact lane packets and
[pause handoff](../handoffs/2026-10-18-technician-T5-pause.md) retain that history.
The pause involved no build/test/browser/reviewer/Git execution. Its inventory
is an authored-path record, not a tested candidate. Resume authorizes remaining
verification, necessary fixes and local commit; no push, merge or deployment.
Runtime/token metrics stay conversation-only, outside committed evidence.

Scope REQ-SH-002/004/006/009/010/012/014/018, account password limits and scoped
NFR-SEC-001/002, NFR-A11Y-001/002, NFR-REL-001, NFR-DOC-001. Identity role owner
retains accountability. No Manager reset/account administration, other-role
manual workflows, schema/migration/dependency changes or live provider/database.

## Actual agents

| Agent | Model/effort | Contribution |
| --- | --- | --- |
| Backend | gpt-6.1-sol/medium | Four new identity service/repository/exception classes, server actor and persisted access checks, atomic password/account version/audit with retained sessions. |
| UI | gpt-6-luna/high | Shared controller/header/password/About pages, role navigation, pending form handling, focus/skip/responsive styles and narrow Work Plan lifecycle polish. |
| Custom tester | gpt-6-luna/high | Service/PostgreSQL/controller/script tests and browser checklist; retained for mechanical fixture fixes after resume. |
| Coordinator | Parent retained | Fixed contracts/ownership, security matcher, real MVC/security tests and additional real encoder/PostgreSQL/JDBC-session integration, sole test runner, guides/evidence/Git. |

Fresh authors ran concurrently with exclusive paths, root plus at most three
active leaves. Workers performed no builds/Git/delegation. At user pause, all
three returned handoffs and stayed available; retained tester corrected its
fixture on resume. [Backend](../handoffs/2026-10-18-technician-T5-backend.md),
[UI](../handoffs/2026-10-18-technician-T5-ui.md),
[tester](../handoffs/2026-10-18-technician-T5-tests.md) handoffs have OS-temp peers.
Independent reviewer starts only after compile/database smoke and frozen inputs.

## Resulting behavior

Own-password service captures current server identity; raw new passwords use
8–24 Unicode code points without trimming/composition rules and exact confirmation.
Repository revalidates direct input; READ_COMMITTED REQUIRES_NEW locks the account
FOR UPDATE, validates active/same role/exact nonnegative session version, verifies
latest hash, then updates only password hash, account version and UTC time plus
one safe ACCOUNT_PASSWORD_CHANGED event. Audit/persistence/encoder failure rolls
back and exposes no operational causes. Existing maintained encoder supports
legacy PBKDF2 verification and new Argon2 output. Session_version/current session
and other existing sessions are retained; Manager reset remains outside scope.

Protected password/About pages share identity and role-relevant navigation.
CSRF/no-store and fixed303 apply; field/general errors never return credentials.
Password fields clear after errors while existing business drafts retain their
behavior. Generic pending script reads no values, suppresses duplicate submits
per form and restores pageshow; existing manual command controls keep their own
handler. Shared skip links/focus/labels/wrapping and explicit UTC/local-zone
display target the stated accessibility criteria. Work Plan invalidation disables
AI immediately on manual submit and clears obsolete pending status, retaining
read-only/plain-text/failure fallback and late-response guards.

## Preflight and corrections

- Initial Java compile and eight service/isolated PostgreSQL password/audit cases
  passed with zero failures/errors/skips: [result](T5-PREFLIGHT-RESULT.json), local
  build/T5-preflight.log and preserved build/T5-preflight-xml/.
- Work Plan and existing manual-command script tests passed. Generic pending-form
  test initially failed because its window fixture overwrote earlier listeners.
  Tester changed it to retain/invoke every listener; both-form reset expectations
  stayed intact and the actual-script rerun passed. Production code unchanged.
- During original static authorship integration, fixed workspace link, safe empty
  field-map errors, Work Plan invalidation text/button, test int counter and
  standalone security context were aligned before execution; not runtime findings.
- Added root-owned real stack integration to distinguish password encoder/login/
  session/audit proof from existing H2 MVC tests using a password-service double.
- First web-stack preflight: 45 cases, two harness failures (standalone circular
  view resolver and MockMvc empty query parameter). Corrected standalone case
  passed. The next run exposed a real NFR-SEC-002 issue: failed-login default
  exception storage retained the attempted credential in a JDBC session. Root
  replaced it with fixed credential-free error redirect and removed old exception
  attributes. The unchanged session-byte regression and a new existing-session
  failed-login case cover the fix. Initial/corrected raw XML remains ignored
  under build; aggregate failure results are T5-WEB-PREFLIGHT-INITIAL-RESULT.json
  and T5-WEB-PREFLIGHT-SECOND-RESULT.json. Final rerun passed 46 cases/three suites with zero failures/errors/skips,
  including actual session-byte assertions and real password/audit/encoder behavior.
  See T5-WEB-PREFLIGHT-RESULT.json, build/T5-web-preflight-final.log and preserved
  build/T5-web-preflight-final-xml/. Independent source/evidence verification
  is in progress; this production correction is explicitly highlighted for review.

## Outstanding acceptance and human gates

Browser discovery returned “Local browser use is disabled in Work settings”. No
browser acceptance occurred; the pending [checklist](T5-BROWSER-ACCEPTANCE.md)
covers latest stable Chrome/version, keyboard lifecycle, three viewport sizes,
password forms, AI fallback, forbidden actions and no-JS behavior. Automated MVC/
DOM fixtures do not substitute for browser/accessibility acceptance. No live
provider/hosted smoke was performed. Human teammate/identity-owner and existing
gateway/safety-corpus/release gates remain. Tests cannot certify advice or approve
a specification. Local commit does not authorize merge or release.


## Independent review and closeout in progress

Source/security/spec/test-adequacy review passed on frozen24 inputs with direct
execution evidence, including independent verification of the failed-login
credential correction and harness repairs. One consolidated finding T5-R1
(NFR-DOC-001) requested reconciliation of current User/Developer guides,
traceability/acceptance/reuse sections that still described the historical pause.
Coordinator rewrote those current sections to reflect resumed local checks and
real-stack evidence, preserving historical pause handoffs and explicit pending
browser/human/hosted gates. Corrected-document/full-check closing verdict is recorded in the reviewer
handoff. Source/test candidate remains unchanged. No security exception granted.

Scoped52 tests/seven suites passed with zero failures/errors/skips, covering
password repository concurrency/rollback, service validation and retained
Technician manual/AI service/controller behavior. See T5-SCOPED-RESULT.json and
preserved local build/T5-scoped-xml/. All three actual scripts passed in
T5-SCRIPT-RESULT.json. Full integrated check/bootJar passed 646 tests/49 suites with no
failures/errors/skips, plus JaCoCo and executable JAR. T5-RESULT.json records
actual suite totals and artifact identity; local build/T5-check.log and direct
build/test-results/test/TEST-*.xml contain execution evidence. All24 source/test
hashes and saved patch unchanged. Browser acceptance stays pending.


## Local close and remaining gate

The verified implementation/evidence is eligible for an exact local commit after
the independent reviewer closes T5-R1 against corrected guides and final results.
Commit identity is obtained from the commit containing this report (git log -1
-- logs/technician/T5-REPORT.md), avoiding a self-referential embedded hash. The
coordinator verifies exact staged paths/blobs against24 tested inputs, checks all
result records/whitespace/links and confirms clean index/tree after commit.
No push, merge, deployment or workflow configuration change is involved.

Measured changes against c15d26e: implementation/resources/tests add1372 and
delete31 lines; five guide/traceability/acceptance/reuse files add150 and delete3
lines. Counts include new files, exclude process evidence and contain no MP2
copied implementation. [Mechanical result](T5-MECHANICAL-RESULT.json) gives exact
paths/counts; [candidate](T5-CANDIDATE.json) identifies tested source/patch;
[full result](T5-RESULT.json), [web preflight](T5-WEB-PREFLIGHT-RESULT.json),
[scoped result](T5-SCOPED-RESULT.json) and [scripts](T5-SCRIPT-RESULT.json) give
executed evidence. Full run retains all original manual/provider regressions.

Remaining T5 milestone work: enable local browser use through the user's Work
settings, then execute the pending Chrome/version/keyboard/viewport/contrast/
manual-lifecycle/password/AI-fallback/forbidden-action checklist and review any
corrections. The blocked browser capability was reported and no workaround was
used to bypass it. Local automated/code acceptance is complete; full browser
acceptance is not. Existing teammate/identity-owner/gateway/corpus/hosted-release
gates remain explicit. Development-agent PASS is not human merge approval.
