# CI/CD and Vercel deployment

Backup/restore, health/alert exercises and final rollback procedure:
[Operations.md](Operations.md). Execution evidence is required in addition to setup.

This runbook implements the approved `NFR-DEP-001` and Technical Specification
deployment flow. Hosted Production deployment, rollback/restoration, backup/restore,
manual external-monitor exercises and Preview smoke have been verified as recorded
below. The five-minute monitor schedule is enabled, but operational schedule
acceptance remains pending until a timer-triggered execution is observed.

## What runs automatically

Every pull request runs these required checks from `.github/workflows/ci-cd.yml`:

1. Gradle wrapper validation, whitespace validation, all tests, JaCoCo and the
   executable Spring Boot JAR build.
2. A high/critical packaged-dependency vulnerability gate and a full-history
   Gitleaks secret scan; both scanners are versioned and checksum-verified.
3. A build of the same non-root Java 25 container used by Vercel.
4. When deployment is enabled, an internal-branch Preview deployment followed
   by health, login and authenticated safe-read smoke checks.

The safe-read check requires the authenticated logout form. A login-page response
cannot satisfy it; a dependency-free shell regression runs in CI.

After an approved merge to `master` and explicit Production enablement, the
workflow builds a Production candidate with `vercel deploy --prod --skip-domain`.
It smoke-tests the candidate before a
separate `production` job may promote that exact deployment without rebuilding.
The `production` GitHub environment must require a human reviewer before the
promotion variable is enabled.

`.github/workflows/rollback.yml` is a manual, confirmation-protected rollback to
the previous known-good Production deployment. Vercel Hobby limits rollback to
the immediately previous Production deployment.

## One-time GitHub configuration

Create these GitHub repository variables:

| Variable | Purpose |
| --- | --- |
| `VERCEL_ORG_ID` | Vercel account/team identifier for the linked project |
| `VERCEL_PROJECT_ID` | Vercel project identifier |
| `VERCEL_DEPLOY_ENABLED` | Shared deployment switch; set to `true` after Preview setup to enable PR deployments |
| `PRODUCTION_DEPLOY_ENABLED` | Leave unset or `false` until Production configuration and smoke credentials are complete; must also be `true` to stage a candidate on `master` |
| `PRODUCTION_PROMOTION_ENABLED` | Set to `true` only after the protected Production environment is configured |

Create these GitHub secrets; never put their values in source, logs or PR text:

| Secret | Purpose |
| --- | --- |
| `VERCEL_TOKEN` | Least-privilege Vercel CLI token for this project |
| `SMOKE_USERNAME` | Active synthetic smoke-test Account username |
| `SMOKE_PASSWORD` | Password for that synthetic Account |
| `SMOKE_BYPASS_SECRET` | Optional Vercel Deployment Protection automation bypass |

Create GitHub environments named `preview`, `production-candidate` and
`production`. Configure at least one teammate as a required reviewer for
`production`. Branch protection on `master` must require this PR's test,
secret-scan and container checks plus one approving teammate review.

Store `SMOKE_USERNAME`, `SMOKE_PASSWORD` and, when needed, `SMOKE_BYPASS_SECRET`
as **environment secrets**: Preview credentials in `preview`, Production
credentials in `production-candidate`. Under ADR-008, use the new Production
Requester's credentials there and retain development credentials in `preview`;
environment secrets are not shared automatically. Never enable promotion before verifying the
required-reviewer gate; if the repository plan cannot enforce it, keep
`PRODUCTION_PROMOTION_ENABLED=false` until an approved alternative is agreed.

For Preview-only operation, keep `VERCEL_DEPLOY_ENABLED=true`,
`PRODUCTION_DEPLOY_ENABLED=false` and `PRODUCTION_PROMOTION_ENABLED=false`.
Missing `production-candidate` credentials do not affect Preview. When staging
is enabled, a preflight requires `SMOKE_USERNAME` and `SMOKE_PASSWORD` before
creating a Production deployment. Also set `SMOKE_BYPASS_SECRET` in that GitHub
environment when Vercel Deployment Protection applies. The bypass secret can be
reused because Preview and Production belong to the same Vercel project.

Do not allow Vercel Git integration to auto-assign the Production domain. The
workflow's staged candidate and protected promotion are the only Production
path. Preview Git integration may also be disabled to avoid duplicate builds.

## Separate database configuration

The latest stakeholder decision, [ADR-008](../workflow/decisions/ADR-008-separate-production-database.md),
supersedes ADR-007's shared-database exception. Retain `facilityflow-dev` for local
and Preview; create fresh `facilityflow_prod` in the same Supabase project with independent database credentials
and only required accounts. Follow [ProductionDatabaseSetup](ProductionDatabaseSetup.md)
for provisioning, migration, Manager bootstrap, smoke account creation and cutover.
Production cutover was verified on 21 October 2026; see
[the evidence and limits](../logs/2026-10-21-production-database-cutover.md).

Required application variables in both environments are:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD`;
- `SUPABASE_SSL_ROOT_CERT=/app/supabase-root-2021.crt`; both images include the
  provider's public CA from `src/main/resources/certs/supabase-root-2021.crt`;
- `SESSION_COOKIE_SECURE=true`;
- `AI_ENABLED`, `SOCLAAS_BASE_URL`, `SOCLAAS_API_KEY` and `SOCLAAS_MODEL`;
- `SPRING_PROFILES_ACTIVE=production` for Production;
- `DEMO_DATA_ENABLED=false` for Production;

Use existing migrations and the explicit `database-migrate` profile with the new
Production migration identity, then bootstrap one Manager with demo mode disabled
in a trusted operator environment. Follow the full fresh-database procedure in
[ProductionDatabaseSetup](ProductionDatabaseSetup.md); do not initialize or migrate
the existing development database as part of this cutover. Never enable either
operator profile on Vercel. Normal web startup validates schema readiness without
schema writes. Create the synthetic Production Requester via the normal Manager
workflow and update GitHub `production-candidate` smoke credentials before
requiring its authenticated checks to pass.

The workflow supplies `APP_VERSION` as the immutable Git commit SHA. Vercel
supplies `PORT`; `application.yml` binds it and enables graceful shutdown behind
the platform proxy. Application state and Spring Sessions remain in PostgreSQL,
not in the stateless container.

Keep the Preview and Production smoke Accounts synthetic and minimally
privileged. The smoke script never prints credentials and deletes its temporary
cookie and response files on exit.

## First enablement and release

1. Create the Vercel project with the repository root as its root directory.
   Vercel auto-detects `Dockerfile.vercel`; no `vercel.json` is required for this
   single HTTP service. Verify container availability and limits for the actual
   account during the deployment spike.
2. Configure separate databases and independent database credentials under ADR-008;
   follow ProductionDatabaseSetup.md and keep demo seeding disabled.
3. Add the GitHub variables, secrets and protected environments above.
4. Open a test pull request with `VERCEL_DEPLOY_ENABLED=true`; retain the Preview
   URL and smoke result in the GitHub job summary.
5. Merge only after the required checks and teammate approval. For a Production
   release, finish Production setup and the environment-scoped smoke secrets,
   then explicitly set `PRODUCTION_DEPLOY_ENABLED=true` before the approved
   `master` push. Otherwise Production staging is skipped.
6. Confirm the Production candidate smoke result, then approve the protected
   `production` job. Verify that promotion used the same candidate URL.
7. Record the deployment URL/ID and sanitized result under `logs/`; never copy
   environment values into the evidence.
8. Rehearse the manual rollback against the immediately previous deployment and
   separately record the required database restore exercise.

## Local verification

The committed `.vercelignore` allowlists only container build inputs for CLI
source uploads, excluding local configuration, data, logs and reference files.
Keep this list aligned with `COPY` inputs when changing `Dockerfile.vercel`.
`.vercel/` contains local project/environment metadata and is Git-ignored.
Directory allowlist entries must omit trailing slashes (`!/gradle`, `!/src`):
Vercel CLI 64.5.0 omits their contents when the entries end in `/`.
Both hosted deployment jobs inspect `vercel deploy --dry --json` and reject an
upload missing tracked build inputs or containing paths outside the allowlist.
Git ignore checks alone do not validate Vercel's directory traversal.

Run the same application checks and image build without deployment credentials:

```text
./gradlew clean check bootJar --no-daemon
bash .github/scripts/smoke-test.test.sh
docker build --file Dockerfile.vercel --tag facilityflow:local .
```

The public `GET /health` endpoint returns only `{"status":"UP"}` when both the
application and database are reachable, or `{"status":"DOWN"}` with HTTP 503.
Provider, credential and connection details are never returned.

The bundled public certificate was checked against the
[provider download](https://supabase-downloads.s3-ap-southeast-1.amazonaws.com/prod/ssl/prod-ca-2021.crt)
on 20 October 2026. Its DER SHA-256 is
`807025ad50d4ed219d2c9c7d299c004f824eb00cf7f65afef607d07b72e6cafa`;
expiry is 26 April 2031. It contains no private key. Recheck the provider CA and
connection when Supabase rotates certificates; retain JDBC `sslmode=verify-full`.
The development pooler passed CA and hostname verification using this CA and
failed against a Mozilla public trust bundle. The hosted Preview container
passed health/login/authenticated-read smoke checks with the packaged CA.

Platform references checked 20 October 2026:
[container deployment support](https://vercel.com/kb/guide/does-vercel-support-docker-deployments),
[source upload exclusions](https://vercel.com/docs/deployments/vercel-ignore), and
[CLI deploy flags](https://vercel.com/docs/cli/deploy).
Documentation support does not establish a successful hosted deployment.

## Verified Preview evidence

Revision `7a05add` passed test/package, secret scan, image build, and Preview
deployment with health/login/authenticated-read smoke in
[CI run 39385940814](https://github.com/CS3227-2610-MP3-FacilityFlowAI/CS3227-2610-MP3/actions/runs/39385940814).
Production jobs were skipped. The user reported Requester AI suggestions appeared
on revision `2699447`'s Preview; this is one browser invocation, not a reliability
or output-quality assessment. Detailed results and limitations are in
[the deployment log](../logs/2026-10-20-deployment-readiness.md) and
[the hosted AI log](../logs/2026-10-20-hosted-ai-connectivity.md).

## Verified deployment acceptance

PR17 was teammate-approved and merged as `f56d15e`. The merged release passed
CI, staged authenticated smoke and protected human promotion. A protected rollback
to the prior isolated Production artifact succeeded, followed by protected
restoration of the tested PR17 artifact. The live domain points to
`facilityflow-7fiqyuutw-yooplo.vercel.app`; public health/login/access-control checks
passed, and the user confirmed Manager, Requester and AI functionality.

Fresh Preview smoke, encrypted backup/isolated restore, portable artifact checksum,
external health checks and alert notification delivery are verified. Backup access
is restricted to master. Both schedules are enabled; first timer-triggered runs
were not yet observed. GitHub schedules can be delayed; this is not a guaranteed
five-minute SLA or future recovery-time guarantee.

See [post-merge acceptance](../logs/2026-10-21-post-merge-acceptance.md) for actual
run/deployment/artifact IDs and [operations evidence](../logs/2026-10-21-operations-completion.md)
for the preceding exercises. These checks do not establish load or general AI-quality
acceptance. Keep reviewing backup artifacts and monitor workflow activity after handover.
