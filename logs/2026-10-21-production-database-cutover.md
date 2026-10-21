# Production database cutover verification

- Requirements: NFR-DEP-001 / ADR-008, NFR-SEC-001/002, NFR-DOC-001.
- User requested live cutover and post-promotion verification. Single-project
  database separation is the approved scope; project/server/owner remain shared.

## Release and public checks executed

- GitHub API: CI run39437565941 attempt7 completed success at
  313ca61d24b694ba47589e1cd615b99362cef384. Tests/package, secret scan, container,
  Production staging including authenticated smoke and protected promotion passed.
- GitHub Production deployment7205163673 reported success with candidate
  https://facilityflow-3vhxmdjas-yooplo.vercel.app . Promotion job:
  https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39437565941/job/118746228333
- Vercel API lookup of facilityflow-ai.vercel.app resolved to that same candidate,
  deployment dpl_8MmfcCaFSfsCoVmgWJGsVzkMG1sQ, READY, production target, matching
  release commit. This establishes the live-domain/candidate mapping.
- Explicit Vercel alias record independently checked by parent:
  facilityflow-ai.vercel.app points to the same deployment ID and candidate URL.
- Public https://facilityflow-ai.vercel.app/health returned HTTP200 and status UP;
  /login returned HTTP200 with username/password controls. Signed-out /manager
  and /requester requests redirected to /login with the login form. No credentials,
  form submissions or application writes used in these automated HTTP checks.
- User explicitly confirmed on the live domain: new Production Manager login,
  prod-smoke ACTIVE account listing, Requester login and AI suggestions all work.
  These authenticated/browser checks are user-reported, not automated by this session.

## Preview configuration finding and correction

- Read-only Vercel configuration inspection found all three database variables
  still jointly targeted Preview/Production. A privately parsed Preview environment
  pull confirmed Preview pointed to facilityflow_prod / facilityflow_prod_app.
  This would send future Preview deployments to Production application data.
- Validated existing development settings with the application's database-check:
  restricted facilityflow_app, database postgres, verified TLS, connection passed.
  No local secret values printed. Owner/migration credentials were not used.
- Split SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME and
  SPRING_DATASOURCE_PASSWORD into Production-only existing entries and separate
  Preview-only entries using validated existing development values. Production
  values preserved; no deployment, promotion or database write triggered.
- The first bulk-create configuration request failed; original scopes were
  restored before retry. Individual variable creation succeeded. Final independent
  private pulls confirmed Production: facilityflow_prod / facilityflow_prod_app;
  Preview: postgres / facilityflow_app. Temporary environment files deleted in
  finally blocks; no passwords, tokens or provider keys retained in evidence.
- Rechecked live domain afterwards: same READY deployment, unchanged.

## Limits and remaining work

- Deployment-specific environment pull was unavailable; exact deployed database
  identity is supported by deployment mapping, current Production configuration,
  staging smoke and user authenticated checks, not a current_database() query
  executed by this session against Production. Hosted SQL/bootstrap evidence is
  recorded separately in logs/2026-10-20-separate-production-database.md.
- No new Preview deployment/authenticated Preview smoke run after the configuration
  correction. Existing Preview artifacts retain their original deployment settings;
  corrected variables apply to future deployments. No browser case submission,
  state transition, load, backup/restore, alert or rollback exercise performed here.
- Earlier provider-key rotation is user-confirmed, not independently reverified.
  No key value included in evidence or user-facing output.
- Setup scripts/tests/specs and evidence remain local on
  chore/separate-production-database; teammate merge review remains required.
- Rollback must use an artifact configured for facilityflow_prod. Pre-cutover
  Production artifacts point at development and are not isolated rollback targets.

## Fresh verification outcome

- Fresh deployment_verifier independently checked GitHub attempt7, Vercel live
  deployment identity/state/commit, six Preview-only/Production-only database
  variable entries, and public health/login/signed-out redirects. No blockers.
- Verifier did not pull/read secret values, query databases or mutate anything.
  Parent owns decrypted database identity checks; authenticated browser/AI result
  remains explicitly user-reported. No broader acceptance claimed.
- Temporary environment-file cleanup confirmed (zero task-prefixed files).
  Affected documentation relative links and git diff whitespace checks passed.
