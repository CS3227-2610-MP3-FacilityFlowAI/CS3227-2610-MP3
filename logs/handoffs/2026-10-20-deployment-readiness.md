# Deployment continuation handoff

- Source agent: Codex deployment preparation session.
- Destination agent: next deployment session / release verifier.
- Task/goal: deploy FacilityFlow through approved Vercel/GitHub release flow.
- Approved requirement IDs: NFR-DEP-001, NFR-DOC-001; NFR-OPS-001/002, NFR-REL-002 release evidence outstanding.
- Approved specification revision/evidence: technical specification v0.1; user requested a separate deployment branch.
- Authorized scope/permissions: deployment preparation on `chore/deployment-readiness`, baseline `15736f8`; preserve unrelated work. No expanded promotion, data mutation or merge permission.
- Inputs: `docs/Deployment.md`, `.github/workflows/ci-cd.yml`, `.github/workflows/rollback.yml`, `workflow/specs/TECHNICAL-SPEC.md`, `logs/2026-10-20-deployment-readiness.md`.
- Assumptions: approved Vercel container direction retained; actual account support still requires a spike.
- Unresolved: target Vercel project/account and deployment access; named separate Preview/Production database targets and required hosted evidence.
- Files changed: see session log; configuration/docs/evidence only.
- Commands/tools/results: see session log; Gradle `check bootJar --no-daemon` passed, 726 tests/59 suites, no failures/errors/skips, plus JaCoCo and JAR. Five JavaScript checks and synthetic smoke regression passed; container execution and hosted checks not run.
- Security considerations: no secrets recorded or hosted writes performed; runtime must use restricted identity and no initialization/migration profile. Source upload restricted to build inputs.
- Suggestions accepted/rejected: existing container/CI retained; no provider switch or speculative automation rewrite.
- Human approval required and reason: teammate review before non-trivial merge; human Production promotion gate mandated by `workflow/agents/README.md` and NFR-DEP-001.
- Human reviewer/outcome/evidence: pending.
- Next authorized action: validate current branch/log state, obtain target and deployment access without sharing secrets in chat, inspect GitHub/Vercel environment configuration, run Preview spike and smoke checks. Do not promote or merge without required human gates.
- Suggested skills: Context7 for Vercel CLI, Supabase for hosted database tasks, supabase-postgres-best-practices before any database/schema changes, FacilityFlow handoff for transition.
- Status: deployment awaiting access; local preparation review pending.

## Fresh verifier dispatch

A release-gate finding was reproduced by inspecting the actual login page: its
text includes workspace, so the old safe-read check accepted an unauthenticated
response. `.github/scripts/smoke-test.sh` now requires the authenticated logout
form. `.github/scripts/smoke-test.test.sh` exercises rejected and accepted login
outcomes with synthetic curl responses; it passed locally with Git Bash. CI runs
this regression. Fresh verifier scope is read-only inspection and rerunning this
check plus whitespace/container/upload checks as needed. No hosting, merge,
secret access or Production permissions are delegated. Review the actual header
and login templates and all smoke callers. Record findings in a message; parent
owns evidence updates. Suggested skill: Ponytail review.

## Fresh verifier return

Fresh verifier `deployment_verifier` reported no blockers after independently
rerunning the synthetic smoke regression, Bash syntax, whitespace and Dockerfile
equality checks, and inspecting callers/templates/upload list/runbook. See session
log. Hosted evidence and human teammate approval remain pending. Receiving
session must verify access and release gates before any hosted mutation.

## Preview CA continuation: fresh verifier scope

User selected existing development Supabase for Preview and reported saving
Vercel database variables. TLS probe required the provider-specific public CA;
public Mozilla CA bundle failed. Local CA matches official Supabase download
fingerprint recorded in Deployment.md. Public cert now lives under
src/main/resources/certs/ and both Dockerfiles copy it to /app/supabase-root-2021.crt.
Verify certificate provenance/expiry/public-only content, identical container
COPY inputs, allowlist compatibility and runbook path; read-only review only.
Do not read ignored secrets or query/mutate hosted state. Parent is running the
existing database-check SELECT 1 profile; do not duplicate network/database
checks. No merge/promotion permissions granted. Record findings to parent.
Suggested skills: Ponytail review, Supabase, FacilityFlow handoff.
Parent verified actual JDBC SELECT 1 with packaged CA file, certificate assertions,
22-second bootJar rebuild and certificate content inside the JAR. No database
mutation performed. Next user step: set Preview SUPABASE_SSL_ROOT_CERT to
/app/supabase-root-2021.crt; actual container/hosted smoke remains required.
Fresh CA verifier returned no blockers after independently checking official CA
provenance, expiry/public-only content, Dockerfile parity, upload inclusion,
runbook path and retained verify-full. Human review/hosted checks remain pending.

## Vercel upload failure: fresh verifier dispatch

Screenshot CI run 39378714664 job 118485945317 failed COPY gradle gradle.
Actual installed Vercel CLI 64.5.0 inspectDeploymentFiles reproduced old
.vercelignore inventory of five root files; !/gradle/ and !/src/ prevented descent.
Changed patterns to !/gradle and !/src; real inventory now includes 262 files,
all 260 tracked build inputs. New .github/scripts/check-vercel-inputs.cjs validates
inventory from both deployment jobs' vercel deploy --dry --json steps. Missing
src/gradle and injected .env.local regressions pass (rejected appropriately).
Fresh verifier: read-only inspect correction and both callers; independently run
actual installed CLI collector/checker and negative regressions. Cached CLI:
C:/Users/jjwr2/AppData/Local/npm-cache/_npx/4926d0e14b8422cc/node_modules/vercel/dist/chunks/chunk-WN7EJWN4.js
exports require_dist(); use inspectDeploymentFiles({path:process.cwd()}) offline.
Do not access ignored secrets, query hosted DB, deploy or edit. Parent owns logs.
No merge/promotion permission. Suggested skill Ponytail review; handoff as usual.
Fresh upload verifier returned no blockers: actual CLI collector -> checker passed;
missing-gradle, missing-src and injected .env.local inventories rejected; both
pre-deployment dry-run pipelines/JSON shape reviewed and whitespace passed.
Next authorized action: push correction to PR16 and verify new hosted CI/Preview
result. Do not merge or promote Production. User's .gitignore edits preserved.

## Verified Preview return

Authoritative candidate 8a91d1d01cbde65db42da1993c18ff119720d7fe passed GitHub
run 39380229676 attempt 2 (test/package, secret scan, image build, Preview deploy
and health/login/authenticated-read smoke). Production jobs skipped. Deployment
7193242122 succeeded at https://facilityflow-qg57qryyc-yooplo.vercel.app . See
session log for evidence URLs. Earlier pending Preview annotations are superseded
for this narrow hosted smoke scope. Browser/AI/full-role/operations/Production
acceptance remains pending. Next user action: browser login/manual workflow checks
on development Preview; disable shared deploy flag before merge until Production
is configured and reviewed. Human review/merge/promotion gates remain unchanged.
Evidence updates are local, not pushed; preserve user CLI .gitignore edits.

## Login UI polish continuation

User requested tighter input focus ring and red failed-login text. NFR-A11Y-001 /
UIX-017-020 apply. workspace.css now uses a 2px ring/1px offset for input/select/
textarea, while muted login paragraph styling excludes role=alert. No behavioral
or schema change. Source agent Codex; next receiver visual reviewer/user.
See logs/2026-10-20-login-ui-polish.md for scope, checks and limitations.
CSS cascade/load order inspected and whitespace passed; browser/hosted visual
verification and human teammate review pending. Preserve existing unrelated
.gitignore and deployment evidence edits. No new merge/promotion permissions.
Suggested skills: Ponytail for any follow-up styling; FacilityFlow handoff.

## Hosted AI transport diagnostics verifier dispatch

Source Codex -> fresh provider verifier. User reports Requester AI 503 in newer
Preview. Runtime safe logs confirmed READINESS/NETWORK_FAILURE. Enabled flag and
endpoint checked privately; model/key variables present. Local credential-free
Python and Java probes reached models endpoint (401). Vercel root cause unknown.
Inspect ProviderDiagnostics.java, SocLlmHttpsProviderAdapter.java and new test
in ProviderDiagnosticsTest.java: added closed transport classifier before cause
is discarded, bounded eight causes, no messages/stacks/raw content. Preserve
failure mapping, timeout/cancel semantics and TLS. Exact requirements/evidence in
logs/2026-10-20-hosted-ai-connectivity.md. Parent running focused test/build; do
not duplicate full suite or read ignored config. Read-only review, no deployments,
queries, edits or expanded merge/promotion permission. Suggested Ponytail review
and FacilityFlow handoff skills. Report findings to parent, parent owns records.
Fresh provider verifier returned no blockers; focused 30-test/two-suite build
passed with no failures/errors/skips. User confirmed READINESS/NETWORK_FAILURE.
Proceed through existing Preview CI only, then inspect TRANSPORT reason. No
Production promotion/merge or raw diagnostic content authorized.
Diagnostic revision 2699447 Preview passed hosted CI/smoke at
https://facilityflow-bo1pgnpy4-yooplo.vercel.app (run39384235933, deployment7193762173).
Await user-authenticated AI attempt to capture TRANSPORT reason; no AI fix claimed.
Use Vercel logs --since 5m, not 1h (Hobby retention rejected old query). Emit only
closed gateway enum codes. No need to access credentials, weaken CSP/TLS or
repeat untrusted provider payloads. Detailed evidence in hosted-AI session log.

## Successful Requester AI retry return

User reported suggestions appeared on diagnostic Preview 2699447 at
https://facilityflow-bo1pgnpy4-yooplo.vercel.app . Exact-deployment five-minute
runtime query returned zero gateway failure codes (CLI exit0). This supports one
user-reported successful browser invocation; no response content was collected.
Previous NETWORK_FAILURE cause is unconfirmed; diagnostic logging is not a
claimed connectivity fix. No further change needed for this attempt. Preserve
Production/human review gates and user's .gitignore edits. Evidence remains local;
see hosted-AI log. Next authorized work: review/record evidence or investigate
with closed diagnostics if a failure recurs, without broadening permissions.

## Production gating correction: fresh verifier dispatch

Source Codex -> fresh deployment verifier. User reported master Production
candidate failed for missing production-candidate environment smoke secrets.
Remote run39382394925 confirms staged smoke failed; promotion skipped. Master
9151650 includes original shared-gate workflow; PR16 remains open. Proposed fix
on deployment branch adds separate PRODUCTION_DEPLOY_ENABLED opt-in and smoke
credential preflight before Production linking/deployment. No secret copies or
API-setting changes. Requirements NFR-DEP-001/DOC-001. Inspect changed workflow,
new deployment-gates.test.cjs and runbook; rerun focused test with Git Bash in PATH
and existing smoke regression, no Java/network/secret/DB calls. Read-only, no edits,
merges or promotion. Tests currently pass actual conditions/preflight fixtures.
See logs/2026-10-20-production-deployment-gate.md. Parent owns final evidence.
Suggested skills Ponytail review, FacilityFlow handoff. Preserve unrelated edits.
Fresh Production verifier returned no blockers; gate/preflight and smoke
regressions/whitespace independently passed. Preserve existing Preview while
Production opt-in remains false/unset. Correction is proposed via PR16, not a
merge or production promotion. Historical master run remains failed until a
new revision supersedes it. See production-deployment-gate log.
