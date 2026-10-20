# 20 October 2026 deployment readiness

- Date/timezone: 20 October 2026, Asia/Singapore.
- Agent: Codex implementation plus fresh read-only `deployment_verifier` required after the release-gate finding; Ponytail, Context7, Supabase and FacilityFlow handoff skills consulted.
- User task: create a separate branch and handle deployment following repository instructions.
- Baseline: `15736f8`; clean working tree at entry; branch created as `chore/deployment-readiness`.
- Authority: approved technical specification, NFR-DEP-001, NFR-DOC-001; outstanding operations/recovery gates NFR-OPS-001/002 and NFR-REL-002.
- Changes: `.vercelignore`, `.gitignore`, `.github/scripts/smoke-test.sh`, new `.github/scripts/smoke-test.test.sh`, `.github/workflows/ci-cd.yml`, `docs/Deployment.md`, `workflow/REUSE.md`, `workflow/TRACEABILITY.md`, this log and durable handoff.
- Decision: retain the approved Vercel container approach and existing CI/CD. Add a source-upload allowlist, exclude local Vercel metadata, clarify explicit database initialization and environment-scoped smoke secrets.
- Sources: [Vercel container support](https://vercel.com/kb/guide/does-vercel-support-docker-deployments), [upload exclusions](https://vercel.com/docs/deployments/vercel-ignore), [deploy CLI](https://vercel.com/docs/cli/deploy), existing initializer/profile code and database guides. Context7 confirmed documented upload allowlist syntax. Supabase changelog fetch failed because the web tool rejected its Markdown content type; no Supabase configuration or database changes performed.
- Verified: branch creation; upload-pattern assertions for seven excluded and eight required paths using Git ignore semantics; matching Dockerfile hashes; all five `src/test/js/*.test.cjs` checks; whitespace check.
- Gradle: first sandbox attempt stopped before running tasks because the global wrapper cache was not writable. Escalated `./gradlew.bat check bootJar --no-daemon` passed in 9m 12s: 726 tests across 59 suites, zero failures/errors/skips, JaCoCo and executable JAR packaging. Evidence: `build/test-results/test/TEST-*.xml`, `build/reports/tests/test/`, `build/reports/jacoco/`, `build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar` (generated, not committed).
- Not verified: actual Vercel CLI upload interpretation, container build (Docker unavailable), hosted deploy/spike, GitHub checks/environment configuration, Preview/Production separation, production promotion, rollback, monitoring/alert exercise, backup/restore, browser/load acceptance. No hosted URL exists from this session.
- Access blocker: no linked Vercel project, deployment environment token or GitHub token found; Vercel project/account and secret-store access requested from user. Do not infer Production values from ignored local configuration.
- Approval boundary: user authorized deployment preparation; teammate review is required before a non-trivial merge and explicit human approval before Production promotion under `workflow/agents/README.md`. Neither approval claimed.
- Suggestions accepted: reuse existing container and pipeline; keep credentials out of source upload and records. Rejected: switching hosting providers or rewriting deployment automation without a demonstrated need.
- Human reviewer/outcome: pending.

## Release-gate correction

The actual login template says "Sign in to your maintenance workspace." The
previous smoke script followed redirects and accepted any HTTP 200 containing
"workspace", allowing rejected credentials to pass. The shared smoke script now
requires the authenticated header's `action="/logout"` form, covering both Preview
and Production-candidate callers. A synthetic curl regression covers success and
rejected-login outcomes without network or secrets; Git Bash execution passed.
CI runs it in the verify job. A fresh read-only verifier completed the
mandatory post-finding review with no blockers (see below). This does not claim live
authenticated hosted verification.

## Fresh verifier result

`deployment_verifier` completed independent read-only review: no blockers found.
Reran shell regression, Bash syntax check, whitespace check and Dockerfile
comparison; inspected both smoke callers, all role landing templates, source
upload allowlist, initialization command and CI wiring. Hosted execution and
account/environment settings remain unverified. This agent review does not
replace required human teammate approval.
