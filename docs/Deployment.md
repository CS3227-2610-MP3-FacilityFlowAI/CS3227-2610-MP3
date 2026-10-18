# CI/CD and Vercel deployment

This runbook implements the approved `NFR-DEP-001` and Technical Specification
deployment flow. It configures automation; it does not claim that a hosted
deployment, backup restore or external monitor has already been verified.

## What runs automatically

Every pull request runs these required checks from `.github/workflows/ci-cd.yml`:

1. Gradle wrapper validation, whitespace validation, all tests, JaCoCo and the
   executable Spring Boot JAR build.
2. GitHub dependency review and a full-history Gitleaks secret scan.
3. A build of the same non-root Java 25 container used by Vercel.
4. When deployment is enabled, an internal-branch Preview deployment followed
   by health, login and authenticated safe-read smoke checks.

After an approved merge to `master`, the workflow builds a Production candidate
with `vercel deploy --prod --skip-domain`. It smoke-tests the candidate before a
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
| `VERCEL_DEPLOY_ENABLED` | Set to `true` only after Preview and Production values are complete |
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

Do not allow Vercel Git integration to auto-assign the Production domain. The
workflow's staged candidate and protected promotion are the only Production
path. Preview Git integration may also be disabled to avoid duplicate builds.

## Vercel environment separation

Create one Vercel project and configure distinct Preview and Production values.
The values must point to separate databases, Accounts, SoCLaaS keys and
hostnames. Do not copy Production secrets into Preview.

Required application variables in both environments are:

- `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME` and
  `SPRING_DATASOURCE_PASSWORD`;
- `SUPABASE_SSL_ROOT_CERT=/etc/ssl/certs/ca-certificates.crt` when the Supabase
  certificate chain validates against the container trust bundle;
- `SESSION_COOKIE_SECURE=true`;
- `AI_ENABLED`, `SOCLAAS_BASE_URL`, `SOCLAAS_API_KEY` and `SOCLAAS_MODEL`;
- `SPRING_PROFILES_ACTIVE=production` for Production;
- `DEMO_DATA_ENABLED=false` for Production;
- `BOOTSTRAP_MANAGER_USERNAME`, `BOOTSTRAP_MANAGER_DISPLAY_NAME` and
  `BOOTSTRAP_MANAGER_PASSWORD` only for the first empty Production database
  initialization described in `DatabaseCompletion.md`.

The workflow supplies `APP_VERSION` as the immutable Git commit SHA. Vercel
supplies `PORT`; `application.yml` binds it and enables graceful shutdown behind
the platform proxy. Application state and Spring Sessions remain in PostgreSQL,
not in the stateless container.

Keep the Preview and Production smoke Accounts synthetic and minimally
privileged. The smoke script never prints credentials and deletes its temporary
cookie and response files on exit.

## First enablement and release

1. Create the Vercel project and add `Dockerfile.vercel` as the project root
   container entrypoint.
2. Add separate Preview and Production application values in Vercel.
3. Add the GitHub variables, secrets and protected environments above.
4. Open a test pull request with `VERCEL_DEPLOY_ENABLED=true`; retain the Preview
   URL and smoke result in the GitHub job summary.
5. Merge only after the required checks and teammate approval.
6. Confirm the Production candidate smoke result, then approve the protected
   `production` job. Verify that promotion used the same candidate URL.
7. Record the deployment URL/ID and sanitized result under `logs/`; never copy
   environment values into the evidence.
8. Rehearse the manual rollback against the immediately previous deployment and
   separately record the required database restore exercise.

## Local verification

Run the same application checks and image build without deployment credentials:

```text
./gradlew clean check bootJar --no-daemon
docker build --file Dockerfile.vercel --tag facilityflow:local .
```

The public `GET /health` endpoint returns only `{"status":"UP"}` when both the
application and database are reachable, or `{"status":"DOWN"}` with HTTP 503.
Provider, credential and connection details are never returned.

## Still requiring hosted evidence

- first Vercel Preview and Production-candidate builds;
- protected no-rebuild promotion and rollback rehearsal;
- separate Preview/Production database and SoCLaaS-key inspection;
- five-minute external monitoring and alert exercise;
- provider backup verification and database restore rehearsal.
