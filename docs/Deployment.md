# CI/CD and Vercel deployment

This runbook implements the approved `NFR-DEP-001` and Technical Specification
deployment flow. It configures automation; it does not claim that a hosted
Production deployment, backup restore or external monitor has already been verified.
Hosted Preview deployment and smoke evidence is recorded below.

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
credentials in `production-candidate`. For the ADR-007 shared database, reuse the
same synthetic Requester credentials in both GitHub environments; environment
secrets are not shared automatically. Never enable promotion before verifying the
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

## Current single-database configuration

The user authorized [ADR-007](../workflow/decisions/ADR-007-single-database-deployment.md)
on 20 October 2026: reuse `facilityflow-dev`, existing runtime credentials,
Accounts and records for this coursework deployment. Apply existing Preview
variables to the Vercel Production target and add the Production profile below.
No new database, data copy, reseeding or initialization is needed.

Preview/local and Production share data, sessions and AI limits: changes from any
of them affect the same database. Keep the dataset synthetic and use isolated
databases for automated tests. Distinct Vercel hostnames/environment targets,
server-side authorization and human release gates remain in place. Known exposed
credentials must be rotated rather than treated as safe by this exception.

Required application variables in both environments are:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD`;
- `SUPABASE_SSL_ROOT_CERT=/app/supabase-root-2021.crt`; both images include the
  provider's public CA from `src/main/resources/certs/supabase-root-2021.crt`;
- `SESSION_COOKIE_SECURE=true`;
- `AI_ENABLED`, `SOCLAAS_BASE_URL`, `SOCLAAS_API_KEY` and `SOCLAAS_MODEL`;
- `SPRING_PROFILES_ACTIVE=production` for Production;
- `DEMO_DATA_ENABLED=false` for Production;

For this existing initialized database, retain its current schema and Accounts;
do not rerun provisioning, migrations or bootstrap merely to change deployment
targets. Ordinary web startup validates readiness without schema writes.

For a future fresh database, provision and migrate using the restricted migration
identity and the explicit `database-migrate` profile described in
[DatabaseCompletion.md](DatabaseCompletion.md). Check existing migration history
first using [DeveloperGuide.md](DeveloperGuide.md); do not repair or reseed it to
make deployment pass. Ordinary web startup does not migrate or initialize data.

Only for a future empty Production database, initialize once from the release JAR in a
trusted operator environment, using the application database identity,
`DEMO_DATA_ENABLED=false`, and temporary `BOOTSTRAP_MANAGER_USERNAME`,
`BOOTSTRAP_MANAGER_DISPLAY_NAME` and `BOOTSTRAP_MANAGER_PASSWORD` secrets:

```text
java -jar build/libs/facilityflow-ai-0.1.0-SNAPSHOT.jar --spring.main.web-application-type=none --spring.profiles.active=database-initialize,production
```

Remove the bootstrap secrets after success. The web deployment uses only
`SPRING_PROFILES_ACTIVE=production`; never enable `database-initialize` or
`database-migrate` on Vercel. Create the synthetic smoke Account through the
normal authorized Manager workflow before enabling authenticated smoke checks.

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
2. Apply the existing database/application values to Preview and Production in
   Vercel under ADR-007; add the Production profile and keep demo seeding disabled.
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

## Still requiring hosted evidence

- first Production-candidate build and smoke;
- protected no-rebuild promotion and rollback rehearsal;
- verify the intentional shared database/credential targets against ADR-007;
- five-minute external monitoring and alert exercise;
- provider backup verification and database restore rehearsal.
